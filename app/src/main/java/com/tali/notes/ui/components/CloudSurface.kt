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

/** Where a cloud surface grows its little crown of puffs above the top edge. */
enum class CloudCrown {
  NONE,

  /** A cumulus crown near the start, and one small puff near the end. */
  START,

  /** A small crown in the middle, like a tiny cloud. */
  CENTER,
}

private class SurfaceTones(val top: Int, val belly: Int, val border: Int, val shadow: Int)

/** Crown puffs as (x, y, r) in dp. x is from the start (or from the centre for [CloudCrown.CENTER]); y is from the top edge. */
private val START_PUFFS = listOf(Triple(18f, 8f, 17f), Triple(45f, 0f, 26f), Triple(74f, 9f, 17f))
private val END_PUFFS = listOf(Triple(-26f, 10f, 13f), Triple(-8f, 14f, 10f))
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
    if (c.isDark) SurfaceTones(0xFF2A2440.toInt(), 0xFF231F3A.toInt(), 0xFF3A3358.toInt(), 0x66000000)
    else SurfaceTones(0xFFFFFFFF.toInt(), 0xFFE3E8FC.toInt(), 0xFFCDD4F5.toInt(), 0x3D6256C8)
  return drawWithCache {
    val d = density
    val w = size.width
    val h = size.height
    val r = radius.toPx().coerceAtMost(h / 2)
    val outline = Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) }
    var crownTop = 0f
    fun puff(x: Float, y: Float, rad: Float) {
      val cx = x * d
      val cy = y * d
      val pr = rad * d
      crownTop = minOf(crownTop, cy - pr)
      outline.op(Path().apply { addCircle(cx, cy, pr, Path.Direction.CW) }, Path.Op.UNION)
    }
    when (crown) {
      CloudCrown.NONE -> Unit
      CloudCrown.START -> {
        if (w > 200 * d) START_PUFFS.forEach { (x, y, rad) -> puff(r / d + x, y, rad) }
        if (w > 320 * d) END_PUFFS.forEach { (x, y, rad) -> puff((w - r) / d + x, y, rad) }
      }
      CloudCrown.CENTER -> CENTER_PUFFS.forEach { (x, y, rad) -> puff(w / 2 / d + x, y, rad) }
    }
    val blur = 11 * d
    val margin = blur * 2 + shadowDepth.toPx()
    val left = -margin
    val top = crownTop - margin
    val bmp = Bitmap.createBitmap((w + margin * 2).toInt().coerceAtLeast(1), (h - top + margin).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
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

