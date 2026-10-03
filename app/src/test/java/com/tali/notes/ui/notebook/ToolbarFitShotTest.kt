package com.tali.notes.ui.notebook

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.tali.notes.data.AppSettings
import com.tali.notes.model.PenSlot
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.Tool
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The toolbar held upright with many pens and colours: the tools page with an arrow instead of being cut off. Frames in build/toolbar-fit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1100dp-h400dp-mdpi", application = android.app.Application::class)
class ToolbarFitShotTest {
  @get:Rule val rule = createComposeRule()

  private fun shot(name: String, width: Int) {
    val base = AppSettings()
    val pens = base.pens + List(3) { PenSlot("x$it", PenType.FINELINER, 0xFF1F5FD1.toInt(), 2.5f) }
    val settings = base.copy(pens = pens, toolbarColors = 6)
    rule.setContent {
      TaliTheme(darkTheme = false) {
        Box(Modifier.testTag("stage").width(width.dp).background(Tali.colors.background).padding(top = 30.dp, bottom = 10.dp), contentAlignment = Alignment.TopCenter) {
          MainToolbar(settings, false, Tool.PEN, ShapeKind.LINE, null, {}, false, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {})
        }
      }
    }
    rule.waitForIdle()
    val dir = File("build/toolbar-fit").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "$name.png").outputStream())
  }

  @Test fun upright() = shot("upright-760", 760)

  @Test fun wide() = shot("wide-1100", 1100)
}
