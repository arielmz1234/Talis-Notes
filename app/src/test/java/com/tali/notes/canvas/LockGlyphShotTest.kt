package com.tali.notes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frames of the padlock closing and opening, light and dark, in build/lock-frames. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class LockGlyphShotTest {
  @Test
  fun frames() {
    val d = 3f
    val opens = listOf(1f, 0.75f, 0.5f, 0.25f, 0f)
    val bmp = Bitmap.createBitmap((60 * d * opens.size).toInt(), (60 * d * 2).toInt(), Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    for ((row, dark) in listOf(false, true).withIndex()) {
      c.save()
      c.clipRect(0f, row * 60 * d, bmp.width.toFloat(), (row + 1) * 60 * d)
      c.drawColor(if (dark) 0xFF15121F.toInt() else 0xFFF6F2FF.toInt())
      c.restore()
      opens.forEachIndexed { i, o -> LockGlyph.draw(c, d, dark, (i + 0.5f) * 60 * d, (row + 0.5f) * 60 * d, 1f, 1f, o) }
    }
    val dir = File("build/lock-frames").apply { mkdirs() }
    bmp.compress(Bitmap.CompressFormat.PNG, 100, File(dir, "lock.png").outputStream())
  }
}
