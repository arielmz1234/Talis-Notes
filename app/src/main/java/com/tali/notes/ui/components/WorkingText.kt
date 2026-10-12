package com.tali.notes.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.PI
import kotlin.math.sin

/** One wave of the three dots, then a short rest before the next. */
private const val WAVE_MS = 1150
/** How long each dot takes to hop up and land. */
private const val HOP_MS = 420
/** How far behind the dot before it each one starts. */
private const val DOT_GAP_MS = 120

/**
 * A status line. While something is under way (the text ends with "…", like "Backing up…"), the
 * three dots hop one after another in a little wave, so she can see it's working; when the text
 * changes (to "Backed up." or an error) the new line rises in softly in place of the old one.
 */
@Composable
fun WorkingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
  AnimatedContent(
    // Saved messages are kept by name (`later`) and put into words here, in the language shown now.
    targetState = com.tali.notes.shown(text),
    transitionSpec = {
      (fadeIn(tween(200, delayMillis = 60)) + slideInVertically(tween(260, easing = EaseOutStrong)) { it / 3 }) togetherWith fadeOut(tween(120))
    },
    label = "status",
    modifier = modifier,
  ) { line ->
    if (!line.endsWith("…")) {
      Text(line, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    } else {
      Row {
        Text(line.dropLast(1), style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        HoppingDots(style, color)
      }
    }
  }
}

@Composable
private fun HoppingDots(style: TextStyle, color: Color) {
  val clock = rememberInfiniteTransition(label = "dots")
  val ms by clock.animateFloat(0f, WAVE_MS.toFloat(), infiniteRepeatable(tween(WAVE_MS, easing = LinearEasing), RepeatMode.Restart), label = "wave")
  for (i in 0 until 3) {
    Text(
      ".",
      style = style,
      color = color,
      modifier = Modifier.graphicsLayer {
        // Read in the draw phase only: the dots move without recomposing anything.
        val t = ((ms - i * DOT_GAP_MS) / HOP_MS).coerceIn(0f, 1f)
        val up = sin(PI * t).toFloat()
        translationY = -up * size.height * 0.32f
        scaleX = 1f + 0.25f * up
        scaleY = 1f + 0.25f * up
        transformOrigin = TransformOrigin(0.5f, 0.8f)
      },
    )
  }
}
