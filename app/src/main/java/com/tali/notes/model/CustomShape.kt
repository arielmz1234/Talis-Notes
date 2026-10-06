package com.tali.notes.model

import com.tali.notes.geometry.Affine
import com.tali.notes.geometry.Geo
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import com.tali.notes.geometry.ShapeMatcher
import com.tali.notes.ink.StrokeFactory
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * A shape she saved herself from something she drew. Its geometry is stored normalised: the top-left
 * corner at the origin and the longer side [SIZE] units long. Holding the pen at the end of a stroke
 * that looks like it snaps the stroke into this clean version, just like the built-in shapes.
 */
@Serializable
data class CustomShape(val id: String, val name: String, val geometry: ShapeGeometry) {
  @Transient private var cachedCloud: ShapeMatcher.Cloud? = null

  val cloud: ShapeMatcher.Cloud?
    get() = cachedCloud ?: ShapeMatcher.cloud(geometry.paths(REFERENCE_PEN)).also { cachedCloud = it }

  private val box: Rect
    get() = Rect.around(geometry.paths(REFERENCE_PEN).flatten()) ?: Rect(0f, 0f, SIZE, SIZE)

  /** Scales the shape to cover the area of [target] (keeping its proportions), centred on it. */
  fun placeOver(target: Rect): ShapeGeometry {
    val b = box
    val sx = if (b.width > 1f) target.width / b.width else 0f
    val sy = if (b.height > 1f) target.height / b.height else 0f
    val s =
      when {
        sx > 0f && sy > 0f -> sqrt(sx * sy)
        else -> max(sx, sy)
      }.coerceAtLeast(0.05f)
    return placed(b, s, target.center)
  }

  /** Scales the shape to fit inside the box dragged from [p0] to [p1], for the shape tool. */
  fun fitInto(p0: Pt, p1: Pt): ShapeGeometry {
    val b = box
    val w = max(abs(p1.x - p0.x), 8f)
    val h = max(abs(p1.y - p0.y), 8f)
    val s = min(if (b.width > 1f) w / b.width else Float.MAX_VALUE, if (b.height > 1f) h / b.height else Float.MAX_VALUE).coerceIn(0.05f, 100f)
    return placed(b, s, Pt((p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f))
  }

  /**
   * The geometry as it's drawn: lines that came from her ink are smooth curves. (Shapes saved
   * before that drew as straight bits between their points and looked choppy; they get it too.)
   */
  private val drawn: ShapeGeometry by lazy { smoothInk(geometry) }

  private fun placed(b: Rect, s: Float, center: Pt): ShapeGeometry =
    drawn.transformed(Affine(a = s, d = s, tx = center.x - b.center.x * s, ty = center.y - b.center.y * s))

  private fun abs(v: Float) = kotlin.math.abs(v)

  companion object {
    const val SIZE = 100f

    /** Arrowheads and hatching are sized for this pen when shapes are compared and previewed. */
    const val REFERENCE_PEN = 2.4f

    /**
     * Turns selected items into one shape: clean shapes keep their exact geometry, hand-drawn ink is
     * lightly simplified into polylines. Images are ignored. Returns null if nothing usable is selected.
     */
    fun geometryFrom(items: List<CanvasItem>): ShapeGeometry? {
      val parts = ArrayList<ShapeGeometry>()
      val all = items.filter { it !is ImageItem }
      val bounds = all.map { it.bounds }.reduceOrNull(Rect::union) ?: return null
      val diag = sqrt(bounds.width * bounds.width + bounds.height * bounds.height)
      if (diag < 8f) return null
      for (item in all) {
        when (item) {
          is ShapeItem -> parts += item.geometry
          is InkItem -> {
            // Lightly simplified (fine enough to keep the curve), then drawn smooth through the points.
            val pts = Geo.simplify(StrokeFactory.points(item.stroke), max(0.25f, diag * 0.0015f))
            if (pts.size >= 2) parts += PolyShape(pts, closed = false, smooth = true)
          }
          is ImageItem, is TextItem, is AudioItem -> Unit
        }
      }
      if (parts.isEmpty()) return null
      val g = if (parts.size == 1) parts[0] else GroupShape(parts)
      return normalized(g)
    }

    /** Open lines (from ink) drawn as smooth curves, inside groups too. */
    fun smoothInk(g: ShapeGeometry): ShapeGeometry =
      when (g) {
        is PolyShape -> if (!g.closed && !g.smooth && g.points.size > 2) g.copy(smooth = true) else g
        is GroupShape -> g.copy(parts = g.parts.map(::smoothInk))
        else -> g
      }

    fun normalized(g: ShapeGeometry): ShapeGeometry? {
      val box = Rect.around(g.paths(REFERENCE_PEN).flatten()) ?: return null
      val longest = max(box.width, box.height)
      if (longest < 1e-3f) return null
      val s = SIZE / longest
      return g.transformed(Affine(a = s, d = s, tx = -box.left * s, ty = -box.top * s))
    }
  }
}
