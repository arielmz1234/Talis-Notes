package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliColors
import com.tali.notes.theme.TaliTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The gliding highlight, rendered for real: it starts under the chosen option, travels (stretching)
 * when another is chosen, and settles under it. Frames are saved to build/sliding-frames for a look.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A plain Application: the real one loads Ink's native library, which the JVM doesn't have.
@Config(sdk = [35], qualifiers = "w600dp-h400dp-mdpi", application = android.app.Application::class)
class SlidingSelectionTest {
  @get:Rule val rule = createComposeRule()

  private val frames = File("build/sliding-frames").apply { mkdirs() }
  private lateinit var palette: TaliColors

  private enum class Quality(val label: String) { BEST("Best"), BALANCED("Balanced"), LIGHT("Light") }

  private fun segmented(dark: Boolean): () -> Quality {
    var choice by mutableStateOf(Quality.BEST)
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        palette = Tali.colors
        Box(Modifier.background(Tali.colors.surface).padding(16.dp)) {
          Segmented(Quality.entries, choice, { it.label }, { choice = it }, Modifier.width(420.dp).testTag("seg"))
        }
      }
    }
    return { choice }
  }

  private fun shot(tag: String): Bitmap = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()

  /** How far a pixel is from [c], summed over RGB. */
  private fun Bitmap.distance(x: Int, y: Int, c: Color): Int {
    val p = getPixel(x, y)
    val t = c.toArgb()
    return abs(android.graphics.Color.red(p) - android.graphics.Color.red(t)) +
      abs(android.graphics.Color.green(p) - android.graphics.Color.green(t)) +
      abs(android.graphics.Color.blue(p) - android.graphics.Color.blue(t))
  }

  @Test
  fun segmentedPillGlidesToTheChosenOption() = glides(dark = false)

  @Test
  fun segmentedPillGlidesInDarkMode() = glides(dark = true)

  private fun glides(dark: Boolean) {
    val choice = segmented(dark)
    rule.mainClock.autoAdvance = false
    rule.mainClock.advanceTimeBy(1000)
    val surface = palette.surface
    val sunken = palette.sunken
    val start = shot("seg")
    val w = start.width
    val y = 6 // near the top edge of the pill, clear of the label text
    // A third of the way in is the middle of each option.
    fun xOf(i: Int) = (w * (i * 2 + 1)) / 6
    assertTrue("pill starts under Best", start.distance(xOf(0), y, surface) < start.distance(xOf(0), y, sunken))
    assertTrue("Light starts plain", start.distance(xOf(2), y, sunken) < start.distance(xOf(2), y, surface))

    rule.onNodeWithText("Light").performClick()
    assertTrue(choice() == Quality.LIGHT)
    val name = if (dark) "dark" else "light"
    var mid: Bitmap? = null
    for (f in 0..24) {
      rule.mainClock.advanceTimeByFrame()
      val b = shot("seg")
      b.compress(Bitmap.CompressFormat.PNG, 100, File(frames, "seg-$name-%02d.png".format(f)).outputStream())
      if (f == 6) mid = b
    }
    // Mid-flight the pill is passing over Balanced, stretched across it.
    val m = mid!!
    assertTrue("pill passes over Balanced", m.distance(xOf(1), y, surface) < m.distance(xOf(1), y, sunken))
    rule.mainClock.advanceTimeBy(1500)
    val end = shot("seg")
    end.compress(Bitmap.CompressFormat.PNG, 100, File(frames, "seg-$name-end.png").outputStream())
    assertTrue("pill ends under Light", end.distance(xOf(2), y, surface) < end.distance(xOf(2), y, sunken))
    assertTrue("Best is plain again", end.distance(xOf(0), y, sunken) < end.distance(xOf(0), y, surface))
  }

  private val colours = listOf(Color(0xFF1F2430), Color(0xFFE5484D), Color(0xFF3B82F6), Color(0xFF22A06B))
  private var chosen by mutableStateOf<Color?>(null)

  private fun dots(initial: Color?) {
    chosen = initial
    rule.setContent {
      TaliTheme(darkTheme = false) {
        palette = Tali.colors
        Column(Modifier.background(Tali.colors.surface).padding(16.dp)) {
          val ring = rememberSlidingSelection()
          Row(Modifier.colorRing(ring, chosen).testTag("dots")) {
            colours.forEach { col -> ColorDot(col, selected = col == chosen, selection = ring) { chosen = col } }
          }
          Spacer(Modifier.height(4.dp))
        }
      }
    }
    rule.mainClock.autoAdvance = false
    rule.mainClock.advanceTimeBy(1000)
  }

  /** A state change from the test thread, announced so the paused clock's next frame recomposes. */
  private fun choose(colour: Color?) {
    chosen = colour
    Snapshot.sendApplyNotifications()
  }

  /** How close the pixel at the left edge of dot [i] (where its ring is drawn) is to the accent. */
  private fun Bitmap.ringAt(i: Int) = distance(width / 4 * i + 1, height / 2, palette.accent)

  @Test
  fun colourRingGlidesToTheChosenColour() {
    dots(colours[0])
    assertTrue("ring starts on the first dot", shot("dots").ringAt(0) < 120)
    choose(colours[3])
    for (f in 0..20) {
      rule.mainClock.advanceTimeByFrame()
      shot("dots").compress(Bitmap.CompressFormat.PNG, 100, File(frames, "ring-%02d.png".format(f)).outputStream())
    }
    rule.mainClock.advanceTimeBy(1500)
    val end = shot("dots")
    assertTrue("ring ends on the last dot", end.ringAt(3) < 120)
    assertTrue("first dot has no ring", end.ringAt(0) > 120)
  }

  @Test
  fun colourRingFadesWhenNothingIsChosen() {
    dots(colours[2])
    assertTrue("ring on the chosen dot", shot("dots").ringAt(2) < 120)
    choose(null)
    rule.mainClock.advanceTimeBy(1000)
    assertTrue("no ring when nothing is chosen", shot("dots").ringAt(2) > 120)
  }
}
