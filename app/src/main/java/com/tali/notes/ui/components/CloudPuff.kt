package com.tali.notes.ui.components

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.CoroutineScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/*
 * Things come and go on a little cloud. When something leaves, a puff of mist the size of it
 * appears where it was and dissipates: dozens of soft wisps drift apart and upwards, each swelling
 * and thinning on its own, so the cloud breaks up rather than fading as one block.
 *
 * Motion notes (the "why" behind the numbers):
 * - The element itself goes quickly (about 200 ms); the mist lingers a little longer but never
 *   blocks anything, and it's thin from the start, so nothing solid hangs over empty space.
 * - Strong ease-out for movement: wisps move straight away, then drift to a stop.
 * - Each wisp is a soft Gaussian dot, so overlapping wisps blend like real mist: no rims, no
 *   circles, and no offscreen layer or blur needed (cheap enough to stay smooth on the S6 Lite).
 * - In the dark the mist is pale and mostly see-through; a solid cloud there looks heavy.
 * - Arrivals never start tiny (96%) and never bounce; a lighter puff dissipates as the card lands.
 */

/** Strong ease-out: moves straight away, then settles. */
internal val EaseOutStrong = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
private val EaseInOutSoft = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)

/** How long an element takes to go, and how long a subtle puff lasts (the toolbars, sky clouds). */
const val ELEMENT_LEAVE_MS = 200
const val SUBTLE_PUFF_MS = 520

/**
 * Deleting a card, row or page: the element takes a little longer to go and its fuller mist lasts
 * longer, so it's noticed. Callers wait [PUFF_LEAVE_MS] before removing it.
 */
private const val DELETE_LEAVE_MS = 220
const val PUFF_LEAVE_MS = 600
private const val ELEMENT_ARRIVE_MS = 260
private const val PUFF_ARRIVE_MS = 520

/**
 * A puff of mist over this area that dissipates as [progress] goes from 0 to 1. [strength] scales
 * how dense it is at its thickest (a lighter puff for things arriving). The mist may spill a little
 * past the area, as clouds do. Progress is read while drawing, so playing it never recomposes.
 *
 * Subtle by default (white wisps with a hint of lilac: the toolbars and sky clouds, which the owner
 * likes quiet). [vivid] is for deleting things: a faintly lilac cloud with a deeper belly that
 * holds its density a moment longer, so it reads against white cards and pages.
 */
