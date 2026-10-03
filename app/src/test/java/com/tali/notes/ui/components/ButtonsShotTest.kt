package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
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

/** Quiet and primary buttons on the sky and on a card, light and dark; frames in build/buttons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w700dp-h400dp-xhdpi", application = android.app.Application::class)
class ButtonsShotTest {
  @get:Rule val rule = createComposeRule()

  private fun shot(name: String, dark: Boolean) {
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        val c = Tali.colors
        Column(Modifier.testTag("stage")) {
          Row(Modifier.background(Brush.verticalGradient(listOf(c.background, c.sidebarEnd))).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton("Select none") {}
            QuietButton("Empty", color = c.danger) {}
            PrimaryButton("Done") {}
          }
          Row(Modifier.background(c.surface).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuietButton("Cancel") {}
            PrimaryButton("Create") {}
          }
        }
      }
    }
    rule.waitForIdle()
    val dir = File("build/buttons").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "$name.png").outputStream())
  }

  @Test fun light() = shot("light", false)

  @Test fun dark() = shot("dark", true)
}
