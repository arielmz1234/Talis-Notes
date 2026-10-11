package com.tali.notes.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hopping dots of "Backing up…", one frame every 60 ms stacked into build/working-dots.png. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h200dp-xhdpi", application = android.app.Application::class)
class WorkingTextShotTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun dotsHop() {
    rule.mainClock.autoAdvance = false
    rule.setContent {
      TaliTheme(darkTheme = false) {
        WorkingText("Backing up to Drive…", MaterialTheme.typography.bodySmall, Tali.colors.blue, Modifier.testTag("t").background(Tali.colors.surface).padding(8.dp))
      }
    }
    val frames = (0 until 20).map {
      rule.mainClock.advanceTimeBy(60)
      rule.onNodeWithTag("t").captureToImage().asAndroidBitmap()
    }
    val w = frames.maxOf { it.width }
    val sheet = Bitmap.createBitmap(w, frames.sumOf { it.height }, Bitmap.Config.ARGB_8888)
    val c = Canvas(sheet)
    var y = 0f
    frames.forEach { c.drawBitmap(it, 0f, y, null); y += it.height }
    sheet.compress(Bitmap.CompressFormat.PNG, 100, File("build/working-dots.png").outputStream())
  }
}
