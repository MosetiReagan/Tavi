package com.example.tavi.agent

import org.junit.Assert.*
import org.junit.Test

class AgentGroundingTest {
    private fun node(id: String, text: String, editable: Boolean = false) = CompactTreeNode(id, text, "", "Button", true, editable)
    private fun tree(vararg nodes: CompactTreeNode) = CompactScreenTree("com.android.chrome", nodes.size, "screen", nodes.toList())
    @Test fun exactSelectorsRejectAmbiguityAndCoordinates() {
        assertNotNull(AgentGrounding.validate(AgentAction.TapNode("Send"), tree(node("a", "Send"), node("b", "Send"))))
        assertNotNull(AgentGrounding.validate(AgentAction.TapNode("Sen"), tree(node("a", "Send"))))
        assertNotNull(AgentGrounding.validate(AgentAction.Tap(10f, 20f), tree(node("a", "Send"))))
        assertNull(AgentGrounding.validate(AgentAction.TapNode("a"), tree(node("a", "Send"))))
    }
    @Test fun safetyUsesActualLabelAndBlocksSensitiveFields() {
        val action = AgentAction.TapNode("neutral_id")
        val screen = tree(node("neutral_id", "Send payment"))
        val target = AgentGrounding.node(action, screen)!!
        assertTrue(AgentSafetyGuard.isConsequentialAction(action, target.text))
        assertNotNull(AgentGrounding.validate(AgentAction.TypeText("password", "secret"), tree(node("password", "", true))))
    }
    @Test fun completionRequiresNonEditableEvidenceAndChangedScreensInvalidateApproval() {
        assertFalse(AgentGrounding.completionVisible(null, tree(node("a", "Results"))))
        assertFalse(AgentGrounding.completionVisible("Results", tree(node("a", "Results", true))))
        assertTrue(AgentGrounding.completionVisible("Results", tree(node("a", "Results"))))
        assertNotEquals(AgentGrounding.fingerprint(tree(node("a", "Next"))), AgentGrounding.fingerprint(tree(node("a", "Delete"))))
    }
}
