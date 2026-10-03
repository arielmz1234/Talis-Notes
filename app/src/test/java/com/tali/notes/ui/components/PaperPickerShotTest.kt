package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.tali.notes.model.PaperStyle
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The paper choices, as in the ⋯ menu (with the line weight) and the new-notebook dialog; frames in build/papers. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w800dp-h600dp-mdpi", application = android.app.Application::class)
class PaperPickerShotTest {
  @get:Rule val rule = createComposeRule()

  private fun shot(name: String, dark: Boolean, weight: Boolean) {
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        Box(Modifier.testTag("stage").background(Tali.colors.surface).padding(12.dp).width(300.dp)) {
          PaperPicker(PaperStyle.LINES, null, emptyList(), {}, {}, {}, {}, lineWeight = if (weight) 1f else null, onLineWeight = if (weight) ({}) else null)
        }
      }
    }
    rule.waitForIdle()
    val dir = File("build/papers").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "$name.png").outputStream())
  }

  @Test fun menuLight() = shot("menu-light", false, true)

  // In a dropdown menu, which sizes itself from its content's natural width (it once crashed there).
  @Test fun insideAMenuDoesNotCrash() {
    rule.setContent {
      TaliTheme(darkTheme = false) {
        androidx.compose.foundation.layout.Column(Modifier.testTag("stage").width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
          PaperPicker(PaperStyle.LINES, null, emptyList(), {}, {}, {}, {}, lineWeight = 1f, onLineWeight = {})
        }
      }
    }
    rule.waitForIdle()
  }

  @Test fun menuDark() = shot("menu-dark", true, true)

  @Test fun dialogLight() = shot("dialog-light", false, false)

  // As wide as the new-notebook card: more papers fit beside More.
  @Test fun dialogWide() {
    rule.setContent {
      TaliTheme(darkTheme = false) {
        Box(Modifier.testTag("stage").background(Tali.colors.surface).padding(12.dp).width(584.dp)) {
          PaperPicker(PaperStyle.GRID, null, emptyList(), {}, {}, {}, {}, tileWidth = 80.dp, perRow = 7)
        }
      }
    }
    rule.waitForIdle()
    val dir = File("build/papers").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "dialog-wide.png").outputStream())
  }

  // At her tablet's density, where the stray line showed.
  @Test @Config(qualifiers = "w800dp-h600dp-xhdpi") fun dialogDarkXhdpi() = shot("dialog-dark-xhdpi", true, false)

  @Test @Config(qualifiers = "w800dp-h600dp-xhdpi") fun menuDarkXhdpi() = shot("menu-dark-xhdpi", true, true)
}
