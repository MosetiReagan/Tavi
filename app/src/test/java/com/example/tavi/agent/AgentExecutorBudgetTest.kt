package com.example.tavi.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class AgentExecutorBudgetTest {

    /**
     * Simulated test loop evaluating the exact budget and kill switch logic of AgentExecutor.
     */
    class TestLoopRunner(private val killSwitch: AtomicBoolean) {

        fun runLoop(maxSteps: Int, stepAction: (Int) -> AgentAction): Pair<Int, String> {
            var stepCount = 0
            while (stepCount < maxSteps) {
                if (killSwitch.get()) {
                    return Pair(stepCount, "Stopped by user kill switch")
                }
                stepCount++
                val action = stepAction(stepCount)
                if (action is AgentAction.Complete) {
                    return Pair(stepCount, "Complete: ${action.summary}")
                }
                if (AgentSafetyGuard.isConsequentialAction(action)) {
                    return Pair(stepCount, "Confirmation required for $action")
                }
            }
            return Pair(stepCount, "Max step budget reached ($maxSteps)")
        }
    }

    @Test
    fun testHardLimit12Steps() {
        val killSwitch = AtomicBoolean(false)
        val runner = TestLoopRunner(killSwitch)

        // Generator that never emits Complete
        val (steps, summary) = runner.runLoop(AgentSafetyGuard.MAX_AGENT_STEPS) {
            AgentAction.Scroll(ScrollDirection.DOWN)
        }

        assertEquals(12, steps)
        assertTrue(summary.contains("Max step budget reached (12)"))
    }

    @Test
    fun testKillSwitchStopsExecutionEarly() {
        val killSwitch = AtomicBoolean(false)
        val runner = TestLoopRunner(killSwitch)

        val (steps, summary) = runner.runLoop(AgentSafetyGuard.MAX_AGENT_STEPS) { currentStep ->
            if (currentStep == 4) {
                killSwitch.set(true)
            }
            AgentAction.Scroll(ScrollDirection.DOWN)
        }

        assertEquals(4, steps)
        assertTrue(summary.contains("Stopped by user kill switch"))
    }

    @Test
    fun testConsequentialActionHaltsLoopForConfirmation() {
        val killSwitch = AtomicBoolean(false)
        val runner = TestLoopRunner(killSwitch)

        val (steps, summary) = runner.runLoop(AgentSafetyGuard.MAX_AGENT_STEPS) { currentStep ->
            if (currentStep == 3) {
                AgentAction.TapNode("buy_now_confirm")
            } else {
                AgentAction.Scroll(ScrollDirection.DOWN)
            }
        }

        assertEquals(3, steps)
        assertTrue(summary.contains("Confirmation required"))
    }
}
