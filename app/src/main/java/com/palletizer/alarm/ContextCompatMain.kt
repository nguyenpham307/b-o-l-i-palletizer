package com.palletizer.alarm
import android.content.Context
import androidx.camera.core.CameraX
import java.util.concurrent.Executor
import android.os.Handler
import android.os.Looper
object ContextCompatMain {
    fun executor(context: Context): Executor = Executor { command ->
        Handler(Looper.getMainLooper()).post(command)
    }
}
