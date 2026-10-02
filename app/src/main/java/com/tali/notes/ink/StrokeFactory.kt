package com.tali.notes.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import com.tali.notes.geometry.Affine
import com.tali.notes.geometry.Geo
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import com.tali.notes.model.PenType
import com.tali.notes.model.LineStyle
import com.tali.notes.model.ShapeGeometry
import kotlin.math.ceil

object StrokeFactory {
  /**
   * Synthetic pen speed for generated shapes, in world units per millisecond. Deliberately slow:
   * Ink smooths input over a short time window, and a slow pen keeps corners sharp.
   */
  private const val SPEED = 0.08f
  private const val SPACING = 3f

  fun bounds(stroke: Stroke): Rect {
    val box = stroke.shape.computeBoundingBox() ?: return Rect(0f, 0f, 0f, 0f)
    return Rect(box.xMin, box.yMin, box.xMax, box.yMax)
  }

  fun shapeStrokes(geometry: ShapeGeometry, pen: PenType, color: Int, size: Float, line: LineStyle = LineStyle.SOLID): List<Stroke> {
    val brush = Brushes.shapeBrush(pen, color, size)
    val paths = geometry.paths(size).filter { it.size >= 2 }
    return (if (line == LineStyle.SOLID) paths else paths.flatMap { dashes(it, size, line) }).map { strokeAlong(it, brush) }
  }

  /**
   * [path] cut into the pieces of [line] for a line [size] thick. Short paths (arrow barbs, small
   * details) stay solid, since a pattern on them just looks broken. Pen ends are round and stick
   * out half the thickness past each piece, so pieces are cut that much shorter and gaps longer,
   * leaving the visible lengths of [LineStyle.pattern]. A dot is a piece of (almost) no length.
   */
  fun dashes(path: List<Pt>, size: Float, line: LineStyle): List<List<Pt>> {
    val pattern = line.pattern.mapIndexed { i, v -> if (i % 2 == 0) maxOf((v - 1f) * size, 0.05f) else (v + 1f) * size }
    if (pattern.isEmpty() || Geo.pathLength(path) < pattern.sum() * 2.5f) return listOf(path)
    val out = mutableListOf<List<Pt>>()
    var current = mutableListOf(path[0])
    var part = 0
    var left = pattern[0]
    for (i in 0 until path.size - 1) {
      var a = path[i]
      val b = path[i + 1]
      var seg = a.dist(b)
      while (seg > 0f) {
        val drawing = part % 2 == 0
        if (seg < left) {
          left -= seg
          if (drawing) current.add(b)
          seg = 0f
        } else {
          val cut = a + (b - a) * (left / seg)
          if (drawing) {
            current.add(cut)
            out += current
          } else current = mutableListOf(cut)
          seg -= left
          a = cut
          part = (part + 1) % pattern.size
          left = pattern[part]
        }
      }
    }
    if (part % 2 == 0 && current.size >= 2) out += current
    return out
  }

  /** Builds a stroke that follows a polyline exactly, keeping every corner. */
  fun strokeAlong(path: List<Pt>, brush: Brush): Stroke {
    val batch = MutableStrokeInputBatch()
    var t = 0L
    var last: Pt? = null
    var carry = 0f
    fun add(p: Pt) {
      val prev = last
      if (prev != null) {
        if (prev.dist(p) < 0.05f) return
        carry += prev.dist(p) / SPEED
        val dt = carry.toLong()
        carry -= dt
        t += dt
      }
      batch.add(InputToolType.STYLUS, p.x, p.y, t, pressure = 0.55f)
      last = p
    }
    for (i in 0 until path.size - 1) {
      val a = path[i]
      val b = path[i + 1]
      val n = ceil(a.dist(b) / SPACING).toInt().coerceAtLeast(1)
      for (k in 0 until n) add(a + (b - a) * (k.toFloat() / n))
    }
    add(path.last())
    return Stroke(brush, batch)
  }

