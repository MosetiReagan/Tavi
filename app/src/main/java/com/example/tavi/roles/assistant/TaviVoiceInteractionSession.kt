package com.example.tavi.roles.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.widget.*
import com.example.tavi.MainActivity
import com.example.tavi.agent.AccessibilityTreeReader

class TaviVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {
    private var sourceLabel: TextView? = null
    private var input: EditText? = null

    override fun onCreateContentView(): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.rgb(28, 32, 44))
            addView(TextView(context).apply { text = "Tavi • on-device assistant"; textSize = 22f; setTextColor(android.graphics.Color.WHITE) })
            sourceLabel = TextView(context).apply { text = "Ask a question or use your voice."; setTextColor(android.graphics.Color.LTGRAY) }
            addView(sourceLabel)
            input = EditText(context).apply { hint = "What can I help with?"; setTextColor(android.graphics.Color.WHITE); setHintTextColor(android.graphics.Color.LTGRAY) }
            addView(input)
            addView(Button(context).apply { text = "Continue in Tavi"; setOnClickListener { open(input?.text?.toString(), false) } })
            addView(Button(context).apply { text = "Explain this screen"; setOnClickListener { open("Explain this screen and suggest useful next steps.", false) } })
            addView(Button(context).apply { text = "Use offline voice"; setOnClickListener { open(null, true) } })
            addView(Button(context).apply { text = "Dismiss"; setOnClickListener { AssistContextStore.clear(); hide() } })
        }
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        AssistContextStore.clear()
    }

    private fun open(prompt: String?, voice: Boolean) {
        if (!prompt.isNullOrBlank()) MainActivity.sharedIncomingText.value = prompt
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (voice) putExtra(EXTRA_START_HANDSFREE, true)
        })
        hide()
    }

    override fun onHandleAssist(data: Bundle?, structure: AssistStructure?, content: AssistContent?) {
        super.onHandleAssist(data, structure, content)
        if (structure == null) { sourceLabel?.text = "This app has not shared screen context."; return }
        val text = StringBuilder()
        var count = 0
        fun visit(node: AssistStructure.ViewNode, depth: Int) {
            if (depth > 6 || count++ >= 80 || text.length > 1800 || node.isAssistBlocked) return
            val password = node.inputType and 0x80 != 0 || node.inputType and 0x10 != 0 && node.inputType and 0x0f == 2
            val safe = AccessibilityTreeReader.sanitizeText(node.text?.toString(), password)
            if (safe.isNotBlank() && safe != "[PASSWORD REDACTED]") text.append(safe).append('\n')
            for (index in 0 until node.childCount) visit(node.getChildAt(index), depth + 1)
        }
        for (window in 0 until structure.windowNodeCount.coerceAtMost(3)) visit(structure.getWindowNodeAt(window).rootViewNode, 0)
        val source = structure.activityComponent?.packageName ?: "current app"
        if (text.isNotBlank()) {
            AssistContextStore.set(source, text.toString())
            sourceLabel?.text = "Context from $source is available for your next question."
        } else sourceLabel?.text = "No readable screen context was shared."
    }

    override fun onHandleScreenshot(screenshot: Bitmap?) { /* Screenshots are not retained. */ }

    companion object { const val EXTRA_START_HANDSFREE = "com.example.tavi.extra.START_HANDSFREE" }
}
