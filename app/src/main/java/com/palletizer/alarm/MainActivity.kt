package com.palletizer.alarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class MainActivity : AppCompatActivity() {
    private lateinit var token: EditText
    private lateinit var chatId: EditText
    private lateinit var status: TextView
    private val prefs by lazy {
        val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(this, "secure_config", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) startMonitor()
        else Toast.makeText(this, "Cần cấp quyền camera để giám sát", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        token = findViewById(R.id.token)
        chatId = findViewById(R.id.chatId)
        status = findViewById(R.id.status)
        token.setText(prefs.getString("token", ""))
        chatId.setText(prefs.getString("chatId", ""))
        findViewById<Button>(R.id.save).setOnClickListener {
            val t = token.text.toString().trim()
            val c = chatId.text.toString().trim()
            if (t.isBlank() || c.isBlank()) {
                Toast.makeText(this, "Nhập Bot Token và Chat ID", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putString("token", t).putString("chatId", c).apply()
                Toast.makeText(this, "Đã lưu cấu hình mã hóa trên thiết bị", Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<Button>(R.id.start).setOnClickListener {
            if (prefs.getString("token", "").isNullOrBlank() || prefs.getString("chatId", "").isNullOrBlank()) {
                Toast.makeText(this, "Hãy lưu Bot Token và Chat ID trước", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val permissions = mutableListOf(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED })
                permissionLauncher.launch(permissions.toTypedArray())
            else startMonitor()
        }
        findViewById<Button>(R.id.stop).setOnClickListener {
            stopService(Intent(this, MonitorService::class.java))
            status.text = "Trạng thái: đã dừng"
        }
    }

    private fun startMonitor() {
        val intent = Intent(this, MonitorService::class.java)
        ContextCompat.startForegroundService(this, intent)
        status.text = "Trạng thái: đang khởi động giám sát"
        Toast.makeText(this, "Đang khởi động camera giám sát", Toast.LENGTH_SHORT).show()
    }
}
