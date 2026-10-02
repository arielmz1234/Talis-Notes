package com.tali.notes.ui.notebook

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.tali.notes.canvas.NoteCanvasView
import com.tali.notes.canvas.TextEdit
import com.tali.notes.ink.Brushes
import com.tali.notes.model.TextItem
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.CloudCrown
import com.tali.notes.ui.components.ColorDot
import com.tali.notes.ui.components.FloatingBar
import com.tali.notes.ui.components.ToolButton
import kotlin.math.roundToInt

/**
 * Typing on the page: a text field laid exactly over the text block (it follows the page when it
 * scrolls or zooms), with a small bar above it for size, colour, delete and done. Tapping anywhere
 * else finishes, like tapping away from a text box in any notes app.
 */
@Composable
fun TextEditorOverlay(
  edit: TextEdit,
  canvas: NoteCanvasView,
  cameraTick: Int,
  colors: List<Int>,
  darkPaper: Boolean,
  onChange: (TextEdit) -> Unit,
  onDone: (String) -> Unit,
) {
  val c = Tali.colors
  val density = LocalDensity.current
  var value by remember(edit.id) { mutableStateOf(TextFieldValue(edit.text, TextRange(edit.text.length))) }
  val focus = remember { FocusRequester() }
  val keyboard = LocalSoftwareKeyboardController.current
  LaunchedEffect(edit.id) {
    focus.requestFocus()
    keyboard?.show()
  }
  // Read on every camera move (the tick changes), so the field stays over its block.
  val scale = if (cameraTick >= 0) canvas.pixelsPerUnit else 1f
  val at = canvas.screenOf(edit.origin)
  val widthDp = with(density) { (edit.width * scale).toDp() }
  val fontSp = with(density) { (edit.fontSize * scale).toSp() }
  val lineSp = with(density) { (edit.fontSize * scale * TextItem.LINE).toSp() }
  val ink = Color(if (darkPaper) Brushes.onDarkPaper(edit.color) else edit.color)
  val finish = {
    keyboard?.hide()
    onDone(value.text)
  }
  androidx.activity.compose.BackHandler(onBack = finish)
  Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = finish)) {
    val pad = with(density) { 6.dp.toPx() }
    BasicTextField(
      value,
      { value = it },
      textStyle = TextStyle(color = ink, fontSize = fontSp, lineHeight = lineSp, fontFamily = FontFamily.SansSerif),
      cursorBrush = SolidColor(c.accent),
      modifier =
        Modifier.offset { IntOffset((at.x - pad).roundToInt(), (at.y - pad).roundToInt()) }
          .width(widthDp + 12.dp)
          .drawBehind {
            drawRoundRect(
              c.accent.copy(alpha = 0.55f),
              topLeft = Offset.Zero,
              size = Size(size.width, size.height),
              cornerRadius = CornerRadius(8.dp.toPx()),
              style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
            )
          }
          .padding(6.dp)
          .focusRequester(focus),
    )
    // The bar sits just above the block, or below the toolbars if the block is near the top.
    val barY = with(density) { (at.y - 64.dp.toPx()).coerceAtLeast(84.dp.toPx()) }
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), barY.roundToInt()) }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
      FloatingBar(Modifier.height(48.dp), crown = CloudCrown.NONE) {
        SizeButton("A−") { onChange(edit.copy(fontSize = (edit.fontSize / 1.15f).coerceAtLeast(10f))) }
        SizeButton("A+") { onChange(edit.copy(fontSize = (edit.fontSize * 1.15f).coerceAtMost(160f))) }
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(c.line))
        colors.take(6).forEach { col -> ColorDot(Color(col), selected = col == edit.color, size = 24.dp) { onChange(edit.copy(color = col)) } }
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(c.line))
        ToolButton(Icons.Outlined.DeleteOutline, "Delete text") {
          keyboard?.hide()
          onDone("")
        }
        ToolButton(Icons.Outlined.Check, "Done", onClick = finish)
      }
    }
  }
}

@Composable
private fun SizeButton(label: String, onClick: () -> Unit) {
  val c = Tali.colors
  Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = if (label == "A+") "Bigger text" else "Smaller text", onClick = onClick), contentAlignment = Alignment.Center) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink)
  }
}
