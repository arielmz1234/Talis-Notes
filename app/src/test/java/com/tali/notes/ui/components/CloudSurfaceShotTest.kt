package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
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

/** A card and a toolbar on the cloud surface, light and dark; frames in build/surfaces. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h500dp-mdpi", application = android.app.Application::class)
class CloudSurfaceShotTest {
  @get:Rule val rule = createComposeRule()

  private fun shot(name: String, dark: Boolean) {
    rule.setContent {
      TaliTheme(darkTheme = dark) {
        Box(Modifier.testTag("stage").background(Tali.colors.background).padding(40.dp)) {
          Column {
            FloatingBar(Modifier.height(60.dp).width(420.dp), crown = CloudCrown.EDGE) { Spacer(Modifier.size(400.dp, 50.dp)) }
            Spacer(Modifier.height(40.dp))
            Box(Modifier.width(420.dp).cloudSurface(24.dp, shadowDepth = 12.dp).padding(28.dp)) { Text("New notebook", color = Tali.colors.ink) }
          }
        }
      }
    }
    rule.waitForIdle()
    val dir = File("build/surfaces").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "$name.png").outputStream())
  }

  @Test fun light() = shot("light", false)

  @Test fun dark() = shot("dark", true)
}
