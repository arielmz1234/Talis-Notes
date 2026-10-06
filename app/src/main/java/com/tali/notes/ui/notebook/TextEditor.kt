package com.tali.notes.ui.notebook

import androidx.compose.foundation.border

import androidx.compose.material3.Icon

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import com.tali.notes.ui.components.colorRing
import com.tali.notes.ui.components.rememberSlidingSelection

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
  var fieldHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
  // The width grip's drag outlives recompositions: it works on the block as it is now.
  val latest by androidx.compose.runtime.rememberUpdatedState(edit)
  val onChangeNow by androidx.compose.runtime.rememberUpdatedState(onChange)
  val scaleNow: () -> Float = { canvas.pixelsPerUnit }
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
          .focusRequester(focus)
          .onSizeChanged { fieldHeight = it.height },
    )
    // Resizing, like a rectangle: a dot in the middle of each side stretches one way (left and right
    // set the width and the text re-wraps; top and bottom set the text size), the opposite side
    // staying put; the resize chip at the corner scales the whole block. In world units, so it's the
    // same block at any zoom.
    val boxLeft = at.x - pad
    val boxTop = at.y - pad
    val boxW = edit.width * scale + with(density) { 12.dp.toPx() }
    // The field's measured height is inside its padding; the dashed box is 6 dp bigger each way.
    val boxH = fieldHeight + with(density) { 12.dp.toPx() }
    SideDot(boxLeft, boxTop + boxH / 2, { latest }, onChangeNow) { start, d ->
      val w = (start.width - d.x / scaleNow()).coerceIn(MIN_TEXT_WIDTH, MAX_TEXT_WIDTH)
      start.copy(width = w, origin = com.tali.notes.geometry.Pt(start.origin.x + (start.width - w), start.origin.y))
    }
    SideDot(boxLeft + boxW, boxTop + boxH / 2, { latest }, onChangeNow) { start, d ->
      start.copy(width = (start.width + d.x / scaleNow()).coerceIn(MIN_TEXT_WIDTH, MAX_TEXT_WIDTH))
    }
    SideDot(boxLeft + boxW / 2, boxTop, { latest }, onChangeNow) { start, d ->
      val h = fieldHeight.toFloat().coerceAtLeast(1f)
      val size = (start.fontSize * (1f - d.y / h)).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)
      val f = size / start.fontSize
      // The bottom stays where it was: the top moves by how much the block grew.
      start.copy(fontSize = size, origin = com.tali.notes.geometry.Pt(start.origin.x, start.origin.y + h / scaleNow() * (1f - f)))
    }
    SideDot(boxLeft + boxW / 2, boxTop + boxH, { latest }, onChangeNow) { start, d ->
      val h = fieldHeight.toFloat().coerceAtLeast(1f)
      start.copy(fontSize = (start.fontSize * (1f + d.y / h)).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE))
    }
    val chip = with(density) { 30.dp.toPx() }
    val chipGap = with(density) { 10.dp.toPx() }
    Box(
      Modifier.offset { IntOffset((boxLeft + boxW + chipGap - chip / 2).roundToInt(), (boxTop + boxH + chipGap - chip / 2).roundToInt()) }
        .size(30.dp)
        .pointerInput(Unit) { detectTapGestures { } }
        .pointerInput(Unit) {
          var total = Offset.Zero
          var start = latest
          var startH = 1f
          detectDragGestures(
            onDragStart = {
              total = Offset.Zero
              start = latest
              startH = fieldHeight.toFloat().coerceAtLeast(1f)
            }
          ) { change, delta ->
            change.consume()
            total += delta
            val k = scaleNow()
            val f = (1f + (total.x / (start.width * k) + total.y / startH) / 2f).coerceIn(0.2f, 6f)
            onChangeNow(start.copy(width = (start.width * f).coerceIn(MIN_TEXT_WIDTH, MAX_TEXT_WIDTH), fontSize = (start.fontSize * f).coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)))
          }
        }
        .graphicsLayer {
          shadowElevation = 3.dp.toPx()
          shape = CircleShape
          clip = true
        }
        .background(c.surface),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.OpenInFull, "Resize the text", tint = c.accent, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 90f })
    }
    // The bar sits just above the block, or below the toolbars if the block is near the top.
    val barY = with(density) { (at.y - 64.dp.toPx()).coerceAtLeast(84.dp.toPx()) }
    Box(Modifier.offset { IntOffset(at.x.roundToInt(), barY.roundToInt()) }.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
      FloatingBar(Modifier.height(48.dp), crown = CloudCrown.NONE) {
        SizeButton("A−") { onChange(edit.copy(fontSize = (edit.fontSize / 1.15f).coerceAtLeast(10f))) }
        SizeButton("A+") { onChange(edit.copy(fontSize = (edit.fontSize * 1.15f).coerceAtMost(160f))) }
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(c.line))
        val ring = rememberSlidingSelection()
        Row(Modifier.colorRing(ring, Color(edit.color)), verticalAlignment = Alignment.CenterVertically) {
          colors.take(6).forEach { col -> ColorDot(Color(col), selected = col == edit.color, size = 24.dp, selection = ring) { onChange(edit.copy(color = col)) } }
        }
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

/**
 * A side dot of the text box at [x], [y] (screen pixels): dragging it reshapes the block from how
 * it was when the drag began ([resize] gets that and how far the finger has gone).
 */
@Composable
private fun SideDot(x: Float, y: Float, latest: () -> TextEdit, change: (TextEdit) -> Unit, resize: (start: TextEdit, moved: Offset) -> TextEdit) {
  val c = Tali.colors
  Box(
    Modifier.offset { IntOffset((x - 16.dp.toPx()).roundToInt(), (y - 16.dp.toPx()).roundToInt()) }
      .size(32.dp)
      .pointerInput(Unit) { detectTapGestures { } }
      .pointerInput(Unit) {
        var total = Offset.Zero
        var start: TextEdit? = null
        detectDragGestures(onDragStart = {
          total = Offset.Zero
          start = latest()
        }) { ch, delta ->
          ch.consume()
          total += delta
          start?.let { change(resize(it, total)) }
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    Box(Modifier.size(13.dp).clip(CircleShape).background(Color.White).border(2.dp, c.accent, CircleShape))
  }
}

@Composable
private fun SizeButton(label: String, onClick: () -> Unit) {
  val c = Tali.colors
  Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = if (label == "A+") "Bigger text" else "Smaller text", onClick = onClick), contentAlignment = Alignment.Center) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink)
  }
}

/** How narrow and how wide a typed block can be dragged, in world units (A4 is 1322 wide). */
private const val MIN_TEXT_WIDTH = 60f
private const val MAX_TEXT_WIDTH = 2400f

/** The text sizes the resize chip and A−/A+ keep to. */
private const val MIN_TEXT_SIZE = 10f
private const val MAX_TEXT_SIZE = 160f
