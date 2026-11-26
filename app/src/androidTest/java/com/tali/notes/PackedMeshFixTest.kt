package com.tali.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.HardwareRenderer
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tali.notes.geometry.Pt
import com.tali.notes.ink.Brushes
import com.tali.notes.ink.PackedMeshFix
import com.tali.notes.ink.StrokeFactory
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PenType
import java.io.File
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every pen, line style and highlighter tip draws the same through the corrected finished-stroke
 * shader ([PackedMeshFix]) as through Ink's own, on the real graphics pipeline (meshes only draw on
 * a hardware canvas), and every mesh layout's shader was corrected without a failure. On the
 * emulator, whose maths is exact, the two must match; on a Mali tablet Ink's own may not, which is
 * the point of the fix. Saves `packed-mesh-fix.png` (Ink's above, corrected below) in the app's
 * external files folder. Run with `adb shell am instrument`.
 */
@RunWith(AndroidJUnit4::class)
class PackedMeshFixTest {
  private val w = 1200
  private val h = 90

  /** [strokes] drawn by [renderer] on the GPU, as a bitmap. */
  private fun draw(renderer: CanvasStrokeRenderer, strokes: List<Stroke>, scale: Float): Bitmap {
    val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
    val node = RenderNode("strokes").apply { setPosition(0, 0, w, h) }
    val c: Canvas = node.beginRecording()
    c.drawColor(0xFFFFFFFF.toInt())
    val m = Matrix().apply { setScale(scale, scale) }
    c.concat(m)
    strokes.forEach { renderer.draw(c, it, m) }
    node.endRecording()
    val hw = HardwareRenderer().apply {
      setSurface(reader.surface)
      setContentRoot(node)
    }
    hw.createRenderRequest().setWaitForPresent(true).syncAndDraw()
    val image = reader.acquireNextImage()
    val bmp = Bitmap.wrapHardwareBuffer(image.hardwareBuffer!!, null)!!.copy(Bitmap.Config.ARGB_8888, false)
    image.close()
    hw.destroy()
    reader.close()
    return bmp
  }

  /** A line of handwriting-like loops with [type] in [line] style. */
  private fun writing(type: PenType, line: LineStyle, square: Boolean, color: Int): List<Stroke> {
    val brush = Brushes.brush(type, color, if (type == PenType.HIGHLIGHTER) 14f else 2.5f, line, square)
    return List(8) { k ->
      val x0 = 20f + k * 70f
      StrokeFactory.strokeAlong(List(60) { i -> Pt(x0 + i * 1f + 14f * sin(i * 0.3f), 40f + 22f * sin(i * 0.21f + k)) }, brush)
    }
  }

  @Test
  fun everyPenDrawsTheSameThroughTheCorrectedShader() {
    assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.UPSIDE_DOWN_CAKE) // meshes (Android 14 draws paths)
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    Brushes.init(context.resources)
    val fixedRenderer = Brushes.renderer()
    val inksOwn = CanvasStrokeRenderer.create(forcePathRendering = false, textureStore = Brushes.textureStore)
    val cases =
      PenType.offered.flatMap { t -> listOf(LineStyle.SOLID, LineStyle.DOTTED, LineStyle.LONG_DASH).map { Triple(t, it, false) } } +
        Triple(PenType.HIGHLIGHTER, LineStyle.SOLID, true)
    val sheet = Bitmap.createBitmap(w, cases.size * 2 * h, Bitmap.Config.ARGB_8888)
    val sc = Canvas(sheet)
    val report = StringBuilder()
    var worst = 0
    cases.forEachIndexed { i, (type, line, square) ->
      val strokes = writing(type, line, square, if (type == PenType.HIGHLIGHTER) 0x80FFD54F.toInt() else 0xFF1B1F2A.toInt())
      for (scale in listOf(1f, 0.25f, 3f)) {
        val a = draw(inksOwn, strokes, scale)
        val b = draw(fixedRenderer, strokes, scale)
        var diff = 0
        for (y in 0 until h) for (x in 0 until w) {
          val p = a.getPixel(x, y)
          val q = b.getPixel(x, y)
          val d = maxOf(kotlin.math.abs((p shr 16 and 255) - (q shr 16 and 255)), kotlin.math.abs((p shr 8 and 255) - (q shr 8 and 255)), kotlin.math.abs((p and 255) - (q and 255)))
          if (d > 8) diff++
        }
        worst = maxOf(worst, diff)
        report.append("$type $line square=$square scale=$scale differing=$diff\n")
        if (scale == 1f) {
          sc.drawBitmap(a, 0f, (2 * i) * h.toFloat(), null)
          sc.drawBitmap(b, 0f, (2 * i + 1) * h.toFloat(), null)
        }
      }
    }
    val dir = context.getExternalFilesDir(null)!!
    File(dir, "packed-mesh-fix.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    report.append("corrected=${PackedMeshFix.corrected} failed=${PackedMeshFix.failed}\n")
    File(dir, "packed-mesh-fix.txt").writeText(report.toString())
    assertEquals("a shader couldn't be corrected:\n$report", 0, PackedMeshFix.failed)
    assertTrue("no shader was corrected:\n$report", PackedMeshFix.corrected > 0)
    assertTrue("drawn differently:\n$report", worst <= 4)
  }
}
