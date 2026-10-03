package com.tali.notes.ui.notebook

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tali.notes.model.LineStyle
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.rememberSlidingSelection
import com.tali.notes.ui.components.slidingHighlight
import com.tali.notes.ui.components.slidingOption

/**
 * The six line styles as tiles, three to a row, each showing a little stroke drawn that way. One
 * highlight glides to the chosen tile, as in every other one-of-many choice in the app.
 */
@Composable
fun LineStylePicker(selected: LineStyle, onPick: (LineStyle) -> Unit, modifier: Modifier = Modifier) {
  val c = Tali.colors
  val glide = rememberSlidingSelection()
  Column(modifier.slidingHighlight(glide, selected, SolidColor(c.accentSoft), 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    LineStyle.entries.chunked(3).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        row.forEach { style ->
          val on = style == selected
          val ink by animateColorAsState(if (on) c.accent else c.ink, tween(220), label = "lineInk")
          val label by animateColorAsState(if (on) c.accent else c.inkMuted, tween(220), label = "lineLabel")
          val edge by animateColorAsState(if (on) Color.Transparent else c.line, tween(220), label = "lineEdge")
          Column(
            Modifier.slidingOption(glide, style)
              .weight(1f)
              .clip(RoundedCornerShape(14.dp))
              .border(1.dp, edge, RoundedCornerShape(14.dp))
              .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClickLabel = style.label) { onPick(style) }
              .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            LineSample(style, ink)
            Spacer(Modifier.height(4.dp))
            Text(style.label, style = MaterialTheme.typography.labelSmall, color = label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
          }
        }
      }
    }
  }
}

/** A short S-shaped stroke drawn in [style], like a pen mark. */
@Composable
fun LineSample(style: LineStyle, color: Color, modifier: Modifier = Modifier, width: Dp = 1.8.dp) {
  Canvas(modifier.size(58.dp, 30.dp)) {
    val w = width.toPx()
    val path =
      Path().apply {
        moveTo(size.width * 0.12f, size.height * 0.82f)
        cubicTo(size.width * 0.55f, size.height * 0.95f, size.width * 0.45f, size.height * 0.05f, size.width * 0.88f, size.height * 0.18f)
      }
    drawPath(path, color, style = Stroke(w, cap = StrokeCap.Round, pathEffect = pathEffect(style, w)))
  }
}

/**
 * The dash pattern for [style] on a round-capped line [w] wide: the round ends stick out half the
 * width past each piece, so pieces are drawn that much shorter and gaps that much longer.
 */
fun pathEffect(style: LineStyle, w: Float): PathEffect? {
  if (style.pattern.isEmpty()) return null
  val intervals = style.pattern.mapIndexed { i, v -> if (i % 2 == 0) maxOf((v - 1f) * w, 0.01f) else (v + 1f) * w }
  return PathEffect.dashPathEffect(intervals.toFloatArray())
}

/**
 * The six line styles in one row (sharing out the width it's given), each a straight stroke with no label: the selection
 * menu's compact version of [LineStylePicker], with the same gliding highlight.
 */
@Composable
fun CompactLineStylePicker(selected: LineStyle, onPick: (LineStyle) -> Unit, modifier: Modifier = Modifier) {
  val c = Tali.colors
  val glide = rememberSlidingSelection()
  Row(modifier.slidingHighlight(glide, selected, SolidColor(c.accentSoft), 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    LineStyle.entries.forEach { style ->
      val ink by animateColorAsState(if (style == selected) c.accent else c.ink, tween(220), label = "lineInk")
      Canvas(
        Modifier.slidingOption(glide, style)
          .weight(1f)
          .height(30.dp)
          .clip(RoundedCornerShape(10.dp))
          .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClickLabel = style.label) { onPick(style) }
      ) {
        val w = 2.dp.toPx()
        val y = size.height / 2
        drawLine(ink, androidx.compose.ui.geometry.Offset(size.width * 0.15f, y), androidx.compose.ui.geometry.Offset(size.width * 0.85f, y), w, StrokeCap.Round, pathEffect(style, w))
      }
    }
  }
}
