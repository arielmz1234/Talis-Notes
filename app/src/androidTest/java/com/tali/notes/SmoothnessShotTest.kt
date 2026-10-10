package com.tali.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tali.notes.ink.Brushes
import com.tali.notes.model.PenType
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the same quick, slightly shaky handwriting (as the S Pen reports it at 240 Hz) with the
 * old brush settings and the app's current ones, zoomed in, one above the other, to judge how
 * smooth strokes look. Saves `smoothness.png` in the app's external files folder (run it with
 * `adb shell am instrument`, since Gradle uninstalls the app afterwards).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalInkCustomBrushApi::class)
class SmoothnessShotTest {
  /** Cursive loops written in screen pixels at [zoom] (world = screen / zoom), with digitizer jitter. */
  private fun inputs(seed: Int, zoom: Float): MutableStrokeInputBatch {
    val rnd = Random(seed)
    val batch = MutableStrokeInputBatch()
    val hz = 240f
    val n = (1.6f * hz).toInt()
    for (i in 0 until n) {
      val t = i / hz
      // Four loops travelling right, about 70 px tall on screen, written fast.
      val a = t * 2f * PI.toFloat() * 2.6f
      val x = 40f + t * 260f + 34f * cos(a + PI.toFloat())
      val y = 90f - 38f * sin(a) * (0.8f + 0.2f * sin(t * 7f))
      val jx = (rnd.nextFloat() - 0.5f) * 2f
      val jy = (rnd.nextFloat() - 0.5f) * 2f
      batch.add(InputToolType.STYLUS, (x + jx) / zoom, (y + jy) / zoom, (t * 1000).toLong(), pressure = 0.5f + 0.2f * sin(t * 5f))
    }
    return batch
  }

  @Test
  fun oldAgainstNow() {
    val zoom = 4f
    val size = 2.4f / zoom * 3f
    // Top: Ink's defaults at the old precision. Below: the fineliner and fountain pen as the app builds them now.
    val brushes =
      listOf(
        Brush.createWithColorIntArgb(StockBrushes.marker(), 0xFF1B1F2A.toInt(), size, 0.08f),
        Brushes.brush(PenType.FINELINER, 0xFF1B1F2A.toInt(), size),
        Brush.createWithColorIntArgb(StockBrushes.pressurePen(), 0xFF1B1F2A.toInt(), size, 0.08f),
        Brushes.brush(PenType.FOUNTAIN, 0xFF1B1F2A.toInt(), size),
      )
    val scale = zoom * 3f
    val rowH = 520
    val bmp = Bitmap.createBitmap(1300, rowH * brushes.size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFFFFFFF.toInt())
    val renderer = Brushes.renderer()
    brushes.forEachIndexed { row, brush ->
      val stroke = Stroke(brush, inputs(7, zoom))
      c.save()
      c.translate(0f, (row * rowH).toFloat())
      c.scale(scale, scale)
      c.translate(-60f / zoom, -40f / zoom)
      renderer.draw(c, stroke, Matrix().apply { setScale(scale, scale) })
      c.restore()
    }
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    val out = File(ctx.getExternalFilesDir(null), "smoothness.png")
    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
