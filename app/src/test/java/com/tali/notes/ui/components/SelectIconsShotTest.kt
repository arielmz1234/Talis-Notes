package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The old lasso, the dotted lasso and the free-form circle, large; in build/select-icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w600dp-h200dp-xhdpi", application = android.app.Application::class)
class SelectIconsShotTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun icons() {
    rule.setContent {
      Row(Modifier.testTag("stage").background(Color.White).padding(12.dp)) {
        listOf(TaliIcons.Lasso, TaliIcons.LassoDotted, TaliIcons.FreeSelect, TaliIcons.MirrorHorizontal, TaliIcons.MirrorVertical).forEach { Icon(it, null, tint = Color(0xFF1B1F2A), modifier = Modifier.padding(8.dp).size(64.dp)) }
      }
    }
    rule.waitForIdle()
    val dir = File("build/select-icons").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "icons.png").outputStream())
  }
}
