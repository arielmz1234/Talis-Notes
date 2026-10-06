package com.tali.notes.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.tali.notes.model.ShapeKind
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shape tiles' icons at their real size (30 dp), on a Tab S6 Lite's density; in build/shape-icons. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h200dp-hdpi", application = android.app.Application::class)
class ShapeIconsShotTest {
  @get:Rule val rule = createComposeRule()

  @Test
  fun icons() {
    rule.setContent {
      Row(Modifier.testTag("stage").background(Color.White).padding(12.dp)) {
        listOf(ShapeKind.ARROW, ShapeKind.DOUBLE_ARROW, ShapeKind.MOMENT, ShapeKind.DIMENSION, ShapeKind.POINT_LOAD).forEach { ShapeIcon(it, Color(0xFF1B1F2A), Modifier.padding(6.dp), size = 96.dp) }
      }
    }
    rule.waitForIdle()
    val dir = File("build/shape-icons").apply { mkdirs() }
    rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, File(dir, "icons.png").outputStream())
  }
}
