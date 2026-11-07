package com.example.tavi.agent

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import com.example.tavi.MainActivity
import kotlinx.coroutines.*

object AgentStopOverlay {
    private var button: Button? = null
    private var manager: WindowManager? = null
    suspend fun show(job: Job) = withContext(Dispatchers.Main) {
        hide()
        val service = ScreenAgentService.activeService ?: return@withContext
        manager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val view = Button(service).apply { text = "Stop Tavi task"; setOnClickListener { AgentExecutor.triggerKillSwitch(); job.cancel(); hide() } }
        val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.END
        }
        manager?.addView(view, params); button = view
    }
    suspend fun review() = withContext(Dispatchers.Main) {
        button?.apply { text = "Review Tavi action"; setOnClickListener {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            hide()
        } }
    }
    fun hide() {
        button?.let { view -> runCatching { manager?.removeView(view) } }
        button = null; manager = null
    }
}
