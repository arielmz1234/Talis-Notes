package com.tali.notes.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * Things come and go on a little cloud. The element turns into a soft cloud the size of itself,
 * which drifts up and evaporates; one that arrives forms out of the same cloud as it clears.
 *
 * Motion notes (the "why" behind the numbers):
 * - Exits are quick: the element is gone in about 200 ms and the cloud by about 420 ms, so nothing
 *   lingers over empty space. Arrivals take a touch longer (the cloud clears around the card).
 * - Strong ease-out everywhere: movement starts at once and settles softly.
 * - Nothing starts tiny: an arriving card grows from 96%, not from a dot, and never bounces.
 * - The cloud is soft-edged lobes on one layer, so it reads as one cloud, never as separate circles,
 *   and it blurs as it evaporates (from Android 12; older ones just fade).
 */

/** Strong ease-out: moves straight away, then settles. */
internal val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOutSoft = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** How long an element takes to go, and how long its cloud lasts. Callers wait [PUFF_LEAVE_MS] before removing it. */
const val ELEMENT_LEAVE_MS = 200
const val PUFF_LEAVE_MS = 420
private const val ELEMENT_ARRIVE_MS = 260
private const val PUFF_ARRIVE_MS = 460

/**
 * A puff of cloud over this area at [progress] 0..1 (nothing at 0 or 1). The cloud may spill a
 * little past the area, as clouds do.
 */
@Composable
fun CloudPuff(progress: Float, modifier: Modifier = Modifier) {
  if (progress <= 0f || progress >= 1f) return
  val c = Tali.colors
  // The same whites and lilac bellies as the clouds on the toolbars, a touch lighter in the dark.
  // In the dark the cloud is one tone: a darker underside shows between the lobes as circles.
  val top = if (c.isDark) lerp(c.cloudTop, DUSK_LILAC, 0.42f) else c.cloudTop
  val belly = if (c.isDark) null else lerp(c.cloudBelly, c.accentSoft, 0.5f)
  val sprite = remember { softLobe() }
  val t = progress
  // In fast, out softly: fully there by 16%, then evaporating from about a third of the way.
  val appear = EaseOutStrong.transform((t / 0.16f).coerceAtMost(1f))
  val vanish = ((t - 0.32f) / 0.68f).coerceIn(0f, 1f)
  // Evaporating is the one place for an ease-in-out: it thins evenly instead of blinking out.
  val alpha = appear * (1f - EaseInOutSoft.transform(vanish))
  val drift = EaseOutStrong.transform(t)
  Canvas(
    modifier
      .outset(OVERFLOW)
      .graphicsLayer {
        this.alpha = alpha
        // Drawn on its own layer so overlapping lobes make one cloud instead of darker overlaps.
        compositingStrategy = CompositingStrategy.Offscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vanish > 0f) {
          val r = 10.dp.toPx() * vanish
          renderEffect = BlurEffect(r, r, TileMode.Decal)
        }
      }
  ) {
    val m = OVERFLOW.toPx()
    val w = size.width - 2 * m
    val h = size.height - 2 * m
    if (w <= 0f || h <= 0f) return@Canvas
    val lobes = cloudLobes(w, h)
    val cx = m + w / 2
    // The cloud rises by about a third of its height and spreads a little as it thins out.
    val rise = -h * 0.35f * drift
    val spread = 1f + 0.10f * drift
    val swell = 0.86f + 0.14f * EaseOutStrong.transform((t / 0.35f).coerceAtMost(1f))
    val shrink = 1f - 0.28f * vanish * vanish
    val bellyFilter = belly?.let { ColorFilter.tint(it) }
    val topFilter = ColorFilter.tint(top)
    for (pass in (if (bellyFilter != null) 0 else 1)..1) {
      for (l in lobes) {
        val r = l.r * swell * shrink
        val x = cx + (l.x - w / 2) * spread
        val y = m + l.y + rise + if (pass == 0) r * 0.18f else 0f
        val d = (2 * r).roundToInt().coerceAtLeast(1)
        drawImage(
          sprite,
          srcOffset = IntOffset.Zero,
          srcSize = IntSize(sprite.width, sprite.height),
          dstOffset = IntOffset((x - r).roundToInt(), (y - r).roundToInt()),
          dstSize = IntSize(d, d),
          colorFilter = if (pass == 0) bellyFilter else topFilter,
        )
      }
    }
  }
}

/**
 * Wraps a card or row so it can come and go on a cloud. [leaving] dissolves it into a puff (the
 * caller removes it [PUFF_LEAVE_MS] later); [appearing] lets it form out of one.
 */
