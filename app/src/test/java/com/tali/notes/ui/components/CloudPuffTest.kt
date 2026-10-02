package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * Things leaving and arriving on a puff of mist, frame by frame, in the shapes the app uses them
 * for: a folder row, a notebook card and a page tile, in light and dark. Frames go to
 * build/puff-frames for a look; the tests check nothing is left once the puff has played.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one loads Ink's native library, which the JVM doesn't have.
@Config(sdk = [35], qualifiers = "w800dp-h500dp-mdpi", application = android.app.Application::class)
class CloudPuffTest {
  @get:Rule val rule = createComposeRule()

  private val frames = File("build/puff-frames").apply { mkdirs() }
  private var leaving by mutableStateOf(false)
  private var appearing by mutableStateOf(false)

  /** A folder row, a notebook card and a page tile side by side; the middle one comes or goes. */
  private fun stage(dark: Boolean, settle: Boolean = true) {
    rule.mainClock.autoAdvance = false
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        val c = Tali.colors
        Box(Modifier.testTag("stage").background(c.background).padding(48.dp), contentAlignment = Alignment.Center) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PuffBox(leaving = leaving, appearing = appearing) {
              Box(Modifier.size(220.dp, 44.dp).clip(RoundedCornerShape(14.dp)).background(c.accentSoft).padding(12.dp)) { Text("Statics", color = c.ink) }
            }
            Spacer(Modifier.width(40.dp))
            PuffBox(leaving = leaving, appearing = appearing) {
              Box(Modifier.size(200.dp, 250.dp).clip(RoundedCornerShape(18.dp)).background(c.surface).padding(14.dp), contentAlignment = Alignment.BottomStart) {
                Box(Modifier.size(172.dp, 170.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFB9A8F0)).align(Alignment.TopCenter))
                Text("Hydraulics", color = c.ink)
              }
            }
            Spacer(Modifier.width(40.dp))
            PuffBox(leaving = leaving, appearing = appearing) {
              Box(Modifier.size(132.dp, 187.dp).clip(RoundedCornerShape(10.dp)).background(Color.White))
            }
          }
        }
      }
    }
    if (settle) rule.mainClock.advanceTimeBy(800)
  }

  private fun record(name: String, count: Int = 40) {
    for (f in 0 until count) {
      rule.mainClock.advanceTimeByFrame()
      shot().compress(Bitmap.CompressFormat.PNG, 100, File(frames, "$name-%02d.png".format(f)).outputStream())
    }
  }

  private fun shot(): Bitmap = rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap()

  private fun leaves(dark: Boolean) {
    stage(dark)
    val before = shot()
    leaving = true
    Snapshot.sendApplyNotifications()
    record("leave-" + if (dark) "dark" else "light")
    rule.mainClock.advanceTimeBy(1000)
    // Once played, nothing is left but the background: the things have gone and so has the mist.
    val after = shot()
    val bg = before.getPixel(2, 2)
    for (x in listOf(after.width / 6, after.width / 2, after.width * 5 / 6)) {
      assertEquals("background at x=$x", bg, after.getPixel(x, after.height / 2))
    }
  }

  @Test fun leavesOnAPuffLight() = leaves(dark = false)

  @Test fun leavesOnAPuffDark() = leaves(dark = true)

  private fun arrives(dark: Boolean) {
    appearing = true
    stage(dark, settle = false)
    record("arrive-" + if (dark) "dark" else "light")
  }

  @Test fun arrivesOnAPuffLight() = arrives(dark = false)

  @Test fun arrivesOnAPuffDark() = arrives(dark = true)
}
