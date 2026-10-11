package com.tali.notes.ui.notebook

import androidx.compose.ui.res.stringResource
import com.tali.notes.R
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PrimaryButton

/**
 * The saturation/brightness square for one hue, painted in software with rounded corners. The
 * picker draws it scaled up with smoothing, so a small bitmap is plenty and nothing depends on GPU
 * gradient support.
 */
private fun svSquare(hue: Float): ImageBitmap {
  // Roughly the square's on-screen aspect, so the baked corners stay round when scaled.
  val w = 96
  val h = 40
  val pixels = IntArray(w * h)
  val hsv = floatArrayOf(hue, 0f, 0f)
  for (y in 0 until h) for (x in 0 until w) {
    hsv[1] = x / (w - 1f)
    hsv[2] = 1f - y / (h - 1f)
    pixels[y * w + x] = android.graphics.Color.HSVToColor(hsv)
  }
  return rounded(pixels, w, h, 3.2f)
}

/** The hue strip as a rounded pill. */
private val hueStrip: ImageBitmap by lazy {
  val w = 360
  val h = 22
  val pixels = IntArray(w * h)
  for (x in 0 until w) {
    val c = android.graphics.Color.HSVToColor(floatArrayOf(x.toFloat(), 1f, 1f))
    for (y in 0 until h) pixels[y * w + x] = c
  }
  rounded(pixels, w, h, h / 2f)
}

private fun rounded(pixels: IntArray, w: Int, h: Int, radius: Float): ImageBitmap {
  val src = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
  val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
  val canvas = android.graphics.Canvas(out)
  val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
  canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
  return out.asImageBitmap()
}

/** Saturation/brightness square plus a hue strip. [onChange] fires live; [onSave] adds to the palette. */
@Composable
fun ColorPicker(initial: Int, onChange: (Int) -> Unit, onSave: (Int) -> Unit) {
  val c = Tali.colors
  val hsv = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
  var hue by remember(initial) { mutableFloatStateOf(hsv[0]) }
  var sat by remember(initial) { mutableFloatStateOf(hsv[1]) }
  var value by remember(initial) { mutableFloatStateOf(hsv[2]) }
  val current = android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))
  val square = remember(hue) { svSquare(hue) }
  fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))

  Column(Modifier.fillMaxWidth()) {
    Canvas(
      Modifier.fillMaxWidth()
        .height(150.dp)
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
      drawImage(square, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
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
      drawImage(hueStrip, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.Low)
      val x = hue / 360f * size.width
      drawCircle(Color.White, size.height / 2 + 1.dp.toPx(), Offset(x.coerceIn(size.height / 2, size.width - size.height / 2), size.height / 2), style = Stroke(3.dp.toPx()))
    }
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      androidx.compose.foundation.layout.Box(Modifier.size(34.dp).clip(CircleShape).background(Color(current)).border(1.dp, c.line, CircleShape))
      Spacer(Modifier.width(12.dp))
      Text(String.format("#%06X", current and 0xFFFFFF), style = MaterialTheme.typography.titleSmall, color = c.inkMuted, modifier = Modifier.weight(1f))
      PrimaryButton(stringResource(R.string.save_colour)) { onSave(Color(current).toArgb()) }
    }
  }
}
