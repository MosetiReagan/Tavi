package com.example.tavi.roles.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import com.example.tavi.MainActivity

class TaviVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        Log.d(TAG, "Voice interaction session shown with flags: $showFlags")

        // Launch MainActivity with assistant prompt intent if needed
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("EXTRA_FROM_ASSISTANT_INVOCATION", true)
        }
        context.startActivity(intent)
        hide()
    }

    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        super.onHandleAssist(data, structure, content)
        Log.d(TAG, "onHandleAssist received")
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) {
        super.onHandleScreenshot(screenshot)
        Log.d(TAG, "onHandleScreenshot received (null=${screenshot == null})")
    }

    companion object {
        private const val TAG = "TaviVoiceSession"
    }
}
