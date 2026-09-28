package com.tali.notes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tali.notes.geometry.Pt
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeKind
import com.tali.notes.theme.Tali

/**
 * A pen drawn like a real tool standing in a tray: neutral body, a coloured band, and a tip shaped
 * by its style. The canvas is 26 x 56 dp; the tip points up.
 */
@Composable
fun PenGlyph(type: PenType, color: Color, modifier: Modifier = Modifier) {
  val c = Tali.colors
  val body = if (c.isDark) Color(0xFF3D3452) else Color(0xFFF1ECFA)
  val outline = if (c.isDark) Color(0xFF504468) else Color(0xFFD8CCEE)
  val wood = Color(0xFFE9C9A0)
  Canvas(modifier.size(26.dp, 56.dp)) {
    val u = size.width / 26f
    val cx = size.width / 2f
    val wide = type == PenType.HIGHLIGHTER
    val half = (if (wide) 8f else 6.5f) * u
    val bodyTop = 20f * u
    // Body
    drawRoundRect(body, Offset(cx - half, bodyTop), Size(half * 2, size.height - bodyTop + 6f * u), CornerRadius(3f * u))
    drawRoundRect(outline, Offset(cx - half, bodyTop), Size(half * 2, size.height - bodyTop + 6f * u), CornerRadius(3f * u), style = Stroke(1f * u))
    // Colour band
    drawRect(color, Offset(cx - half, bodyTop + 9f * u), Size(half * 2, 4f * u))
    if (type == PenType.DASHED) {
      for (i in 0..2) drawRect(outline, Offset(cx - 1f * u, bodyTop + (17f + i * 6f) * u), Size(2f * u, 3f * u))
    }
    when (type) {
      PenType.FINELINER, PenType.DASHED -> {
        cone(cx, bodyTop, half, 9f * u, 1.6f * u, body, outline)
        drawRoundRect(color, Offset(cx - 1.6f * u, 4f * u), Size(3.2f * u, 8f * u), CornerRadius(1.6f * u))
      }
      PenType.FOUNTAIN -> {
        val nib =
          Path().apply {
            moveTo(cx - half + 1.5f * u, bodyTop)
            quadraticTo(cx - half * 0.6f, 9f * u, cx, 2f * u)
            quadraticTo(cx + half * 0.6f, 9f * u, cx + half - 1.5f * u, bodyTop)
            close()
          }
        drawPath(nib, color)
        drawLine(body, Offset(cx, 6f * u), Offset(cx, 14f * u), 1f * u)
        drawCircle(body, 1.3f * u, Offset(cx, 14.5f * u))
      }
      PenType.PENCIL -> {
        val cone = Path().apply {
          moveTo(cx - half, bodyTop)
          lineTo(cx, 3f * u)
          lineTo(cx + half, bodyTop)
          close()
        }
        drawPath(cone, wood)
        val lead = Path().apply {
          moveTo(cx - 2.3f * u, 10f * u)
          lineTo(cx, 3f * u)
          lineTo(cx + 2.3f * u, 10f * u)
          close()
        }
        drawPath(lead, color)
      }
      PenType.CALLIGRAPHY -> {
        cone(cx, bodyTop, half, 10f * u, 3.5f * u, body, outline)
        val tip = Path().apply {
          moveTo(cx - 3.5f * u, 10f * u)
          lineTo(cx + 3.5f * u, 5f * u)
          lineTo(cx + 3.5f * u, 8f * u)
          lineTo(cx - 3.5f * u, 13f * u)
          close()
        }
        drawPath(tip, color)
      }
      PenType.HIGHLIGHTER -> {
        cone(cx, bodyTop, half, 11f * u, 5f * u, body, outline)
        val tip = Path().apply {
          moveTo(cx - 5f * u, 11f * u)
          lineTo(cx + 5f * u, 5f * u)
          lineTo(cx + 5f * u, 11f * u)
          close()
        }
        drawPath(tip, color.copy(alpha = 0.85f))
      }
    }
  }
}

private fun DrawScope.cone(cx: Float, bodyTop: Float, half: Float, top: Float, topHalf: Float, fill: Color, outline: Color) {
  val p = Path().apply {
    moveTo(cx - half, bodyTop)
    lineTo(cx - topHalf, top)
    lineTo(cx + topHalf, top)
    lineTo(cx + half, bodyTop)
    close()
  }
  drawPath(p, fill)
  drawPath(p, outline, style = Stroke(1f * (size.width / 26f)))
}

/** The shape itself, built from the same geometry the canvas uses, so icons never drift from reality. */
@Composable
fun ShapeIcon(kind: ShapeKind, color: Color, modifier: Modifier = Modifier, size: Dp = 30.dp) {
  Canvas(modifier.size(size)) {
    val s = this.size.width
    val m = s * 0.14f
    val geometry =
      when {
        kind == ShapeKind.CIRCLE -> kind.create(Pt(s / 2, s / 2), Pt(s - m, s / 2))
        kind.dragIsSegment -> kind.create(Pt(m, s - m), Pt(s - m, m))
        kind == ShapeKind.POINT_LOAD -> kind.create(Pt(s / 2, m), Pt(s / 2, s - m))
        else -> kind.create(Pt(m, m), Pt(s - m, s - m))
      }
    val stroke = Stroke(width = s * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    geometry.paths(s * 0.05f).forEach { pts ->
      if (pts.size < 2) return@forEach
      val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
      }
      drawPath(path, color, style = stroke)
    }
  }
}

/** A small square of paper showing its pattern. */
@Composable
fun PaperSwatch(style: PaperStyle, modifier: Modifier = Modifier) {
  val c = Tali.colors
  Canvas(modifier) {
    drawRoundRect(c.paper, cornerRadius = CornerRadius(8.dp.toPx()))
    val line = Color(0xFFD2C3EE)
    val step = size.width / 7f
    when (style) {
      PaperStyle.BLANK -> Unit
      PaperStyle.DOTS -> {
        var y = step
        while (y < size.height) {
          var x = step
          while (x < size.width) {
            drawCircle(line, 1.4.dp.toPx(), Offset(x, y))
            x += step
          }
          y += step
        }
      }
      PaperStyle.GRID -> grid(step, line, 1f, both = true)
      PaperStyle.LINES -> grid(step * 1.4f, line, 1.2f, both = false)
      PaperStyle.ENGINEERING -> {
        grid(step / 2.5f, Color(0xFFEBE3FB), 0.8f, both = true)
        grid(step * 2f, Color(0xFFC9B3F0), 1.2f, both = true)
      }
    }
    drawRoundRect(c.line, cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
  }
}

private fun DrawScope.grid(step: Float, color: Color, width: Float, both: Boolean) {
  var y = step
  while (y < size.height) {
    drawLine(color, Offset(0f, y), Offset(size.width, y), width * density)
    y += step
  }
  if (both) {
    var x = step
    while (x < size.width) {
      drawLine(color, Offset(x, 0f), Offset(x, size.height), width * density)
      x += step
    }
  }
}

/** Dashed outline used for "add" affordances. */
fun DrawScope.dashedCircle(color: Color, radius: Float) {
  drawCircle(color, radius, style = Stroke(1.5f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * density, 3f * density))))
}
