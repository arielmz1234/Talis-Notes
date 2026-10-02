package com.tali.notes.ui.notebook

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PenType
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliTheme
import com.tali.notes.ui.components.PenGlyph
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The line styles as she sees them in the pen settings; frames go to build/line-styles. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h500dp-mdpi", application = android.app.Application::class)
class LineStylePickerTest {
  @get:Rule val rule = createComposeRule()

  private val frames = File("build/line-styles").apply { mkdirs() }
  private var picked by mutableStateOf(LineStyle.SOLID)

  private fun show(dark: Boolean) {
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        Box(Modifier.testTag("stage").background(Tali.colors.surface).padding(16.dp)) {
          Column {
            LineStylePicker(picked, { picked = it }, Modifier.width(360.dp))
            Row(Modifier.padding(top = 12.dp)) {
              LineStyle.entries.forEach { PenGlyph(PenType.FINELINER, Color(0xFF1F5FD1), Modifier.padding(horizontal = 6.dp), line = it) }
            }
          }
        }
      }
    }
  }

  private fun save(name: String) =
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(frames, "$name.png").outputStream())

  @Test
  fun pickingGlidesToTheTile() {
    show(dark = false)
    save("light-solid")
    rule.onNodeWithText("Dot and dash").performClick()
    rule.waitForIdle()
    assertEquals(LineStyle.DOT_DASH, picked)
    save("light-dot-dash")
  }

  @Test
  fun darkToo() {
    picked = LineStyle.LONG_DASH
    show(dark = true)
    save("dark-long-dash")
  }
}
