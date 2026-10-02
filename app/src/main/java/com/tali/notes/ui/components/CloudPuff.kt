package com.tali.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.tali.notes.theme.Tali
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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
    val r = minOf(size.width, size.height) / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    // Quick to swell, slow to fade: in over the first fifth, out over the rest.
    val alpha = if (progress < 0.2f) progress / 0.2f else (1f - progress) / 0.8f
    val ease = 1f - (1f - progress) * (1f - progress)
    for (i in 0 until puffs) {
      // Fixed, slightly uneven spacing so it reads as a cloud rather than a ring.
      val angle = (2 * PI * i / puffs + sin(i * 2.3) * 0.25).toFloat()
      val reach = r * (0.25f + 0.55f * ease) * (0.85f + 0.3f * ((i * 37) % 10) / 10f)
      val size = r * (0.16f + 0.2f * ease) * (0.8f + 0.4f * ((i * 53) % 10) / 10f)
      val p = center + Offset(cos(angle) * reach, sin(angle) * reach)
      drawCircle(shade.copy(alpha = 0.45f * alpha), size, p + Offset(0f, size * 0.22f))
      drawCircle(Color.White.copy(alpha = 0.95f * alpha), size, p)
    }
  }
}
