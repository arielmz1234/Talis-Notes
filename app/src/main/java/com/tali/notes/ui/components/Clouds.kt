package com.tali.notes.ui.components

import android.graphics.Bitmap
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.produceState
import androidx.compose.animation.core.animateFloatAsState
import android.util.LruCache
import android.graphics.BlurMaskFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * make the sunlit top; the rest are the small scallops along the base. [base] is a rounded bar
 * (left, top, right, bottom) that fills the gaps between them, so the underside stays calm.
 */
private class CloudRecipe(val crown: Int, val puffs: List<Triple<Float, Float, Float>>, val base: RectF)

private fun p(x: Float, y: Float, r: Float) = Triple(x, y, r)

private val RECIPES =
  listOf(
    // Big cumulus.
    CloudRecipe(
      5,
      listOf(
        p(0.31f, 0.25f, 0.12f), p(0.49f, 0.17f, 0.165f), p(0.68f, 0.235f, 0.135f), p(0.83f, 0.32f, 0.09f), p(0.17f, 0.33f, 0.085f),
        p(0.12f, 0.405f, 0.06f), p(0.25f, 0.40f, 0.075f), p(0.38f, 0.405f, 0.08f), p(0.51f, 0.40f, 0.085f), p(0.64f, 0.405f, 0.08f), p(0.77f, 0.40f, 0.075f), p(0.88f, 0.41f, 0.055f),
      ),
      RectF(0.12f, 0.28f, 0.88f, 0.47f),
    ),
    // Long, low cloud.
    CloudRecipe(
      5,
      listOf(
        p(0.25f, 0.30f, 0.10f), p(0.42f, 0.235f, 0.14f), p(0.61f, 0.27f, 0.12f), p(0.78f, 0.33f, 0.085f), p(0.11f, 0.36f, 0.07f),
        p(0.08f, 0.415f, 0.045f), p(0.18f, 0.41f, 0.06f), p(0.30f, 0.41f, 0.065f), p(0.43f, 0.41f, 0.07f), p(0.56f, 0.41f, 0.07f), p(0.69f, 0.41f, 0.065f), p(0.81f, 0.41f, 0.06f), p(0.90f, 0.415f, 0.045f),
      ),
      RectF(0.08f, 0.30f, 0.90f, 0.47f),
    ),
    // Small, round puff.
    CloudRecipe(
      4,
      listOf(
        p(0.36f, 0.22f, 0.17f), p(0.62f, 0.24f, 0.16f), p(0.20f, 0.34f, 0.11f), p(0.82f, 0.35f, 0.10f),
        p(0.14f, 0.41f, 0.06f), p(0.27f, 0.405f, 0.08f), p(0.42f, 0.405f, 0.085f), p(0.58f, 0.405f, 0.085f), p(0.73f, 0.405f, 0.08f), p(0.87f, 0.41f, 0.06f),
      ),
      RectF(0.14f, 0.28f, 0.87f, 0.47f),
    ),
  )

/** Cloud colours: the lit white, the shaded underside and its deepest tone, and the floating shadow. */
private class CloudTones(val light: Int, val shade: Int, val deep: Int, val shadow: Int)

/**
 * Paints one cloud into a bitmap. The body fades from white into a blue-lilac belly; a heavily
 * blurred white mass over the crown makes the top glow without showing any single circle, and soft
 * creases under the crown puffs hint at the lobes. A soft oval shadow floats below.
 */
