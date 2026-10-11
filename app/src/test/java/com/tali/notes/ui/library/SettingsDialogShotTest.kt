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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi", application = android.app.Application::class)
class SettingsDialogShotTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun settings() {
    rule.setContent { TaliTheme(darkTheme = false) { SettingsDialog(AppSettings(), {}, {}, {}, {}) } }
    rule.waitForIdle()
    val dir = File("build/settings").apply { mkdirs() }
    rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "settings.png").outputStream())
  }
}
