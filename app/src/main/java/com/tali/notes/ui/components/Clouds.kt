package com.tali.notes.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali

/**
 * A cartoon cloud recipe: puffs as (x, y, r) in units of the cloud's width. The first [crown] puffs
 * make the sunlit top; the rest are the smaller scallops along the shaded base.
 */
private class CloudRecipe(val crown: Int, val puffs: List<Triple<Float, Float, Float>>)

private fun p(x: Float, y: Float, r: Float) = Triple(x, y, r)

private val RECIPES =
  listOf(
    // Big cumulus.
    CloudRecipe(
      5,
      listOf(
        p(0.30f, 0.26f, 0.125f), p(0.49f, 0.17f, 0.17f), p(0.69f, 0.24f, 0.14f), p(0.85f, 0.33f, 0.095f), p(0.16f, 0.34f, 0.09f),
        p(0.22f, 0.405f, 0.075f), p(0.40f, 0.40f, 0.09f), p(0.58f, 0.405f, 0.09f), p(0.76f, 0.405f, 0.075f), p(0.11f, 0.41f, 0.055f), p(0.89f, 0.415f, 0.05f),
      ),
    ),
    // Long, low cloud.
    CloudRecipe(
      5,
      listOf(
        p(0.25f, 0.30f, 0.10f), p(0.42f, 0.235f, 0.14f), p(0.61f, 0.27f, 0.12f), p(0.78f, 0.33f, 0.085f), p(0.11f, 0.36f, 0.07f),
        p(0.20f, 0.41f, 0.06f), p(0.36f, 0.405f, 0.08f), p(0.53f, 0.41f, 0.08f), p(0.70f, 0.41f, 0.07f), p(0.86f, 0.415f, 0.05f), p(0.07f, 0.42f, 0.04f),
      ),
    ),
    // Small, round puff.
    CloudRecipe(
      4,
      listOf(
        p(0.36f, 0.22f, 0.17f), p(0.62f, 0.24f, 0.16f), p(0.20f, 0.34f, 0.11f), p(0.82f, 0.35f, 0.10f),
        p(0.28f, 0.405f, 0.09f), p(0.50f, 0.40f, 0.10f), p(0.70f, 0.405f, 0.09f), p(0.13f, 0.41f, 0.06f), p(0.88f, 0.415f, 0.055f),
      ),
    ),
  )

/** Cloud colours: the lit white, the shaded underside and its deepest tone, and the floating shadow. */
private class CloudTones(val light: Int, val shade: Int, val deep: Int, val shadow: Int)

/**
 * Paints one cloud into a bitmap. The body is filled in the shade colour, then the puffs are lit
 * from above with a blurred white copy lifted up and in, so the white melts softly into the
 * blue-lilac underside; the crown puffs get a brighter core. A soft oval shadow floats below.
 */
private fun paintCloud(recipe: CloudRecipe, w: Float, tones: CloudTones): Bitmap {
  val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), (w * CLOUD_BITMAP_ASPECT).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(bmp)
  val shadow =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tones.shadow
      maskFilter = BlurMaskFilter(w * 0.03f, BlurMaskFilter.Blur.NORMAL)
    }
  canvas.drawOval(RectF(w * 0.12f, w * 0.465f, w * 0.88f, w * 0.535f), shadow)

  val body = android.graphics.Path()
  for ((x, y, r) in recipe.puffs) body.addCircle(x * w, y * w, r * w, android.graphics.Path.Direction.CW)
  // Lit white across the crown's upper edge, so the silhouette stays crisp against the sky.
  val fill =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(0f, 0f, 0f, w * 0.49f, intArrayOf(tones.light, tones.light, tones.shade, tones.deep), floatArrayOf(0f, 0.3f, 0.62f, 1f), Shader.TileMode.CLAMP)
    }
  canvas.drawPath(body, fill)

  canvas.save()
  canvas.clipPath(body)
  val light =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tones.light
      maskFilter = BlurMaskFilter(w * 0.022f, BlurMaskFilter.Blur.NORMAL)
    }
  for ((i, puff) in recipe.puffs.withIndex()) {
    val (x, y, r) = puff
    light.alpha = if (i < recipe.crown) 255 else 150
    canvas.drawCircle((x - r * 0.12f) * w, (y - r * 0.34f) * w, r * w * 0.86f, light)
  }
  val core =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tones.light
      alpha = 242
      maskFilter = BlurMaskFilter(w * 0.01f, BlurMaskFilter.Blur.NORMAL)
    }
  for (i in 0 until recipe.crown) {
    val (x, y, r) = recipe.puffs[i]
    canvas.drawCircle((x - r * 0.2f) * w, (y - r * 0.42f) * w, r * w * 0.6f, core)
  }
  canvas.restore()
  return bmp
}

/** The cloud bitmap is a little taller than the layout box so the floating shadow has room. */
private const val CLOUD_BITMAP_ASPECT = 0.56f

/**
 * One fluffy cartoon cloud: white, sunlit puffs on top, a soft blue-lilac underside with little
 * scallops, and a gentle shadow floating beneath. It is painted once into a bitmap, so drifting
 * and scrolling cost nothing.
 */
@Composable
fun Cloud(modifier: Modifier = Modifier, width: Dp = 160.dp, variant: Int = 0, alpha: Float = 1f) {
  val c = Tali.colors
  val tones =
    if (c.isDark) CloudTones(0xFFE4E1F7.toInt(), 0xFF9AA0DC.toInt(), 0xFF7C7CC8.toInt(), 0x40000000)
    else CloudTones(0xFFFFFFFF.toInt(), 0xFFCDD5F8.toInt(), 0xFFB0B4EE.toInt(), 0x296256C8)
  Spacer(
    modifier.size(width, width * 0.5f).graphicsLayer { this.alpha = alpha }.drawWithCache {
      val bmp = paintCloud(RECIPES[variant % RECIPES.size], size.width, tones).asImageBitmap()
      onDrawBehind { drawImage(bmp) }
    }
  )
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
  DriftingCloud(Modifier.align(Alignment.TopCenter).offset(x = 170.dp, y = 12.dp), width = 260.dp, variant = 0, driftDp = 14f, periodMs = 20000)
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