  /** A gentle S-curve with rising and falling pressure, used to preview a pen in its settings. */
  fun previewStroke(brush: Brush, width: Float, height: Float): Stroke {
    val batch = MutableStrokeInputBatch()
    val n = 90
    val margin = 14f
    for (i in 0..n) {
      val f = i.toFloat() / n
      val x = margin + (width - 2 * margin) * f
      val y = height / 2f + kotlin.math.sin(f * 2f * Math.PI.toFloat()) * height * 0.22f
      val pressure = 0.35f + 0.55f * kotlin.math.sin(f * Math.PI.toFloat())
      batch.add(InputToolType.STYLUS, x, y, (i * 6).toLong(), pressure = pressure)
    }
    return Stroke(brush, batch)
  }

  fun points(stroke: Stroke): List<Pt> {
    val input = StrokeInput()
    return List(stroke.inputs.size) { i ->
      stroke.inputs.populate(i, input)
      Pt(input.x, input.y)
    }
  }

  /** Moves/scales a stroke by rewriting its inputs; brush size scales with it. */
  fun transform(stroke: Stroke, m: Affine): Stroke {
    val out = MutableStrokeInputBatch()
    val input = StrokeInput()
    for (i in 0 until stroke.inputs.size) {
      stroke.inputs.populate(i, input)
      val p = m.apply(Pt(input.x, input.y))
      out.add(input.toolType, p.x, p.y, input.elapsedTimeMillis, input.strokeUnitLengthCm, input.pressure, input.tiltRadians, input.orientationRadians)
    }
    val s = m.scaleFactor()
    val brush = if (s == 1f) stroke.brush else stroke.brush.copy(size = (stroke.brush.size * s).coerceIn(0.3f, 400f))
    return Stroke(brush, out)
  }

  /**
   * Splits a stroke where the eraser passed. [erased] reports whether a point is under the
   * eraser. Inputs are densified first so that a gap between sparse samples can still be cut.
   * Returns null when nothing was erased.
   */
  fun split(stroke: Stroke, maxGap: Float, erased: (Float, Float) -> Boolean): List<Stroke>? {
    val dense = densify(stroke.inputs, maxGap)
    val runs = mutableListOf<MutableStrokeInputBatch>()
    var current: MutableStrokeInputBatch? = null
    var removedAny = false
    for (input in dense) {
      if (erased(input.x, input.y)) {
        removedAny = true
        current = null
      } else {
        val run = current ?: MutableStrokeInputBatch().also { runs += it; current = it }
        run.add(input)
      }
    }
    if (!removedAny) return null
    return runs.filter { it.size >= 2 }.map { Stroke(stroke.brush, it) }
  }

  private fun densify(inputs: StrokeInputBatch, maxGap: Float): List<StrokeInput> {
    val out = ArrayList<StrokeInput>(inputs.size * 2)
    var prev: StrokeInput? = null
    for (i in 0 until inputs.size) {
      val cur = inputs[i]
      val p = prev
      if (p != null) {
        val d = kotlin.math.hypot(cur.x - p.x, cur.y - p.y)
        val steps = (d / maxGap).toInt()
        for (k in 1..steps) {
          val f = k.toFloat() / (steps + 1)
          val mid = StrokeInput()
          mid.update(
            x = p.x + (cur.x - p.x) * f,
            y = p.y + (cur.y - p.y) * f,
            elapsedTimeMillis = p.elapsedTimeMillis + ((cur.elapsedTimeMillis - p.elapsedTimeMillis) * f).toLong(),
            toolType = cur.toolType,
            strokeUnitLengthCm = cur.strokeUnitLengthCm,
            pressure = if (p.hasPressure && cur.hasPressure) p.pressure + (cur.pressure - p.pressure) * f else cur.pressure,
            tiltRadians = cur.tiltRadians,
            orientationRadians = cur.orientationRadians,
          )
          out += mid
        }
      }
      out += cur
      prev = cur
    }
    return out
  }
}
