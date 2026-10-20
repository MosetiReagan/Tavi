package com.example.tavi.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.tavi.model.Message
import com.example.tavi.model.MessageRole
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun messageBubble_displaysUserAndAssistantText() {
        val userMsg = Message(
            role = MessageRole.USER,
            content = "What's on my calendar?"
        )
        val assistantMsg = Message(
            role = MessageRole.ASSISTANT,
            content = "You have Team Meeting tomorrow at 3pm."
        )

        composeTestRule.setContent {
            androidx.compose.foundation.layout.Column {
                MessageBubble(message = userMsg, onSpeak = {})
                MessageBubble(message = assistantMsg, onSpeak = {})
            }
        }

        composeTestRule.onNodeWithText("What's on my calendar?").assertExists()
        composeTestRule.onNodeWithText("You have Team Meeting tomorrow at 3pm.").assertExists()
        composeTestRule.onNodeWithText("You").assertExists()
        composeTestRule.onNodeWithText("Tavi").assertExists()
    }
}
