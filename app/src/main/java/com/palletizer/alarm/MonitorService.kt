package com.palletizer.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log
import androidx.lifecycle.LifecycleService

class MonitorService : LifecycleService() {
    private val executor = Executors.newSingleThreadExecutor()
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val processing = AtomicBoolean(false)
    private var lastSentKey = ""
    private var lastSentAt = 0L
    private var candidate = ""
    private var repeats = 0

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(101, notification("Đang khởi tạo camera…"))
        bindCamera()
    }

    private fun bindCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { imageProxy -> analyze(imageProxy) }
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)
                updateNotification("Đang giám sát màn hình HMI")
            } catch (e: Exception) {
                Log.e("PalletizerAlarm", "Camera binding failed", e)
                updateNotification("Không mở được camera. Mở lại ứng dụng.")
            }
        }, ContextCompatMain.executor(this))
    }

    private fun analyze(proxy: ImageProxy) {
        if (!processing.compareAndSet(false, true)) { proxy.close(); return }
        val media = proxy.image
        if (media == null) { processing.set(false); proxy.close(); return }
        val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val text = result.text.replace("\r", "\n").trim()
                val normalized = text.lowercase(Locale.ROOT)
                if (text.isNotBlank() && !normalized.contains("no current alarms")) {
                    val hasFaultWord = listOf("fault", "alarm", "error", "blocked", "did not retract",
                        "failed", "failure", "not retract", "transfer late").any { normalized.contains(it) }
                    val longLine = text.lines().any { it.trim().length >= 28 }
                    if (hasFaultWord || longLine) {
                        val key = text.lines().map { it.trim() }.filter { it.isNotBlank() }.joinToString(" | ").take(500)
                        if (key == candidate) repeats++ else { candidate = key; repeats = 1 }
                        if (repeats >= 3) maybeSend(key)
                    } else { candidate = ""; repeats = 0 }
                } else { candidate = ""; repeats = 0 }
            }
            .addOnFailureListener { Log.w("PalletizerAlarm", "OCR failed", it) }
            .addOnCompleteListener { proxy.close(); processing.set(false) }
    }

    private fun maybeSend(message: String) {
        val now = System.currentTimeMillis()
        val key = message.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        // Same alert is suppressed for 30 minutes. Different text can alert immediately.
        if (key == lastSentKey && now - lastSentAt < 30 * 60 * 1000L) return
        lastSentKey = key
        lastSentAt = now
        val prefs = securePrefs()
        val token = prefs.getString("token", "").orEmpty()
        val chatId = prefs.getString("chatId", "").orEmpty()
        if (token.isBlank() || chatId.isBlank()) return
        Thread {
            try {
                val endpoint = URL("https://api.telegram.org/bot$token/sendMessage")
                val conn = endpoint.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                val body = "chat_id=${enc(chatId)}&text=${enc("🚨 PALLETIZER ALARM\\nTime: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date())}\\nDetected text:\\n$message")}"
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) Log.e("PalletizerAlarm", "Telegram HTTP $code")
                conn.disconnect()
            } catch (e: Exception) { Log.e("PalletizerAlarm", "Telegram send failed", e) }
        }.start()
    }

    private fun securePrefs() = run {
        val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(this, "secure_config", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel("palletizer_monitor", "Palletizer monitor", NotificationManager.IMPORTANCE_LOW))
        }
    }
    private fun notification(msg: String): Notification =
        NotificationCompat.Builder(this, "palletizer_monitor").setContentTitle("Palletizer Alarm")
            .setContentText(msg).setSmallIcon(android.R.drawable.ic_dialog_alert).setOngoing(true).build()
    private fun updateNotification(msg: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(101, notification(msg))
    }
    override fun onDestroy() {
        try { ProcessCameraProvider.getInstance(this).get().unbindAll() } catch (_: Exception) {}
        executor.shutdown()
        recognizer.close()
        super.onDestroy()
    }
}
