package com.tali.notes.ui.library

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import com.tali.notes.data.AppSettings
import com.tali.notes.theme.TaliTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The new-notebook card on a landscape tablet (her Tab S9 FE is about 1280 × 800 dp); frames in build/new-notebook. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi", application = android.app.Application::class)
class NewNotebookFitTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun landscape() {
    rule.setContent {
      TaliTheme(darkTheme = false) {
        NewNotebookDialog(AppSettings(), folderName = "Statics", defaultCover = 0xFF9B6BF2.toInt(), onDismiss = {}, importImage = { null }, onAddPaper = {}, onRemovePaper = {}, onCreate = {})
      }
    }
    rule.waitForIdle()
    val dir = File("build/new-notebook").apply { mkdirs() }
    rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "landscape.png").outputStream())
  }
}
