// SPDX-License-Identifier: GPL-3.0-only
package app.aria.designsystem

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class GlassSurfaceTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contentIsShownWhenGlassIsOff() {
        rule.setContent { GlassSurface(mode = GlassMode.OFF) { Text("hello") } }
        rule.onNodeWithText("hello").assertIsDisplayed()
    }

    @Test fun contentIsShownWhenGlassIsRequested() {
        rule.setContent { GlassSurface(mode = GlassMode.ON) { Text("hello") } }
        rule.onNodeWithText("hello").assertIsDisplayed()
    }
}
