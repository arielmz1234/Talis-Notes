package com.tali.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import com.tali.notes.theme.Tali
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

/**
 * A little burst of cloud: soft white puffs with a lilac underside that billow outwards from the
 * middle and melt away as [progress] goes from 0 to 1. Used when something appears or disappears,
 * so the app's motion matches its sky.
 */
@Composable
fun CloudPuff(progress: Float, modifier: Modifier = Modifier, puffs: Int = 9) {
  if (progress <= 0f || progress >= 1f) return
  val c = Tali.colors
  val shade = if (c.isDark) Color(0xFF8E7FC4) else Color(0xFFCFC2F2)
  Canvas(modifier) {
    // Spreads across the whole area, so a wide toolbar gets a wide cloud.
    val rx = size.width / 2f
    val ry = size.height / 2f
    val r = minOf(rx, ry)
    val center = Offset(size.width / 2f, size.height / 2f)
    // Quick to swell, slow to fade: in over the first fifth, out over the rest.
    val alpha = if (progress < 0.2f) progress / 0.2f else (1f - progress) / 0.8f
    val ease = 1f - (1f - progress) * (1f - progress)
    for (i in 0 until puffs) {
      // Fixed, slightly uneven spacing so it reads as a cloud rather than a ring.
      val angle = (2 * PI * i / puffs + sin(i * 2.3) * 0.25).toFloat()
      val reach = (0.25f + 0.55f * ease) * (0.85f + 0.3f * ((i * 37) % 10) / 10f)
      val size = r * (0.3f + 0.35f * ease) * (0.8f + 0.4f * ((i * 53) % 10) / 10f)
      val p = center + Offset(cos(angle) * rx * reach, sin(angle) * ry * reach)
      drawCircle(shade.copy(alpha = 0.45f * alpha), size, p + Offset(0f, size * 0.22f))
      drawCircle(Color.White.copy(alpha = 0.95f * alpha), size, p)
    }
    // A row of fuller puffs through the middle, so a wide cloud has a body and not just an edge.
    val body = (rx / r).toInt().coerceIn(1, 6)
    for (j in 0 until body) {
      val t = if (body == 1) 0f else j / (body - 1f) * 2f - 1f
      val size = r * (0.42f + 0.3f * ease) * (0.85f + 0.3f * ((j * 41) % 10) / 10f)
      val p = center + Offset(t * rx * (0.3f + 0.35f * ease), (if (j % 2 == 0) -1f else 1f) * ry * 0.08f)
      drawCircle(shade.copy(alpha = 0.4f * alpha), size, p + Offset(0f, size * 0.2f))
      drawCircle(Color.White.copy(alpha = 0.95f * alpha), size, p)
    }
  }
}

/**
 * Wraps a card or row so it can come and go on a cloud: [appearing] pops it in with a puff,
 * [leaving] shrinks and fades it into one (the caller removes it once the puff has played).
 */
@Composable
fun PuffBox(leaving: Boolean, appearing: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val leave by androidx.compose.animation.core.animateFloatAsState(if (leaving) 1f else 0f, androidx.compose.animation.core.tween(if (leaving) 280 else 0), label = "leave")
  val puff = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(1f) }
  val grow = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(if (appearing) 0.6f else 1f) }
  androidx.compose.runtime.LaunchedEffect(appearing) {
    if (!appearing) return@LaunchedEffect
    puff.snapTo(0f)
    kotlinx.coroutines.coroutineScope {
      launch { grow.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.5f, stiffness = 400f)) }
      puff.animateTo(1f, androidx.compose.animation.core.tween(650))
    }
  }
  androidx.compose.runtime.LaunchedEffect(leaving) {
    if (!leaving) return@LaunchedEffect
    puff.snapTo(0f)
    puff.animateTo(1f, androidx.compose.animation.core.tween(480))
  }
  androidx.compose.foundation.layout.Box(modifier) {
    androidx.compose.foundation.layout.Box(
      androidx.compose.ui.Modifier.graphicsLayer {
        val s = grow.value * (1f - 0.15f * leave)
        scaleX = s
        scaleY = s
        alpha = 1f - leave
      }
    ) {
      content()
    }
    CloudPuff(puff.value, androidx.compose.ui.Modifier.matchParentSize())
  }
}
