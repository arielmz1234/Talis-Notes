package com.tali.notes.ui.notebook

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PrimaryButton

/** Saturation/brightness square plus a hue strip. [onChange] fires live; [onSave] adds to the palette. */
@Composable
fun ColorPicker(initial: Int, onChange: (Int) -> Unit, onSave: (Int) -> Unit) {
  val c = Tali.colors
  val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
  var hue by remember(initial) { mutableFloatStateOf(hsv[0]) }
  var sat by remember(initial) { mutableFloatStateOf(hsv[1]) }
  var value by remember(initial) { mutableFloatStateOf(hsv[2]) }
  val current = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
  fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

  Column(Modifier.fillMaxWidth()) {
    Canvas(
      Modifier.fillMaxWidth()
        .height(150.dp)
        .clip(RoundedCornerShape(12.dp))
        .pointerInput(Unit) {
          fun set(o: Offset) {
            sat = (o.x / size.width).coerceIn(0f, 1f)
            value = 1f - (o.y / size.height).coerceIn(0f, 1f)
            emit()
          }
          detectTapGestures { set(it) }
        }
        .pointerInput(Unit) {
          detectDragGestures(onDragStart = { o ->
            sat = (o.x / size.width).coerceIn(0f, 1f)
            value = 1f - (o.y / size.height).coerceIn(0f, 1f)
            emit()
          }) { change, _ ->
            sat = (change.position.x / size.width).coerceIn(0f, 1f)
            value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
            emit()
          }
        }
    ) {
      drawRect(Brush.horizontalGradient(listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))))
      drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
      val p = Offset(sat * size.width, (1f - value) * size.height)
      drawCircle(Color.White, 9.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
      drawCircle(Color(0x55000000), 10.5.dp.toPx(), p, style = Stroke(1.dp.toPx()))
    }
    Spacer(Modifier.height(12.dp))
    Canvas(
      Modifier.fillMaxWidth()
        .height(22.dp)
        .pointerInput(Unit) {
          detectTapGestures {
            hue = (it.x / size.width).coerceIn(0f, 1f) * 360f
            emit()
          }
        }
        .pointerInput(Unit) {
          detectDragGestures { change, _ ->
            hue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
            emit()
          }
        }
    ) {
      val colors = (0..12).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 30f, 1f, 1f))) }
      drawRoundRect(Brush.horizontalGradient(colors), cornerRadius = CornerRadius(size.height / 2))
      val x = hue / 360f * size.width
      drawCircle(Color.White, size.height / 2 + 1.dp.toPx(), Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2), style = Stroke(3.dp.toPx()))
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      androidx.compose.foundation.layout.Box(Modifier.size(34.dp).clip(CircleShape).background(Color(current)).border(1.dp, c.line, CircleShape))
      Spacer(Modifier.width(12.dp))
      Text(String.format("#%06X", current and 0xFFFFFF), style = MaterialTheme.typography.titleSmall, color = c.inkMuted, modifier = Modifier.weight(1f))
      PrimaryButton("Save colour") { onSave(Color(current).toArgb()) }
    }
  }
}
