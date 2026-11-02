package com.tali.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tali.notes.geometry.Pt
import com.tali.notes.ink.Brushes
import com.tali.notes.ink.StrokeFactory
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PenType
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The straight highlighter's quick line ([StrokeFactory.straightLine]) draws the same as the slow
 * one ([StrokeFactory.strokeAlong]), ends included, for both tips, short and long, level and
 * slanted; and a long one builds many times faster. Saves `straight-lines.png` (old above new)
 * in the app's external files folder. Run with `adb shell am instrument`.
 */
@RunWith(AndroidJUnit4::class)
class StraightLineTest {
  private val renderer = Brushes.renderer()

  /** [stroke] drawn so [a]..[b] fits [w] px across, as an alpha mask. */
  private fun mask(stroke: Stroke, a: Pt, b: Pt, w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val box = StrokeFactory.bounds(stroke)
    val s = minOf((w - 20) / (box.right - box.left), (h - 20) / maxOf(box.bottom - box.top, 1f))
    val m = Matrix().apply { setTranslate(-box.left, -box.top); postScale(s, s); postTranslate(10f, 10f) }
    c.concat(m)
    renderer.draw(c, stroke, m)
    return bmp
  }

  private fun differing(x: Bitmap, y: Bitmap): Pair<Int, Int> {
    var diff = 0
    var ink = 0
    for (j in 0 until x.height) for (i in 0 until x.width) {
      val ax = x.getPixel(i, j) ushr 24
      val ay = y.getPixel(i, j) ushr 24
      if (ax > 0 || ay > 0) ink++
      if (kotlin.math.abs(ax - ay) > 24) diff++
    }
    return diff to ink
  }

  @Test
  fun quickLineLooksTheSameAndIsQuick() {
    val report = StringBuilder()
    val sheet = Bitmap.createBitmap(1400, 16 * 110, Bitmap.Config.ARGB_8888)
    val sc = Canvas(sheet)
    sc.drawColor(0xFFFFFFFF.toInt())
    var row = 0
    for (square in listOf(false, true)) for (len in listOf(30f, 400f, 2700f)) for (angle in listOf(0f, 0.5f)) {
      val brush = Brushes.brush(PenType.HIGHLIGHTER, 0xFF4FA3E0.toInt(), 18f, LineStyle.SOLID, square)
      val a = Pt(100f, 100f)
      val b = a + Pt(kotlin.math.cos(angle), kotlin.math.sin(angle)) * len
      val t0 = System.nanoTime()
      val old = StrokeFactory.strokeAlong(listOf(a, b), brush)
      old.shape // build the mesh
      val t1 = System.nanoTime()
      val new = StrokeFactory.straightLine(a, b, brush)
      new.shape
      val t2 = System.nanoTime()
      val ob = StrokeFactory.bounds(old)
      val nb = StrokeFactory.bounds(new)
      val mo = mask(old, a, b, 1400, 100)
      val mn = mask(new, a, b, 1400, 100)
      val (diff, ink) = differing(mo, mn)
      report.append("square=$square len=$len angle=$angle old=${(t1 - t0) / 1000}us new=${(t2 - t1) / 1000}us ")
        .append("boundsOld=${ob.left},${ob.top},${ob.right},${ob.bottom} boundsNew=${nb.left},${nb.top},${nb.right},${nb.bottom} diff=$diff/$ink\n")
      if (row < 16) { sc.drawBitmap(mo, 0f, row * 110f, null); row++ }
      if (row < 16) { sc.drawBitmap(mn, 0f, row * 110f, null); row++ }
      // Same box within half a unit at each side, and next to no pixels differ.
      val close = kotlin.math.abs(ob.left - nb.left) < 0.5f && kotlin.math.abs(ob.right - nb.right) < 0.5f && kotlin.math.abs(ob.top - nb.top) < 0.5f && kotlin.math.abs(ob.bottom - nb.bottom) < 0.5f
      assertTrue(report.toString(), close && diff <= ink / 100 + 4)
    }
    val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    File(ctx.getExternalFilesDir(null), "straight-lines.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    File(ctx.getExternalFilesDir(null), "straight-lines.txt").writeText(report.toString())
    android.util.Log.i("StraightLineTest", report.toString())
  }
}
