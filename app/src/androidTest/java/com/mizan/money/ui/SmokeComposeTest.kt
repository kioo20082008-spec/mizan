package com.mizan.money.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mizan.money.ui.theme.ProvideMizanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Instrumented smoke test placeholder: verifies Compose UI testing is wired up.
// Requires an emulator/device, so it does NOT run under `gradle testDebugUnitTest`
// in CI.
@RunWith(AndroidJUnit4::class)
class SmokeComposeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersTextInsideAppTheme() {
        composeRule.setContent {
            ProvideMizanTheme(isDark = false) {
                Text("mizan")
            }
        }
        composeRule.onNodeWithText("mizan").assertIsDisplayed()
    }
}
