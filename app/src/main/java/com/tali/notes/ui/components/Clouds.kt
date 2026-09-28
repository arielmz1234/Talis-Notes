package com.tali.notes.ui.components

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali

/**
 * A cartoon cloud recipe in unit coordinates (x across the width, y down the height): the puffs
 * that make its top, a flat base, and an optional tapering wisp trailing off one side.
 */
private class CloudRecipe(
  val puffs: List<Triple<Float, Float, Float>>,
  val baseLeft: Float,
  val baseRight: Float,
  /** -1 wisp trails left, 1 trails right, 0 none. */
  val wisp: Int,
  /** Little loose bubbles around the edge: (x, y, r). */
  val bubbles: List<Triple<Float, Float, Float>>,
)

private val RECIPES =
  listOf(
    // Big cumulus, wisp to the left.
    CloudRecipe(
      puffs = listOf(Triple(0.3f, 0.6f, 0.14f), Triple(0.43f, 0.4f, 0.19f), Triple(0.6f, 0.3f, 0.22f), Triple(0.76f, 0.46f, 0.17f), Triple(0.87f, 0.62f, 0.11f)),
      baseLeft = 0.22f,
      baseRight = 0.96f,
      wisp = -1,
      bubbles = listOf(Triple(0.97f, 0.4f, 0.024f), Triple(0.93f, 0.3f, 0.014f), Triple(0.2f, 0.42f, 0.016f)),
    ),
    // Low wide cloud, wisp to the right.
    CloudRecipe(
      puffs = listOf(Triple(0.13f, 0.62f, 0.11f), Triple(0.27f, 0.46f, 0.16f), Triple(0.44f, 0.38f, 0.19f), Triple(0.6f, 0.54f, 0.13f)),
      baseLeft = 0.05f,
      baseRight = 0.68f,
      wisp = 1,
      bubbles = listOf(Triple(0.06f, 0.42f, 0.02f), Triple(0.62f, 0.3f, 0.014f)),
    ),
    // Small puffy cloud.
    CloudRecipe(
      puffs = listOf(Triple(0.22f, 0.6f, 0.17f), Triple(0.42f, 0.38f, 0.25f), Triple(0.66f, 0.44f, 0.22f), Triple(0.84f, 0.62f, 0.13f)),
      baseLeft = 0.1f,
      baseRight = 0.94f,
      wisp = 0,
      bubbles = listOf(Triple(0.07f, 0.48f, 0.03f), Triple(0.95f, 0.42f, 0.022f)),
    ),
  )

private const val BASE_Y = 0.82f

private fun puffsOnly(w: Float, h: Float, r: CloudRecipe, puffShift: Float, puffScale: Float): Path {
  var path = Path()
  for ((x, y, rad) in r.puffs) {
    val puff = Path().apply { addOval(Rect(Offset(x * w, y * h + puffShift), rad * w * puffScale)) }
    path = Path.combine(PathOperation.Union, path, puff)
  }
  return path
}

private fun cloudBody(w: Float, h: Float, r: CloudRecipe, puffShift: Float = 0f, puffScale: Float = 1f): Path {
  var path = Path()
  val baseTop = h * 0.6f
  path.addRoundRect(
    androidx.compose.ui.geometry.RoundRect(Rect(w * r.baseLeft, baseTop, w * r.baseRight, h * BASE_Y), androidx.compose.ui.geometry.CornerRadius(h * 0.2f))
  )
  for ((x, y, rad) in r.puffs) {
    val puff = Path().apply { addOval(Rect(Offset(x * w, y * h + puffShift), rad * w * puffScale)) }
    path = Path.combine(PathOperation.Union, path, puff)
  }
  if (r.wisp != 0) {
    // A long, tapering tail along the base.
    val wisp = Path()
    val baseY = h * BASE_Y
    if (r.wisp < 0) {
      val start = w * r.baseLeft + h * 0.1f
      wisp.moveTo(start, baseY - h * 0.2f)
      wisp.cubicTo(start - w * 0.1f, baseY - h * 0.18f, w * 0.04f, baseY - h * 0.1f, 0f, baseY - h * 0.08f)
      wisp.cubicTo(w * 0.05f, baseY, start - w * 0.05f, baseY, start, baseY)
      wisp.close()
    } else {
      val start = w * r.baseRight - h * 0.1f
      wisp.moveTo(start, baseY - h * 0.22f)
      wisp.cubicTo(start + w * 0.12f, baseY - h * 0.2f, w * 0.94f, baseY - h * 0.12f, w, baseY - h * 0.09f)
      wisp.cubicTo(w * 0.95f, baseY, start + w * 0.06f, baseY, start, baseY)
      wisp.close()
    }
    path = Path.combine(PathOperation.Union, path, wisp)
  }
  return path
}

/**
 * One cartoon cloud: white on top, a pale blue-lilac shaded belly whose edge follows the puffs,
 * a soft flat shadow underneath, and a few loose bubbles.
 */
