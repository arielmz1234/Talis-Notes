package com.tali.notes.model

import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.StrokeFactory
import kotlin.math.sqrt

/**
 * A shape's outline as drawn: vector lines with sharp, mitred corners, like a ruler and a technical
 * pen. Ink's round pen tip rounds every corner on the outside (a rectangle's inside corners were
 * sharp, its outside ones round), so shapes drawn with an even-width pen are drawn as paths
 * instead ([ShapeItem.vector]). Their Ink strokes are still built, for picking up and erasing.
 *
 * Open lines keep round ends, as before (dash pieces are cut for round ends, see
 * [StrokeFactory.dashes]); closed outlines have no ends at all.
 */
object ShapeOutline {
  /** Corners sharper than this (the miter's length against the line's thickness) are cut flat, as Android does by default. */
  const val MITER_LIMIT = 4f

  /** One line of the outline: [points] in order, [closed] when it comes back to its start (the start not repeated). */
  class Piece(val points: List<Pt>, val closed: Boolean)

  /** The lines of [geometry] drawn [size] thick in [line] style. */
  fun pieces(geometry: ShapeGeometry, size: Float, line: LineStyle): List<Piece> {
    val paths = geometry.paths(size).filter { it.size >= 2 }
    val parts = if (line == LineStyle.SOLID) paths else paths.flatMap { StrokeFactory.dashes(it, size, line) }
    return parts.map { p ->
      val closed = p.size >= 4 && p.first().dist(p.last()) < 0.01f
      Piece(if (closed) p.dropLast(1) else p, closed)
    }
  }

  /** What [pieces] drawn [size] thick cover: round ends and sides, and each corner's mitred tip. */
  fun bounds(pieces: List<Piece>, size: Float): Rect? {
    val half = size / 2f
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var r = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    fun add(x: Float, y: Float) {
      l = minOf(l, x)
      t = minOf(t, y)
      r = maxOf(r, x)
      b = maxOf(b, y)
    }
    for (piece in pieces) {
      val pts = piece.points
      for (p in pts) {
        add(p.x - half, p.y - half)
        add(p.x + half, p.y + half)
      }
      val n = pts.size
      val joins = if (piece.closed) 0 until n else 1 until n - 1
      for (i in joins) {
        miterTip(pts[(i - 1 + n) % n], pts[i], pts[(i + 1) % n], half)?.let { add(it.x, it.y) }
      }
    }
    return if (l > r) null else Rect(l, t, r, b)
  }

  /** The outer tip of the mitred corner at [v] (coming from [a], going to [c]), or null when it's cut flat or there's no corner. */
  internal fun miterTip(a: Pt, v: Pt, c: Pt, half: Float): Pt? {
    val u = (v - a).normalized()
    val w = (c - v).normalized()
    if (u == Pt.ZERO || w == Pt.ZERO) return null
    val cos = u.dot(w)
    // Length of the miter against the line's thickness: 1 / sin(half the angle between the sides).
    val ratio = sqrt(2f / maxOf(1f + cos, 1e-6f))
    if (ratio > MITER_LIMIT) return null
    val out = (u - w).normalized()
    if (out == Pt.ZERO) return null
    return v + out * (half * ratio)
  }

  /** [pieces] as one Android path: drawn in one go, a see-through outline doesn't darken where its lines cross. */
  fun path(pieces: List<Piece>, into: android.graphics.Path = android.graphics.Path()): android.graphics.Path {
    into.rewind()
    for (piece in pieces) {
      piece.points.forEachIndexed { i, p -> if (i == 0) into.moveTo(p.x, p.y) else into.lineTo(p.x, p.y) }
      if (piece.closed) into.close()
    }
    return into
  }

  /** A paint for outlines: mitred corners, round ends. */
  fun paint() = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    style = android.graphics.Paint.Style.STROKE
    strokeCap = android.graphics.Paint.Cap.ROUND
    strokeJoin = android.graphics.Paint.Join.MITER
    strokeMiter = MITER_LIMIT
  }
}