@Composable
fun CloudPuff(progress: () -> Float, modifier: Modifier = Modifier, strength: Float = 1f, vivid: Boolean = false) {
  val c = Tali.colors
  // Light: white (or for [vivid], faintly lilac) wisps, the lower ones lilac like the clouds' bellies.
  // Dark: pale, thin mist.
  val top = if (c.isDark) lerp(lerp(c.cloudTop, DUSK_LILAC, 0.4f), Color.White, 0.45f) else if (vivid) lerp(Color.White, c.cloudBelly, 0.28f) else Color.White
  // One even tone in the dark: varied tints there read as blotches.
  val low = if (c.isDark) top else lerp(Color.White, c.cloudBelly, if (vivid) 0.85f else 0.7f)
  val belly = lerp(c.cloudBelly, c.accent, if (vivid) 0.38f else 0.22f)
  val peak = (if (c.isDark) (if (vivid) 0.38f else 0.26f) else if (vivid) 1f else 0.95f) * strength
  val hold = if (vivid) 0.18f else 0f
  val underside = if (vivid) 0.7f else 0.4f
  Spacer(
    modifier
      .outset(OVERFLOW)
      .drawWithCache {
        val m = OVERFLOW.toPx()
        val w = size.width - 2 * m
        val h = size.height - 2 * m
        val wisps = if (w > 0f && h > 0f) mist(w, h, density, smooth = c.isDark) else emptyList()
        // A handful of tints, made once per size: wisps low in the cloud lean lilac.
        val tints = List(TONES) { i -> ColorFilter.tint(lerp(top, low, i / (TONES - 1f))) }
        // In the light a soft lilac underside gives the cloud volume against the pale page.
        val shade = if (c.isDark) null else ColorFilter.tint(belly)
        val sprite = SoftDot.image
        val src = IntSize(sprite.width, sprite.height)
        onDrawBehind {
          val t = progress()
          if (t <= 0f || t >= 1f || wisps.isEmpty()) return@onDrawBehind
          val cx = m + w / 2
          val cy = m + h / 2
          // The whole cloud rises and swells together; each wisp only wanders a little from it.
          // A deletion's fuller cloud stays a touch smaller, so it doesn't overwhelm the screen.
          val lift = -minOf(h, w) * (if (vivid) 0.22f else 0.28f) * EaseOutStrong.transform(t)
          val swell = 1f + (if (vivid) 0.14f else 0.22f) * EaseOutStrong.transform(t)
          for (pass in (if (shade != null) 0 else 1)..1) for (p in wisps) {
            // The lilac underside sits only under the lower part of the cloud.
            if (pass == 0 && p.tone < TONES / 2) continue
            // Each wisp runs on its own clock: starts a little late, finishes when the puff does.
            val local = ((t - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val go = EaseOutStrong.transform(local)
            // Thick at once, holding for a moment so the cloud is seen, then thinning out evenly.
            val a = peak * p.weight * EaseOutStrong.transform((local / 0.12f).coerceAtMost(1f)) * (1f - EaseInOutSoft.transform(((local - hold) / (1f - hold)).coerceIn(0f, 1f)))
            if (a <= 0.004f) continue
            val x = cx + p.x * swell + p.dx * go
            val y = cy + p.y * swell + p.dy * go + lift + if (pass == 0) p.r * 0.3f else 0f
            val r = p.r * (if (vivid) 0.82f + 0.36f * go else 0.9f + 0.45f * go)
            val d = (2 * r).roundToInt().coerceAtLeast(1)
            drawImage(
              sprite,
              srcOffset = IntOffset.Zero,
              srcSize = src,
              dstOffset = IntOffset((x - r).roundToInt(), (y - r).roundToInt()),
              dstSize = IntSize(d, d),
              alpha = (if (pass == 0) a * underside else a).coerceAtMost(1f),
              colorFilter = if (pass == 0) shade else tints[p.tone],
            )
          }
        }
      }
  )
}

/**
 * Wraps a card, tile or row so it can come and go on a cloud. [leaving] dissolves it into a puff
 * of mist (the caller removes it [PUFF_LEAVE_MS] later); [appearing] lets it settle in as a lighter
 * puff dissipates around it.
 */
@Composable
fun PuffBox(leaving: Boolean, appearing: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val leave by animateFloatAsState(if (leaving) 1f else 0f, tween(if (leaving) DELETE_LEAVE_MS else 0, easing = EaseOutStrong), label = "leave")
  val arrive = remember { Animatable(if (appearing) 0f else 1f) }
  val puff = remember { Animatable(1f) }
  val layer = LocalPuffLayer.current
  // Where it is, for handing its mist to the layer above the screen.
  val where = remember { arrayOfNulls<LayoutCoordinates>(1) }
  var handedOff by remember { mutableStateOf(false) }
  LaunchedEffect(appearing) {
    if (!appearing) return@LaunchedEffect
    arrive.snapTo(0f)
    puff.snapTo(0f)
    coroutineScope {
      launch { puff.animateTo(1f, tween(PUFF_ARRIVE_MS, easing = LinearEasing)) }
      arrive.animateTo(1f, tween(ELEMENT_ARRIVE_MS, delayMillis = 40, easing = EaseOutStrong))
    }
  }
  LaunchedEffect(leaving) {
    if (!leaving) return@LaunchedEffect
    // Drawn above the whole screen when there's a layer for it, so the mist drifts freely over the
    // sidebar, the header or the dialog's edge instead of being cut off by the list it was in.
    if (layer != null && layer.play(where[0])) {
      handedOff = true
      return@LaunchedEffect
    }
    puff.snapTo(0f)
    puff.animateTo(1f, tween(PUFF_LEAVE_MS, easing = LinearEasing))
  }
  // While it leaves it sits above its neighbours, so the mist drifting over them isn't cut off.
  Box(modifier.zIndex(if (leaving) 1f else 0f).onGloballyPositioned { where[0] = it }) {
    Box(
      Modifier.layerWithRoom(LAYER_ROOM) {
        // Out: lift a little and shrink into the mist as it fades and blurs. In: the same, backwards.
        val gone = maxOf(leave, 1f - arrive.value)
        val s = 1f - 0.07f * gone
        scaleX = s
        scaleY = s
        translationY = -10.dp.toPx() * leave
        alpha = 1f - gone
        renderEffect =
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && gone > 0.02f && gone < 0.98f) {
            // In whole steps, so a new effect isn't built every frame.
            val r = (6.dp.toPx() * gone).roundToInt().toFloat().coerceAtLeast(1f)
            BlurEffect(r, r, TileMode.Decal)
          } else null
      }
    ) {
      content()
    }
    if (!handedOff) CloudPuff({ puff.value }, Modifier.matchParentSize(), strength = if (appearing && !leaving) 0.55f else 1f, vivid = leaving)
  }
}

/**
 * Plays the mist of things being deleted above everything inside a [PuffLayerHost], so it's never
 * cut off by the list, grid or panel the thing was in.
 */
class PuffLayer internal constructor(private val scope: CoroutineScope) {
  internal var host: LayoutCoordinates? = null
  internal val puffs = mutableStateListOf<Puff>()

  internal class Puff(val rect: androidx.compose.ui.geometry.Rect) {
    val progress = Animatable(0f)
  }

  /** Starts a deletion puff over [from]'s area; false when it can't (then the caller plays its own). */
  fun play(from: LayoutCoordinates?): Boolean {
    val h = host ?: return false
    if (from == null || !h.isAttached || !from.isAttached) return false
    val puff = Puff(h.localBoundingBoxOf(from, clipBounds = false))
    puffs += puff
    scope.launch {
      try {
        puff.progress.animateTo(1f, tween(PUFF_LEAVE_MS, easing = LinearEasing))
      } finally {
        puffs -= puff
      }
    }
    return true
  }
}

val LocalPuffLayer = staticCompositionLocalOf<PuffLayer?> { null }

/** A box whose deletion puffs ([PuffBox]) are drawn over all of its content, unclipped. */
@Composable
fun PuffLayerHost(modifier: Modifier = Modifier, contentAlignment: Alignment = Alignment.TopStart, content: @Composable BoxScope.() -> Unit) {
  val scope = rememberCoroutineScope()
  val layer = remember(scope) { PuffLayer(scope) }
  Box(modifier.onGloballyPositioned { layer.host = it }, contentAlignment = contentAlignment) {
    CompositionLocalProvider(LocalPuffLayer provides layer) { this@Box.content() }
    // Its own top-left-aligned box: the host may centre its content, which would shift the mist.
    Box(Modifier.matchParentSize()) {
    layer.puffs.forEach { p ->
      key(p) {
        CloudPuff(
          { p.progress.value },
          Modifier.layout { measurable, _ ->
            val placeable = measurable.measure(Constraints.fixed(p.rect.width.roundToInt().coerceAtLeast(1), p.rect.height.roundToInt().coerceAtLeast(1)))
            // Takes no room of its own; just drawn where the deleted thing was.
            layout(0, 0) { placeable.place(p.rect.left.roundToInt(), p.rect.top.roundToInt()) }
          },
          vivid = true,
        )
      }
    }
    }
  }
}

/**
 * A graphics layer with [room] to spare on every side. Fading or blurring draws through an
 * offscreen buffer the size of the layer, which would cut off anything drawn outside the content
 * (a card's shadow, a toolbar's cloud bumps); the room keeps it, without changing the layout.
 */
fun Modifier.layerWithRoom(room: Dp, block: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit): Modifier =
  outset(room).graphicsLayer(block).padding(room)

/** Enough room for the largest card shadow and cloud bumps. */
private val LAYER_ROOM: Dp = 28.dp

/** Room around the area for the mist to spill into as it spreads and rises. */
private val OVERFLOW: Dp = 56.dp

/** A pale lilac the dark-mode mist leans towards. */
private val DUSK_LILAC = Color(0xFFB7A6EE)

/** How many tints the wisps come in, from the top of the cloud to its lilac underside. */
private const val TONES = 4

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

/** One wisp: where it starts (from the centre), where it drifts to, its size, timing and tint. */
private class Wisp(val x: Float, val y: Float, val dx: Float, val dy: Float, val r: Float, val delay: Float, val weight: Float, val tone: Int)

/** A stable pseudo-random number in [0, 1), so the mist looks natural but is the same each time. */
private fun hash(i: Int, salt: Int): Float {
  val x = sin(i * 127.1 + salt * 311.7) * 43758.5453
  return (x - floor(x)).toFloat()
}

/**
 * Wisps covering a [w] by [h] area (any shape: a toolbar, a card, a tall page), on a jittered grid
 * a little inside the edges with rounded-off corners, each drifting outwards from the middle and
 * upwards. [density] keeps wisp sizes sensible in dp.
 */
private fun mist(w: Float, h: Float, density: Float, smooth: Boolean): List<Wisp> {
  // Wisp size follows the smaller side, within sensible bounds, and the count stays modest.
  // Thin rows still get big, soft wisps (a few of them), not a string of small dots.
  var r = maxOf(minOf(w, h) * 0.42f, 24f * density).coerceAtMost(58f * density) * (if (smooth) 1.35f else 1f)
  var step = r * 0.72f
  while (ceil(w / step) * ceil(h / step) > 56) {
    r *= 1.12f
    step = r * 0.72f
  }
  val cols = ceil(w / step).toInt().coerceAtLeast(1)
  // A thin row gets a single line of wisps; two lines in so little height read as a wavy band.
  val rows = if (h < r * 1.6f) 1 else ceil(h / step).toInt().coerceAtLeast(1)
  val out = ArrayList<Wisp>(cols * rows)
  val reach = hypot(w / 2, h / 2)
  var i = 0
  for (row in 0 until rows) {
    for (col in 0 until cols) {
      i++
      val u = (col + 0.5f) / cols - 0.5f
      val v = (row + 0.5f) / rows - 0.5f
      // Round off the corners so the puff reads as a cloud, not a rectangle of dots.
      if (cols > 2 && rows > 2 && (u * u + v * v) > 0.42f && hash(i, 9) < 0.7f) continue
      val x = u * w * 0.9f + (hash(i, 1) - 0.5f) * step * 0.6f
      val y = v * h * 0.9f + (hash(i, 2) - 0.5f) * step * 0.6f
      // Drift outwards from the middle, and upwards: mist rises as it thins.
      val len = hypot(x, y).coerceAtLeast(1f)
      val spread = r * (0.25f + 0.45f * hash(i, 3)) * (len / reach)
      val dx = x / len * spread + (hash(i, 4) - 0.5f) * r * 0.3f
      val dy = y / len * spread * 0.5f - r * 0.35f * hash(i, 10)
      // The top row is puffier, giving the cloud its bumpy outline.
      val size = r * (0.8f + 0.45f * hash(i, 5)) * (if (row == 0 && rows > 1) 1.15f else 1f)
      val tone = (((v + 0.5f) * (TONES - 1)) + (hash(i, 6) - 0.5f)).roundToInt().coerceIn(0, TONES - 1)
      // [smooth] (the dark): even wisps, so light mist on a dark page doesn't look blotchy.
      out += Wisp(
        x, y,
        if (smooth) dx * 0.5f else dx,
        if (smooth) dy * 0.5f else dy,
        size,
        delay = hash(i, 7) * (if (smooth) 0.04f else 0.12f),
        weight = if (smooth) 1f else 0.75f + 0.25f * hash(i, 8),
        tone = tone,
      )
    }
  }
  return out
}

/** A soft Gaussian dot, white, made once: wisps are tinted and scaled copies of it. */
private object SoftDot {
  val image: ImageBitmap by lazy {
    val size = 96
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val half = size / 2f
    val stops = FloatArray(9) { it / 8f }
    val colors = IntArray(9) { k ->
      val a = exp(-(stops[k] * stops[k]) / (2 * 0.44f * 0.44f)) * (1f - stops[k] * stops[k] * stops[k])
      (((a * 255f).roundToInt().coerceIn(0, 255)) shl 24) or 0xFFFFFF
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(half, half, half, colors, stops, Shader.TileMode.CLAMP) }
    android.graphics.Canvas(bmp).drawCircle(half, half, half, paint)
    bmp.asImageBitmap()
  }
}