private fun paintCloud(recipe: CloudRecipe, w: Float, tones: CloudTones): Bitmap {
  val bmp = Bitmap.createBitmap(w.toInt().coerceAtLeast(1), (w * CLOUD_BITMAP_ASPECT).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(bmp)
  val shadow =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tones.shadow
      maskFilter = BlurMaskFilter(w * 0.03f, BlurMaskFilter.Blur.NORMAL)
    }
  canvas.drawOval(RectF(w * 0.12f, w * 0.47f, w * 0.88f, w * 0.53f), shadow)

  val body = android.graphics.Path()
  for ((x, y, r) in recipe.puffs) body.addCircle(x * w, y * w, r * w, android.graphics.Path.Direction.CW)
  val b = recipe.base
  body.addRoundRect(b.left * w, b.top * w, b.right * w, b.bottom * w, (b.bottom - b.top) * w / 2, (b.bottom - b.top) * w / 2, android.graphics.Path.Direction.CW)
  val top = recipe.puffs.minOf { it.second - it.third } * w
  val bottom = recipe.puffs.maxOf { it.second + it.third } * w
  canvas.drawPath(
    body,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      shader = LinearGradient(0f, top, 0f, bottom, intArrayOf(tones.light, tones.light, tones.shade, tones.deep), floatArrayOf(0f, 0.42f, 0.78f, 1f), Shader.TileMode.CLAMP)
    },
  )

  canvas.save()
  canvas.clipPath(body)
  // The glow: crown puffs lifted a touch and blurred so far that no circle edge survives.
  val glow = android.graphics.Path()
  for (i in 0 until recipe.crown) {
    val (x, y, r) = recipe.puffs[i]
    glow.addCircle((x - r * 0.05f) * w, (y - r * 0.12f) * w, r * w * 0.92f, android.graphics.Path.Direction.CW)
  }
  canvas.drawPath(
    glow,
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = tones.light
      alpha = 242
      maskFilter = BlurMaskFilter(w * 0.045f, BlurMaskFilter.Blur.NORMAL)
    },
  )
  // Creases: a soft shade along the lower rim of each crown puff, curving away from the centre.
  val crease =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = w * 0.022f
      strokeCap = Paint.Cap.ROUND
      color = tones.shade
      alpha = 140
      maskFilter = BlurMaskFilter(w * 0.014f, BlurMaskFilter.Blur.NORMAL)
    }
  for (i in 1 until recipe.crown) {
    val (x, y, r) = recipe.puffs[i]
    val rr = r * w * 0.97f
    canvas.drawArc(RectF(x * w - rr, y * w - rr, x * w + rr, y * w + rr), if (x < 0.5f) 63f else 27f, 90f, false, crease)
  }
  canvas.restore()
  return bmp
}

/** The cloud bitmap is a little taller than the layout box so the floating shadow has room. */
private const val CLOUD_BITMAP_ASPECT = 0.56f

/** Painted clouds, shared across screens so coming back to the library costs nothing. */
private val cloudCache = LruCache<String, ImageBitmap>(24)

/**
 * One fluffy cartoon cloud: white, sunlit puffs on top, a soft blue-lilac underside with little
 * scallops, and a gentle shadow floating beneath. It is painted once, off the main thread, and
 * fades in when ready; after that drifting and scrolling cost nothing.
 */
@Composable
fun Cloud(modifier: Modifier = Modifier, width: Dp = 160.dp, variant: Int = 0, alpha: Float = 1f) {
  val c = Tali.colors
  val px = with(LocalDensity.current) { width.toPx() }
  val dark = c.isDark
  val key = "$variant/${px.toInt()}/$dark"
  val image by
    produceState(cloudCache.get(key), key) {
      if (value == null) {
        val tones =
          if (dark) CloudTones(0xFFE4E1F7.toInt(), 0xFF9AA0DC.toInt(), 0xFF7C7CC8.toInt(), 0x40000000)
          else CloudTones(0xFFFFFFFF.toInt(), 0xFFD3D9FA.toInt(), 0xFFB3B8EF.toInt(), 0x296256C8)
        value =
          withContext(Dispatchers.Default) {
            val bmp = paintCloud(RECIPES[variant % RECIPES.size], px, tones)
            bmp.prepareToDraw()
            bmp.asImageBitmap().also { cloudCache.put(key, it) }
          }
      }
    }
  val shown by animateFloatAsState(if (image != null) 1f else 0f, tween(700), label = "cloud")
  Spacer(
    modifier.size(width, width * 0.5f).graphicsLayer { this.alpha = alpha * shown }.drawBehind { image?.let { drawImage(it) } }
  )
}

/**
 * A cloud that drifts slowly sideways on its own GPU layer (no re-layout, no re-draw). The drift is
 * so slow that 12 updates a second look perfectly smooth, which keeps the tablet cool and saves
 * battery while the library sits open; it follows the frame clock, so it stops in the background.
 */
@Composable
fun DriftingCloud(modifier: Modifier = Modifier, width: Dp, variant: Int = 0, driftDp: Float = 10f, periodMs: Int = 9000, alpha: Float = 1f) {
  var x by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(periodMs) {
    val start = withFrameMillis { it }
    while (true) {
      val t = withFrameMillis { it } - start
      // Eased back and forth: -1 → 1 → -1 over two periods.
      x = -kotlin.math.cos(t.toDouble() / periodMs * Math.PI).toFloat()
      delay(80)
    }
  }
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
  // Two small, faded clouds further away, drifting slower: a little depth behind the big ones.
  DriftingCloud(Modifier.align(Alignment.TopStart).offset(x = 196.dp, y = 104.dp), width = 96.dp, variant = 1, driftDp = 5f, periodMs = 23000, alpha = 0.55f)
  DriftingCloud(Modifier.align(Alignment.TopEnd).offset(x = (-196).dp, y = 118.dp), width = 84.dp, variant = 2, driftDp = 4f, periodMs = 26000, alpha = 0.5f)
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
