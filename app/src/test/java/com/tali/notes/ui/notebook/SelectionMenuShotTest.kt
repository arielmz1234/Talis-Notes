package com.tali.notes.ui.notebook

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.tali.notes.canvas.SelectionInfo
import com.tali.notes.data.AppSettings
import com.tali.notes.model.LineStyle
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The selection menu folded (everyday actions) and opened with ⋯ (style and the rest). Frames in build/selection-menu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1100dp-h800dp-mdpi", application = android.app.Application::class)
class SelectionMenuShotTest {
  @get:Rule val rule = createComposeRule()

  private fun shot(name: String, dark: Boolean, open: Boolean) {
    val sel = SelectionInfo(RectF(450f, 520f, 650f, 640f), count = 3, hasShape = true, hasImage = false, color = 0xFF1F5FD1.toInt(), lineSize = 2.5f, line = LineStyle.SOLID)
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        Box(Modifier.testTag("stage").size(1100.dp, 800.dp).background(Tali.colors.paper)) {
          SelectionMenu(sel, narrow = false, colors = AppSettings().savedColors, pasteCount = 2, onPaste = {}, onDelete = {}, onDuplicate = {}, onCopy = {}, onCut = {}, onColor = {}, onRestyle = { _, _ -> }, onSaveShape = {}, onEditText = {}, onArrange = {}, onLock = {}, onMirror = {})
        }
      }
    }
    if (open) rule.onNodeWithContentDescription("More options").performClick()
    rule.waitForIdle()
    val dir = File("build/selection-menu").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "$name.png").outputStream())
  }

  @Test fun folded() = shot("folded", dark = false, open = false)

  @Test fun opened() = shot("opened", dark = false, open = true)

  @Test fun openedDark() = shot("opened-dark", dark = true, open = true)
}
