package com.tali.notes.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first-open card (Google Drive marked Recommended) and the library's backup banner, for a look (`build/backup-nudge/`). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1280dp-h800dp-land-mdpi", application = android.app.Application::class)
class BackupNudgeShotTest {
  @get:Rule val rule = createComposeRule()

  private val dir = File("build/backup-nudge").apply { mkdirs() }

  @Test
  fun welcomeCardMarksDriveRecommended() {
    rule.setContent { TaliTheme(darkTheme = false) { WelcomeRestoreCard(onDismiss = {}) } }
    rule.waitForIdle()
    rule.onNode(isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "welcome.png").outputStream())
  }

  @Test
  fun banner() {
    rule.setContent {
      TaliTheme(darkTheme = false) {
        Box(Modifier.testTag("banner").background(Tali.colors.background).padding(24.dp).width(900.dp)) {
          BackupBanner(onClose = {}, onFinished = {})
        }
      }
    }
    rule.waitForIdle()
    rule.onNodeWithTag("banner").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "banner.png").outputStream())
  }
}