@Composable
fun PuffBox(leaving: Boolean, appearing: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val leave by animateFloatAsState(if (leaving) 1f else 0f, tween(if (leaving) ELEMENT_LEAVE_MS else 0, easing = EaseOutStrong), label = "leave")
  val arrive = remember { Animatable(if (appearing) 0f else 1f) }
  val puff = remember { Animatable(1f) }
  LaunchedEffect(appearing) {
    if (!appearing) return@LaunchedEffect
    arrive.snapTo(0f)
    puff.snapTo(0f)
    coroutineScope {
      launch { puff.animateTo(1f, tween(PUFF_ARRIVE_MS)) }
      // The cloud gathers first; the card forms inside it as it clears.
      arrive.animateTo(1f, tween(ELEMENT_ARRIVE_MS, delayMillis = 60, easing = EaseOutStrong))
    }
  }
  LaunchedEffect(leaving) {
    if (!leaving) return@LaunchedEffect
    puff.snapTo(0f)
    puff.animateTo(1f, tween(PUFF_LEAVE_MS))
  }
  Box(modifier) {
    Box(
      Modifier.graphicsLayer {
        // Out: shrink a hair, fade and blur into the cloud. In: the same, backwards.
        val gone = maxOf(leave, 1f - arrive.value)
        val s = 1f - 0.04f * gone
        scaleX = s
        scaleY = s
        alpha = 1f - gone
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && gone > 0.01f) {
          val r = 6.dp.toPx() * gone
          renderEffect = BlurEffect(r, r, TileMode.Decal)
        }
      }
    ) {
      content()
    }
    CloudPuff(puff.value, Modifier.matchParentSize())
  }
}

/** A pale lilac the dark-mode cloud leans towards, so it isn't a flat grey. */
private val DUSK_LILAC = Color(0xFFB7A6EE)

/** Room around the area for lobes to spill into, rise and blur. */
private val OVERFLOW: Dp = 44.dp

/** Lays this out [by] bigger on every side, centred on where it would have been. */
private fun Modifier.outset(by: Dp): Modifier = layout { measurable, constraints ->
  val m = by.roundToPx()
  val p = measurable.measure(
    constraints.copy(
      minWidth = constraints.minWidth + 2 * m,
      maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * m else constraints.maxWidth,
      minHeight = constraints.minHeight + 2 * m,
      maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * m else constraints.maxHeight,
    )
  )
  layout(p.width - 2 * m, p.height - 2 * m) { p.place(-m, -m) }
}

private class Lobe(val x: Float, val y: Float, val r: Float)

/** A stable pseudo-random number in [0, 1), so the cloud looks hand-made but is the same each time. */
private fun hash(i: Int, salt: Int): Float {
  val x = sin(i * 127.1 + salt * 311.7) * 43758.5453
  return (x - floor(x)).toFloat()
}

/**
 * A cloud shaped to a [w] by [h] area: a flat-bottomed row of lobes across it, rising into a
 * crown in the middle, so a wide toolbar gets a long cloud and a tile a round one.
 */
private fun cloudLobes(w: Float, h: Float): List<Lobe> {
  val n = (w / (h * 0.55f)).roundToInt().coerceIn(3, 16)
  val out = ArrayList<Lobe>(n * 2)
  for (i in 0 until n) {
    val u = (i + 0.5f) / n
    val arch = sin(PI * u).toFloat()
    val r = h * (0.36f + 0.2f * arch) * (0.88f + 0.24f * hash(i, 1))
    val x = w * (0.06f + 0.88f * u) + (hash(i, 2) - 0.5f) * h * 0.12f
    out += Lobe(x, h * 0.62f - r * 0.35f, r)
    // A crown of smaller lobes over the middle.
    if (i % 2 == 1 || n <= 4) {
      val cr = r * (0.7f + 0.2f * hash(i, 3))
      out += Lobe(x + (hash(i, 4) - 0.5f) * r * 0.5f, h * 0.62f - r * 0.35f - r * (0.5f + 0.4f * arch), cr)
    }
  }
  return out
}

/** A round lobe: solid in the middle, feathering to nothing at the edge, so no lobe shows a rim. */
private fun softLobe(): ImageBitmap {
  val size = 96
  val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
  val half = size / 2f
  val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = RadialGradient(
      half, half, half,
      // Solid to 70% so neighbouring lobes overlap solid-on-solid (no see-through seams), then a short feather.
      intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0x8CFFFFFF.toInt(), 0x00FFFFFF),
      floatArrayOf(0f, 0.7f, 0.88f, 1f),
      Shader.TileMode.CLAMP,
    )
  }
  android.graphics.Canvas(bmp).drawCircle(half, half, half, paint)
  return bmp.asImageBitmap()
}
