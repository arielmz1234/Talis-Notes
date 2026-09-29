package com.tali.notes.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

/** Where a cloud surface grows its little crown of puffs above the top edge. */
enum class CloudCrown {
  NONE,

  /** Many small, uneven bumps all along the top edge with a gentle crest near the start, and soft puffs at both ends. */
  EDGE,

  /** Like [EDGE] but lower and without the crest or end puffs, for small bars. */
  SOFT_EDGE,

  /** A small crown in the middle, like a tiny cloud. */
  CENTER,
}

private class SurfaceTones(val top: Int, val belly: Int, val border: Int, val shadow: Int)

/** A stable pseudo-random number in [0, 1) so the bumps look hand-placed but never change. */
private fun hash(i: Int, salt: Int): Float {
  val x = sin(i * 127.1 + salt * 311.7) * 43758.5453
  return (x - floor(x)).toFloat()
}

/** Bumps along the top edge as (x, y, r) in dp: uneven sizes and spacing, each rising only a little above the edge. */
private fun edgeBumps(w: Float, radius: Float, soft: Boolean): List<Triple<Float, Float, Float>> {
  val out = ArrayList<Triple<Float, Float, Float>>()
  var x = radius * 0.55f
  var i = 0
  while (x < w - radius * 0.55f) {
    val r = if (soft) 9f + hash(i, 1) * 6f else 10f + hash(i, 1) * 9f
    // A gentle crest over the first third makes the bar read as one cloud rather than a trim.
    val crest = if (soft) 0f else maxOf(0f, 1f - abs(x / w - 0.3f) / 0.24f) * 5f
    val rise = if (soft) 2.5f + hash(i, 2) * 3f else 4f + hash(i, 2) * 6f + crest
    out += Triple(x, r - rise, r)
    x += r * (1.15f + hash(i, 3) * 0.35f)
    i++
  }
  return out
}

/** Crown puffs as (x, y, r) in dp, x from the centre, y from the top edge. */
private val CENTER_PUFFS = listOf(Triple(-20f, 6f, 11f), Triple(0f, 0f, 16f), Triple(20f, 7f, 10f))

/**
 * Draws a soft cloud-like surface behind the content: white fading to a pale blue-lilac belly,
 * a hairline edge, a gentle lilac shadow floating beneath, and optionally a crown of puffs rising
 * above the top edge. Everything is painted once per size into a bitmap.
 */
@Composable
fun Modifier.cloudSurface(radius: Dp = 22.dp, crown: CloudCrown = CloudCrown.NONE, shadowDepth: Dp = 7.dp): Modifier {
  val c = Tali.colors
  val tones =
    if (c.isDark) SurfaceTones(0xFF3A335E.toInt(), 0xFF2B2548.toInt(), 0xFF514980.toInt(), 0x38000000)
    else SurfaceTones(0xFFFFFFFF.toInt(), 0xFFE3E8FC.toInt(), 0xFFCDD4F5.toInt(), 0x3D6256C8)
  return drawWithCache {
    val d = density
    val w = size.width
    val h = size.height
    val r = radius.toPx().coerceAtMost(h / 2)
    val outline = Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) }
    var crownTop = 0f
    var sideOut = 0f
    fun puff(x: Float, y: Float, rad: Float) {
      val cx = x * d
      val cy = y * d
      val pr = rad * d
      crownTop = minOf(crownTop, cy - pr)
      sideOut = maxOf(sideOut, -(cx - pr), cx + pr - w)
      outline.op(Path().apply { addCircle(cx, cy, pr, Path.Direction.CW) }, Path.Op.UNION)
    }
    when (crown) {
      CloudCrown.NONE -> Unit
      CloudCrown.EDGE, CloudCrown.SOFT_EDGE -> {
        val soft = crown == CloudCrown.SOFT_EDGE
        edgeBumps(w / d, r / d, soft).forEach { (x, y, rad) -> puff(x, y, rad) }
        if (!soft) {
          puff(4f, h / d * 0.62f, 14f)
          puff(w / d - 4f, h / d * 0.58f, 13f)
        }
      }
      CloudCrown.CENTER -> CENTER_PUFFS.forEach { (x, y, rad) -> puff(w / 2 / d + x, y, rad) }
    }
    val blur = 11 * d
    val margin = blur * 2 + shadowDepth.toPx()
    val left = -margin - sideOut
    val top = crownTop - margin
    val bmp = Bitmap.createBitmap((w - left * 2).toInt().coerceAtLeast(1), (h - top + margin).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    canvas.translate(-left, -top)
    canvas.save()
    canvas.translate(0f, shadowDepth.toPx())
    canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tones.shadow
      maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
    })
    canvas.restore()
    canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(0f, crownTop, 0f, h, intArrayOf(tones.top, tones.top, tones.belly), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    })
    canvas.drawPath(outline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = d
      color = tones.border
    })
    val image = bmp.asImageBitmap()
    onDrawBehind { drawImage(image, Offset(left, top)) }
  }
}

