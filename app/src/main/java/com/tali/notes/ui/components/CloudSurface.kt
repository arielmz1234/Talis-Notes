package com.tali.notes.ui.components

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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

  /** A low, narrow crown for a single button: a hint of cloud that stays within the button's width. */
  LITTLE,
}

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

/** [CloudCrown.LITTLE] puffs: rise about 6 dp instead of 16 and span about 37 dp instead of 62. */
private val LITTLE_PUFFS = listOf(Triple(-11f, 7f, 7.5f), Triple(0f, 4f, 10f), Triple(11f, 7.5f, 7f))

/**
 * Draws a soft cloud-like surface behind the content: white fading to a pale blue-lilac belly,
 * a hairline edge, a gentle lilac shadow floating beneath, and optionally a crown of puffs rising
 * above the top edge. Cheap to redraw at a new size, so it can animate.
 */
@Composable
fun Modifier.cloudSurface(radius: Dp = 22.dp, crown: CloudCrown = CloudCrown.NONE, shadowDepth: Dp = 7.dp): Modifier {
  val c = Tali.colors
  val toneTop = c.cloudTop.toArgb()
  val toneBelly = c.cloudBelly.toArgb()
  val edge = c.cloudEdge.toArgb()
  val shadow = c.cloudShadow.toArgb()
  return drawWithCache {
    val d = density
    val w = size.width
    val h = size.height
    val r = radius.toPx().coerceAtMost(h / 2)
    val outline = Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) }
    var crownTop = 0f
    // All the puffs go into one path and join the outline in a single union: one union per puff
    // took about three times as long, too slow to redo every frame while a bar changes width.
    val puffs = Path()
    fun puff(x: Float, y: Float, rad: Float) {
      val cx = x * d
      val cy = y * d
      val pr = rad * d
      crownTop = minOf(crownTop, cy - pr)
      puffs.addCircle(cx, cy, pr, Path.Direction.CW)
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
      CloudCrown.LITTLE -> LITTLE_PUFFS.forEach { (x, y, rad) -> puff(w / 2 / d + x, y, rad) }
    }
    if (!puffs.isEmpty) outline.op(puffs, Path.Op.UNION)
    // Drawn straight onto the (hardware) canvas, with the shadow as a GPU shadow layer. It used to
    // be painted into a full-size bitmap with a software blur, which was redone at every new size:
    // a card or bar changing size (a section folding away, the toolbar easing wider) rebuilt a
    // multi-megabyte blurred bitmap every frame and stuttered.
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(0f, crownTop, 0f, h, intArrayOf(toneTop, toneTop, toneBelly), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
      setShadowLayer(11 * d, 0f, shadowDepth.toPx(), shadow)
    }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = d
      color = edge
    }
    onDrawBehind {
      drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.drawPath(outline, fill)
        nc.drawPath(outline, stroke)
      }
    }
  }
}

