package com.example.tavi.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSafetyPolicyTest {

    @Test
    fun testBlacklistedPackages() {
        assertTrue(AgentSafetyGuard.isPackageProhibited("com.chase.sig.android"))
        assertTrue(AgentSafetyGuard.isPackageProhibited("com.paypal.android.p2pmobile"))
        assertTrue(AgentSafetyGuard.isPackageProhibited("com.1password.android"))
        assertTrue(AgentSafetyGuard.isPackageProhibited("com.google.android.finsky.billing"))

        // Standard apps are permitted
        assertFalse(AgentSafetyGuard.isPackageProhibited("com.android.calculator2"))
        assertFalse(AgentSafetyGuard.isPackageProhibited("com.google.android.deskclock"))
        assertFalse(AgentSafetyGuard.isPackageProhibited("com.android.chrome"))
    }

    @Test
    fun testConsequentialActionsDetected() {
        // Consequential
        assertTrue(AgentSafetyGuard.isConsequentialAction(AgentAction.TapNode("buy_now_button")))
        assertTrue(AgentSafetyGuard.isConsequentialAction(AgentAction.TapNode("delete_all_files")))
        assertTrue(AgentSafetyGuard.isConsequentialAction(AgentAction.TapNode("confirm_transfer")))
        assertTrue(AgentSafetyGuard.isConsequentialAction(AgentAction.TypeText("pay_recipient", "100")))
        assertTrue(AgentSafetyGuard.isConsequentialAction(AgentAction.Tap(100f, 200f), nodeContext = "Send Payment"))

        // Non-consequential
        assertFalse(AgentSafetyGuard.isConsequentialAction(AgentAction.TapNode("next_page")))
        assertFalse(AgentSafetyGuard.isConsequentialAction(AgentAction.TapNode("refresh_button")))
        assertFalse(AgentSafetyGuard.isConsequentialAction(AgentAction.TypeText("search_box", "android news")))
        assertFalse(AgentSafetyGuard.isConsequentialAction(AgentAction.PressBack))
    }

    @Test
    fun testStepBudgetLimit() {
        assertFalse(AgentSafetyGuard.isBudgetExceeded(0))
        assertFalse(AgentSafetyGuard.isBudgetExceeded(11))
        assertTrue(AgentSafetyGuard.isBudgetExceeded(12))
        assertTrue(AgentSafetyGuard.isBudgetExceeded(13))
    }
}
