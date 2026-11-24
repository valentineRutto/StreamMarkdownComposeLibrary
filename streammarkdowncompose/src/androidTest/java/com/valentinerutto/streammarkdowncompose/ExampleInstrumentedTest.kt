package com.valentinerutto.streammarkdowncompose

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamingMarkdownComposeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rendersCompletedMarkdownContent() {
        composeRule.setContent {
            StreamingMarkdown(
                text = """
                    # Stream title

                    **Bold text** and `inline code`

                    - [ ] Unchecked task
                    - [x] Checked task

                    | Name | Status |
                    |:-----|------:|
                    | Kotlin | Ready |

                    ```kotlin
                    fun hello() = "Hello"
                    ```

                    <strong>Safe HTML</strong>
                """.trimIndent()
            )
        }

        composeRule.onNodeWithText("Stream title", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Unchecked task", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Checked task", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Name", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Ready", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("fun hello() = \"Hello\"", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Safe HTML", substring = true).assertIsDisplayed()
    }

    @Test
    fun rendersNestedListsAndQuote() {
        composeRule.setContent {
            StreamingMarkdown(
                text = """
                    - Parent
                      - Child
                        - Grandchild

                    > Quoted response
                """.trimIndent()
            )
        }

        composeRule.onNodeWithText("Parent", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Child", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Grandchild", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Quoted response", substring = true).assertIsDisplayed()
    }

    @Test
    fun rendersHorizontalRuleAndHeadingLevels() {
        composeRule.setContent {
            StreamingMarkdown(text = "## H2 heading\n\n---\n\n###### H6 heading")
        }

        composeRule.onNodeWithText("H2 heading", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("H6 heading", substring = true).assertIsDisplayed()
    }

    @Test
    fun codeBlockCopyButtonChangesToCopied() {
        composeRule.setContent {
            StreamingMarkdown(text = "```kotlin\nfun hello() = \"Hello\"\n```")
        }

        composeRule.onNodeWithText("Copy", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Copy", substring = true).performClick()
        composeRule.onNodeWithText("Copied", substring = true).assertIsDisplayed()
    }
}