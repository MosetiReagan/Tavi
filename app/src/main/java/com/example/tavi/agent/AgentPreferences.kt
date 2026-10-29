package com.example.tavi.agent

import android.content.Context
import android.content.SharedPreferences

object AgentPreferences {
    private const val PREFS_NAME = "tavi_agent_prefs"
    const val KEY_AGENT_MODE_ENABLED = "screen_agent_mode_enabled"
    const val KEY_FIRST_RUN_EXPLANATION_SHOWN = "screen_agent_first_run_shown"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAgentModeEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_AGENT_MODE_ENABLED, false)

    fun setAgentModeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AGENT_MODE_ENABLED, enabled).apply()
    }

    fun isFirstRunExplanationShown(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FIRST_RUN_EXPLANATION_SHOWN, false)

    fun setFirstRunExplanationShown(context: Context, shown: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FIRST_RUN_EXPLANATION_SHOWN, shown).apply()
    }
}
