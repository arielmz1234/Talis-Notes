package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The cloud puff around a card as it leaves and as one arrives, frame by frame, in light and dark.
 * Frames go to build/puff-frames for a look; the test checks the puff is gone once it has played.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one loads Ink's native library, which the JVM doesn't have.
@Config(sdk = [35], qualifiers = "w600dp-h400dp-mdpi", application = android.app.Application::class)
class CloudPuffTest {
  @get:Rule val rule = createComposeRule()

  private val frames = File("build/puff-frames").apply { mkdirs() }
  private var leaving by mutableStateOf(false)
  private var appearing by mutableStateOf(false)

  private fun card(dark: Boolean, settle: Boolean = true) {
    rule.mainClock.autoAdvance = false
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        Box(Modifier.testTag("stage").background(Tali.colors.background).padding(40.dp), contentAlignment = Alignment.Center) {
          PuffBox(leaving = leaving, appearing = appearing) {
            Box(Modifier.size(220.dp, 64.dp).clip(RoundedCornerShape(14.dp)).background(Tali.colors.surface).padding(16.dp)) {
              Text("Statics", color = Tali.colors.ink)
            }
          }
        }
      }
    }
    if (settle) rule.mainClock.advanceTimeBy(500)
  }

  private fun record(name: String, count: Int = 36) {
    for (f in 0 until count) {
      rule.mainClock.advanceTimeByFrame()
      shot().compress(Bitmap.CompressFormat.PNG, 100, File(frames, "$name-%02d.png".format(f)).outputStream())
    }
  }

  private fun shot(): Bitmap = rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap()

  private fun leaves(dark: Boolean) {
    card(dark)
    val before = shot()
    leaving = true
    Snapshot.sendApplyNotifications()
    record("leave-" + if (dark) "dark" else "light")
    rule.mainClock.advanceTimeBy(1000)
    // Once played, nothing is left but the background: the card has gone and so has the cloud.
    val after = shot()
    assertEquals("corner untouched", before.getPixel(2, 2), after.getPixel(2, 2))
    assertEquals("centre back to background", before.getPixel(2, after.height / 2), after.getPixel(after.width / 2, after.height / 2))
  }

  @Test fun leavesOnAPuffLight() = leaves(dark = false)

  @Test fun leavesOnAPuffDark() = leaves(dark = true)

  @Test
  fun arrivesOnAPuff() {
    appearing = true
    card(dark = false, settle = false)
    record("arrive-light")
  }
}