@Composable
fun Cloud(modifier: Modifier = Modifier, width: Dp = 160.dp, variant: Int = 0, alpha: Float = 1f) {
  val c = Tali.colors
  val white = if (c.isDark) Color(0xFFE9E6FA) else Color.White
  val shade = if (c.isDark) Color(0xFFA8B2E6) else Color(0xFFCFDBFA)
  val shadeDeep = if (c.isDark) Color(0xFF8F8FD6) else Color(0xFFBDBEF3)
  val shadow = if (c.isDark) Color(0x33000000) else Color(0x267A6FD6)
  Spacer(
    modifier.size(width, width * 0.5f).drawWithCache {
      val recipe = RECIPES[variant % RECIPES.size]
      val body = cloudBody(size.width, size.height, recipe)
      // The white highlight: the same puffs lifted and slightly smaller, clipped to the cloud.
      // Only the puffs are lit; the flat base and the wisp stay in the shaded belly.
      val highlight = puffsOnly(size.width, size.height, recipe, puffShift = -size.height * 0.12f, puffScale = 0.94f)
      onDrawBehind { drawCloud(body, highlight, recipe, white, shade, shadeDeep, shadow, alpha) }
    }
  )
}

private fun DrawScope.drawCloud(body: Path, highlight: Path, recipe: CloudRecipe, white: Color, shade: Color, shadeDeep: Color, shadow: Color, alpha: Float) {
  translate(top = size.height * 0.07f) { drawPath(body, shadow, alpha = alpha) }
  drawPath(body, Brush.verticalGradient(listOf(shade, shadeDeep), startY = size.height * 0.4f, endY = size.height), alpha = alpha)
  clipPath(body) {
    drawPath(highlight, white, alpha = alpha)
    // Little white curls poking down into the shaded belly, like the puffs underneath.
    for ((i, puff) in recipe.puffs.withIndex()) {
      if (i % 2 == 1) continue
      val (x, y, r) = puff
      drawCircle(white, r * size.width * 0.36f, Offset(x * size.width + r * size.width * 0.4f, y * size.height + r * size.width * 0.35f), alpha = alpha)
    }
  }
  for ((x, y, r) in recipe.bubbles) {
    drawCircle(white, r * size.width, Offset(x * size.width, y * size.height), alpha = alpha)
    drawCircle(shade, r * size.width * 0.55f, Offset(x * size.width + r * size.width * 0.25f, y * size.height + r * size.width * 0.3f), alpha = alpha)
  }
}

/** A cloud that drifts slowly sideways on its own GPU layer (no re-layout, no re-draw). */
@Composable
fun DriftingCloud(modifier: Modifier = Modifier, width: Dp, variant: Int = 0, driftDp: Float = 10f, periodMs: Int = 9000, alpha: Float = 1f) {
  val transition = rememberInfiniteTransition(label = "cloud")
  val x by transition.animateFloat(-1f, 1f, InfiniteRepeatableSpec(tween(periodMs, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
  Cloud(modifier.graphicsLayer { translationX = x * driftDp * density }, width = width, variant = variant, alpha = alpha)
}

/** The sky that sits behind the top of a screen: blue into lilac, fading into the page below. */
@Composable
fun SkyBand(modifier: Modifier = Modifier, height: Dp = 320.dp, fadeTo: Color = Tali.colors.background) {
  val c = Tali.colors
  val left = if (c.isDark) Color(0xFF1D2A52) else Color(0xFFBFD4FF)
  val right = if (c.isDark) Color(0xFF30245A) else Color(0xFFDCCBFF)
  Canvas(modifier.fillMaxWidth().height(height)) {
    drawRect(Brush.linearGradient(listOf(left, right), start = Offset.Zero, end = Offset(size.width, size.height * 0.6f)))
    drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to fadeTo))
  }
}

/** Clouds scattered across a sky band. [layout] picks one of a few arrangements. */
@Composable
fun BoxScope.SkyClouds() {
  DriftingCloud(Modifier.align(Alignment.TopStart).offset(x = (-24).dp, y = 18.dp), width = 190.dp, variant = 2, driftDp = 8f, periodMs = 14000)
  DriftingCloud(Modifier.align(Alignment.TopStart).offset(x = 300.dp, y = 8.dp), width = 230.dp, variant = 1, driftDp = 12f, periodMs = 17000)
  DriftingCloud(Modifier.align(Alignment.TopCenter).offset(x = 170.dp, y = 2.dp), width = 260.dp, variant = 0, driftDp = 14f, periodMs = 20000)
  DriftingCloud(Modifier.align(Alignment.TopEnd).offset(x = (-20).dp, y = 26.dp), width = 170.dp, variant = 2, driftDp = 8f, periodMs = 12000)
  Sparkle(Modifier.align(Alignment.TopStart).offset(x = 230.dp, y = 40.dp), size = 11.dp, color = Color.White)
  Sparkle(Modifier.align(Alignment.TopCenter).offset(x = (-60).dp, y = 26.dp), size = 13.dp, color = Color.White)
  Sparkle(Modifier.align(Alignment.TopEnd).offset(x = (-230).dp, y = 76.dp), size = 9.dp, color = Color.White.copy(alpha = 0.85f))
}

/** A four-point sparkle. */
@Composable
fun Sparkle(modifier: Modifier = Modifier, size: Dp = 10.dp, color: Color) {
  Canvas(modifier.size(size)) {
    val w = this.size.width
    val cx = w / 2
    val p = Path().apply {
      moveTo(cx, 0f)
      quadraticTo(cx, cx, w, cx)
      quadraticTo(cx, cx, cx, w)
      quadraticTo(cx, cx, 0f, cx)
      quadraticTo(cx, cx, cx, 0f)
      close()
    }
    drawPath(p, color)
  }
}
