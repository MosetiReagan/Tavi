package com.example.tavi.agent

object AgentSafetyGuard {

    const val MAX_AGENT_STEPS = 12

    private val BLACKLISTED_PACKAGES = setOf(
        "com.chase",
        "com.bankofamerica",
        "com.wellsfargo",
        "com.citi",
        "com.paypal",
        "com.venmo",
        "com.squareup.cash",
        "com.robinhood",
        "com.binance",
        "com.coinbase",
        "com.1password",
        "com.lastpass",
        "com.bitwarden",
        "com.dashlane",
        "keepass",
        "com.google.android.finsky.billing",
        "com.android.vending.billing",
        "com.android.settings.security",
        "com.android.settings.password"
    )

    private val CONSEQUENTIAL_ACTION_KEYWORDS = setOf(
        "send", "buy", "purchase", "delete", "erase", "remove",
        "confirm", "pay", "payment", "transfer", "checkout",
        "order", "uninstall", "factory reset", "format"
    )

    /**
     * Checks if a target package is prohibited by safety policy.
     */
    fun isPackageProhibited(packageName: String): Boolean {
        val lower = packageName.lowercase().trim()
        return BLACKLISTED_PACKAGES.any { lower.contains(it) }
    }

    /**
     * Checks if an action or its targeted label constitutes a dangerous consequential action
     * that requires explicit human confirmation.
     */
    fun isConsequentialAction(action: AgentAction, nodeContext: String? = null): Boolean {
        val contextLower = (nodeContext ?: "").lowercase()

        when (action) {
            is AgentAction.TapNode -> {
                val target = action.selector.lowercase()
                if (CONSEQUENTIAL_ACTION_KEYWORDS.any { target.contains(it) || contextLower.contains(it) }) {
                    return true
                }
            }
            is AgentAction.TypeText -> {
                val txt = action.text.lowercase()
                val sel = action.selector.lowercase()
                if (CONSEQUENTIAL_ACTION_KEYWORDS.any { sel.contains(it) || txt.contains(it) }) {
                    return true
                }
            }
            is AgentAction.Tap -> {
                if (CONSEQUENTIAL_ACTION_KEYWORDS.any { contextLower.contains(it) }) {
                    return true
                }
            }
            else -> {}
        }
        return false
    }

    /**
     * Validates whether execution budget has been exceeded.
     */
    fun isBudgetExceeded(stepCount: Int): Boolean {
        return stepCount >= MAX_AGENT_STEPS
    }
}
