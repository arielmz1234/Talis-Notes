package com.tali.notes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A slider with − and + on either side for fine steps of [step] (0.1 for thickness). Holding a
 * button keeps stepping, faster the longer it's held. Values always land on whole steps.
 * [onChangeFinished] runs when the slider or a button is let go, for things that should only
 * happen once per change (an undo step).
 */
@Composable
fun StepSlider(
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  step: Float,
  onChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  onChangeFinished: (() -> Unit)? = null,
) {
  val c = Tali.colors
  fun snap(v: Float) = ((v / step).roundToInt() * step).coerceIn(range.start, range.endInclusive)
  val current by rememberUpdatedState(value)
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    StepButton(Icons.Outlined.Remove, "Thinner", enabled = value > range.start + step / 2, onStep = { onChange(snap(current - step)) }, onDone = { onChangeFinished?.invoke() })
    Slider(
      value = value,
      onValueChange = { onChange(snap(it)) },
      onValueChangeFinished = onChangeFinished,
      valueRange = range,
      colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.sunken),
      modifier = Modifier.weight(1f),
    )
    StepButton(Icons.Outlined.Add, "Thicker", enabled = value < range.endInclusive - step / 2, onStep = { onChange(snap(current + step)) }, onDone = { onChangeFinished?.invoke() })
  }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onStep: () -> Unit, onDone: () -> Unit) {
  val c = Tali.colors
  var pressed by remember { mutableStateOf(false) }
  // A quick, small press-in so the tap is felt; it springs back as soon as it's let go.
  val scale by animateFloatAsState(if (pressed) 0.9f else 1f, tween(if (pressed) 90 else 160), label = "stepPress")
  val step by rememberUpdatedState(onStep)
  val done by rememberUpdatedState(onDone)
  val on by rememberUpdatedState(enabled)
  Box(
    Modifier.size(34.dp)
      .graphicsLayer {
        scaleX = scale
        scaleY = scale
      }
      .clip(CircleShape)
      .background(c.sunken)
      .semantics {
        role = Role.Button
        contentDescription = label
        onClick { step(); done(); true }
      }
      .pointerInput(Unit) {
        coroutineScope {
          awaitEachGesture {
            awaitFirstDown()
            if (!on) return@awaitEachGesture
            pressed = true
            step()
            // Held: repeat after a pause, speeding up.
            val repeat = launch {
              delay(420)
              var gap = 110L
              while (true) {
                step()
                delay(gap)
                gap = (gap * 0.85f).toLong().coerceAtLeast(35L)
              }
            }
            waitForUpOrCancellation()
            repeat.cancel()
            pressed = false
            done()
          }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, null, tint = if (enabled) c.ink else c.inkFaint, modifier = Modifier.size(18.dp))
  }
}
