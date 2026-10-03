package com.tali.notes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The gliding highlight takes the new theme's colour when light/dark switches (it used to keep the old one). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h400dp-mdpi", application = android.app.Application::class)
class ThemeFlipTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun highlightFollowsTheTheme() {
    val dark = mutableStateOf(false)
    var surface = 0
    rule.setContent {
      TaliTheme(darkTheme = dark.value) {
        surface = Tali.colors.surface.toArgb()
        Box(Modifier.testTag("seg").width(300.dp)) { Segmented(listOf(1, 2, 3), 1, { "  " }, {}) }
      }
    }
    rule.waitForIdle()
    dark.value = true
    rule.waitForIdle()
    val px = rule.onNodeWithTag("seg").captureToImage().toPixelMap()
    // Inside the first (chosen) option, clear of its rounded corners.
    val got = px[12, px.height / 2].toArgb()
    fun close(a: Int, b: Int) = (0..2).all { s -> kotlin.math.abs(((a shr (s * 8)) and 0xFF) - ((b shr (s * 8)) and 0xFF)) < 12 }
    assertTrue("highlight %08x should be the dark surface %08x".format(got, surface), close(got, surface))
  }
}
