package com.tali.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tali.notes.ink.Brushes
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fountain pen where it slows and turns: a handwritten "d" (bowl, up the stem, back down,
 * a sharp corner into the tail) and a stem gone up and straight back down, with the digitizer's
 * jitter, at 240 Hz. Top row: Ink's stock pressure pen (its width catches up over time); bottom
 * row: the app's (over distance). Saves `fountain-turns.png` in the app's external files folder
 * (run with `adb shell am instrument`, then pull it).
 */
@RunWith(AndroidJUnit4::class)
class PressureEdgesShotTest {
  private class Sample(val x: Float, val y: Float, val t: Long, val p: Float)

  /** [path] over [seconds], its speed easing to a stop at each of [stops] (fractions along it). */
  private fun write(seconds: Float, stops: List<Float>, seed: Int, path: (Float) -> Pair<Float, Float>): List<Sample> {
    val rnd = Random(seed)
    val n = (seconds * 240).toInt()
    // Slows right down at each stop: progress spends longer near them.
    val weights = FloatArray(n + 1) { i ->
      val u = i / n.toFloat()
      1f / (0.12f + stops.fold(1f) { acc, s -> acc * (1f - exp(-((u - s) * 14f).let { it * it })) })
    }
    val total = weights.sum()
    var acc = 0f
    return (0..n).map { i ->
      acc += weights[i]
      val u = acc / total
      val (x, y) = path(u)
      Sample(x + (rnd.nextFloat() - 0.5f) * 0.5f, y + (rnd.nextFloat() - 0.5f) * 0.5f, (i * 1000L / 240), 0.55f + 0.1f * rnd.nextFloat())
    }
  }

  private fun d(u: Float): Pair<Float, Float> =
    when {
      u < 0.4f -> {
        val a = u / 0.4f * 2f * PI.toFloat()
        (40f + 24f * cos(PI.toFloat() * 0.1f - a) * -1f + 24f) to (100f + 22f * sin(PI.toFloat() * 0.1f - a))
      }
      u < 0.6f -> { val s = (u - 0.4f) / 0.2f; (88f - 2f * s) to (100f - 90f * s) }
      u < 0.85f -> { val s = (u - 0.6f) / 0.25f; (86f + 3f * s) to (10f + 112f * s) }
      else -> { val s = (u - 0.85f) / 0.15f; (89f + 16f * s) to (122f - 14f * s) }
    }

  private fun upAndBack(u: Float): Pair<Float, Float> {
    val s = sin(u * PI.toFloat())
    return (40f + 5f * u) to (130f - 110f * s)
  }

  /** Up a stem, resting ~80 ms at the top (the digitizer jittering, pressure easing off), then back down and off to the right. */
  private fun pause(): List<Sample> {
    val rnd = Random(9)
    val out = ArrayList<Sample>()
    var t = 0L
    fun add(x: Float, y: Float, p: Float) { out += Sample(x + (rnd.nextFloat() - 0.5f) * 0.8f, y + (rnd.nextFloat() - 0.5f) * 0.8f, t, p); t += 4 }
    for (i in 0..40) { val s = i / 40f; add(40f + 4f * s, 130f - 110f * (1 - (1 - s) * (1 - s)), 0.6f) }
    for (i in 0..20) add(44f, 20f, 0.6f - 0.3f * sin(i / 20f * PI.toFloat()))
    for (i in 0..40) { val s = i / 40f; add(44f + 2f * s, 20f + 110f * s * s, 0.65f) }
    for (i in 0..20) { val s = i / 20f; add(46f + 20f * s, 130f - 12f * s, 0.6f) }
    return out
  }

  private fun stroke(s: List<Sample>, brush: Brush): Stroke {
    val b = MutableStrokeInputBatch()
    s.forEach { b.add(InputToolType.STYLUS, it.x, it.y, it.t, pressure = it.p) }
    return Stroke(brush, b)
  }

  @Test
  fun stockAgainstOurs() {
    val scale = 4f
    val cell = 600
    val shapes = listOf(write(1.2f, listOf(0.6f, 0.85f), 3, ::d), write(0.7f, listOf(0.5f), 5, ::upAndBack), pause())
    val width = 10f
    val brushes = listOf(
      Brush.createWithColorIntArgb(StockBrushes.pressurePen(), 0xFF1E9E6A.toInt(), width, 0.02f),
      Brush.createWithColorIntArgb(Brushes.fountain, 0xFF1E9E6A.toInt(), width, 0.02f),
    )
    val bmp = Bitmap.createBitmap(cell * shapes.size, cell * brushes.size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFFFFFFF.toInt())
    val renderer = Brushes.renderer()
    shapes.forEachIndexed { col, samples ->
      brushes.forEachIndexed { row, brush ->
        val m = Matrix().apply { setScale(scale, scale); postTranslate(col * cell + 20f, row * cell + 0f) }
        c.save()
        c.clipRect(col * cell.toFloat(), row * cell.toFloat(), (col + 1f) * cell, (row + 1f) * cell)
        c.concat(m)
        renderer.draw(c, stroke(samples, brush), m)
        c.restore()
      }
    }
    val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "fountain-turns.png")
    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
  /** A sharp zigzag (over, back, over) at a few speeds: stock/ours, drawn as meshes and as paths. */
  @Test
  fun zigzags() {
    val scale = 3f
    val cell = 520
    fun zig(u: Float): Pair<Float, Float> {
      val legs = 3
      val k = minOf((u * legs).toInt(), legs - 1)
      val s = u * legs - k
      val x = if (k % 2 == 0) 20f + 120f * s else 140f - 120f * s
      return x to (40f + 30f * (k + s) - 20f * s)
    }
    val shapes = listOf(write(0.25f, listOf(0.333f, 0.666f), 11, ::zig), write(0.6f, listOf(0.333f, 0.666f), 12, ::zig), write(0.12f, listOf(), 13, ::zig))
    val width = 14f
    val families = listOf(StockBrushes.pressurePen(), Brushes.fountain)
    val renderers = listOf(androidx.ink.rendering.android.canvas.CanvasStrokeRenderer.create(), androidx.ink.rendering.android.canvas.CanvasStrokeRenderer.create(forcePathRendering = true))
    val bmp = Bitmap.createBitmap(cell * shapes.size, cell * 4, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(0xFFFFFFFF.toInt())
    shapes.forEachIndexed { col, samples ->
      for (row in 0 until 4) {
        val brush = Brush.createWithColorIntArgb(families[row % 2], 0xFF1B2230.toInt(), width, 0.02f)
        val m = Matrix().apply { setScale(scale, scale); postTranslate(col * cell + 0f, row * cell + 0f) }
        c.save()
        c.clipRect(col * cell.toFloat(), row * cell.toFloat(), (col + 1f) * cell, (row + 1f) * cell)
        c.concat(m)
        renderers[row / 2].draw(c, stroke(samples, brush), m)
        c.restore()
      }
    }
    val out = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "zigzags.png")
    out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }
}
