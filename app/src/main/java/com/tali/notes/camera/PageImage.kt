package com.tali.notes.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.pow

/** How a page looks once taken. */
enum class Look { ORIGINAL, DOCUMENT, GREY, BLACK_WHITE }

/**
 * What the camera screen does to a picture: turn it, straighten the page out of it (the four corners
 * pulled to a flat rectangle), and the looks: [Look.DOCUMENT] whitens the paper and its shadows while
 * keeping the ink's colour, [Look.GREY] the same in grey, [Look.BLACK_WHITE] crisp black on white.
 */
object PageImage {
  fun turned(src: Bitmap, quarters: Int): Bitmap {
    val q = ((quarters % 4) + 4) % 4
    if (q == 0) return src
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f * q) }, true)
  }

  fun scaledTo(src: Bitmap, maxSide: Int): Bitmap {
    val s = maxSide.toFloat() / maxOf(src.width, src.height)
    if (s >= 1f) return src
    return Bitmap.createScaledBitmap(src, maxOf(1, (src.width * s).toInt()), maxOf(1, (src.height * s).toInt()), true)
  }

  /** The page inside [quad] pulled out flat, its sides as long as the longer of each pair (at most [maxSide]). */
  fun straightened(src: Bitmap, quad: Quad, maxSide: Int): Bitmap {
    val w = src.width.toFloat()
    val h = src.height.toFloat()
    val c = quad.points.map { P(it.x * w, it.y * h) }
    var outW = maxOf(distance(c[0], c[1]), distance(c[3], c[2]))
    var outH = maxOf(distance(c[0], c[3]), distance(c[1], c[2]))
    val s = minOf(1f, maxSide / maxOf(outW, outH))
    outW = maxOf(8f, outW * s)
    outH = maxOf(8f, outH * s)
    val m = Matrix()
    m.setPolyToPoly(floatArrayOf(c[0].x, c[0].y, c[1].x, c[1].y, c[2].x, c[2].y, c[3].x, c[3].y), 0, floatArrayOf(0f, 0f, outW, 0f, outW, outH, 0f, outH), 0, 4)
    val out = Bitmap.createBitmap(outW.toInt(), outH.toInt(), Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    return out
  }

  fun looked(src: Bitmap, look: Look): Bitmap {
    if (look == Look.ORIGINAL) return src
    val w = src.width
    val h = src.height
    val px = IntArray(w * h)
    src.getPixels(px, 0, w, 0, 0, w, h)
    val bg = paper(px, w, h)
    for (i in px.indices) {
      val p = px[i]
      val r = (p shr 16) and 0xff
      val g = (p shr 8) and 0xff
      val b = p and 0xff
      val k = 255f / maxOf(bg[i], 24)
      px[i] =
        when (look) {
          Look.DOCUMENT -> rgb(curve(r * k / 255f), curve(g * k / 255f), curve(b * k / 255f))
          Look.GREY -> curve(lum(p) * k / 255f).let { rgb(it, it, it) }
          else -> {
            val v = ((lum(p) * k / 255f - 0.6f) / 0.16f).coerceIn(0f, 1f)
            rgb(v, v, v)
          }
        }
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(px, 0, w, 0, 0, w, h)
    return out
  }

  /**
   * The paper's own brightness behind each pixel: the brightest of each small cell (the writing is
   * darker than the paper, so the cell's brightest is paper), smoothed, then spread back over the
   * page. Dividing by it evens out shadows and a yellow lamp.
   */
  private fun paper(px: IntArray, w: Int, h: Int): IntArray {
    val cell = maxOf(4, maxOf(w, h) / 48)
    val gw = (w + cell - 1) / cell
    val gh = (h + cell - 1) / cell
    var grid = IntArray(gw * gh)
    for (y in 0 until h) {
      val gy = y / cell
      for (x in 0 until w) {
        val i = gy * gw + x / cell
        val v = lum(px[y * w + x])
        if (v > grid[i]) grid[i] = v
      }
    }
    repeat(2) { grid = blur(grid, gw, gh) }
    val out = IntArray(w * h)
    for (y in 0 until h) {
      val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, gh - 1f)
      val y0 = fy.toInt()
      val y1 = minOf(y0 + 1, gh - 1)
      val ty = fy - y0
      for (x in 0 until w) {
        val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, gw - 1f)
        val x0 = fx.toInt()
        val x1 = minOf(x0 + 1, gw - 1)
        val tx = fx - x0
        val top = grid[y0 * gw + x0] * (1 - tx) + grid[y0 * gw + x1] * tx
        val bottom = grid[y1 * gw + x0] * (1 - tx) + grid[y1 * gw + x1] * tx
        out[y * w + x] = (top * (1 - ty) + bottom * ty).toInt()
      }
    }
    return out
  }

  /** Paper to white, ink a little deeper. */
  private fun curve(v: Float): Float = ((v - 0.18f) / 0.72f).coerceIn(0f, 1f).pow(1.25f)

  private fun lum(p: Int): Int = (((p shr 16) and 0xff) * 77 + ((p shr 8) and 0xff) * 150 + (p and 0xff) * 29) shr 8

  private fun rgb(r: Float, g: Float, b: Float): Int =
    (0xff shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

  private fun distance(a: P, b: P) = kotlin.math.hypot(a.x - b.x, a.y - b.y)

  /** A 3×3 box blur. */
  private fun blur(src: IntArray, w: Int, h: Int): IntArray {
    val tmp = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      val r = y * w
      tmp[r + x] = (src[r + maxOf(x - 1, 0)] + src[r + x] + src[r + minOf(x + 1, w - 1)]) / 3
    }
    val out = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      out[y * w + x] = (tmp[maxOf(y - 1, 0) * w + x] + tmp[y * w + x] + tmp[minOf(y + 1, h - 1) * w + x]) / 3
    }
    return out
  }
}
