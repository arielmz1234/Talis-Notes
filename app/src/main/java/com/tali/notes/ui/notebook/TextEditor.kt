package com.tali.notes.ui.notebook

import androidx.compose.ui.res.stringResource
import com.tali.notes.R
import androidx.compose.material.icons.outlined.OpenWith

import androidx.compose.ui.layout.layout

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
 * Typing on the page: a text box laid exactly over the text block (it follows the page when it
 * scrolls or zooms), with a small bar above it for size, colour, delete and done.
 *
 * It acts like a text box: the box has its own size, set by the dots on its sides and the chip at
 * its corner (the text wraps at its width; the box grows if the text needs more room), apart from
 * the text's size (A− / A+). Dragging its frame or the move chip moves it, anywhere, on or off the
 * paper. Tapping elsewhere finishes; tapping another text box opens that one.
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
  // A new box opens for typing; an existing one is first just picked up (to move or size it), and
  // a second tap on it brings the keyboard.
  var typing by remember(edit.id) { mutableStateOf(edit.id == null) }
  LaunchedEffect(edit.id, typing) {
    if (typing) {
      // Picked up first and moved, it may now be where the keyboard comes: bring it into view.
      if (edit.id != null) canvas.runSafely { showForTyping(edit.origin.y) }
      focus.requestFocus()
      keyboard?.show()
    }
  }
  // Read on every camera move (the tick changes), so the field stays over its block.
  val scale = if (cameraTick >= 0) canvas.pixelsPerUnit else 1f
  // How tall the typed text is, in screen pixels.
  var textHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
  // Drags outlive recompositions: they work on the box as it is now.
  val latest by androidx.compose.runtime.rememberUpdatedState(edit)
  val onChangeNow by androidx.compose.runtime.rememberUpdatedState(onChange)
  val scaleNow: () -> Float = { canvas.pixelsPerUnit }
  // The box's content height in world units: what she set, or the text's own if that's taller.
  val contentNow: (TextEdit) -> Float = { e -> maxOf(e.height, textHeight / scaleNow(), e.fontSize * TextItem.LINE) }
  val at = canvas.screenOf(edit.origin)
  val pad = with(density) { 6.dp.toPx() }
  val w = edit.width * scale
  val minH = edit.height * scale
  val fontSp = with(density) { (edit.fontSize * scale).toSp() }
  val lineSp = with(density) { (edit.fontSize * scale * TextItem.LINE).toSp() }
  val ink = Color(if (darkPaper) Brushes.onDarkPaper(edit.color) else edit.color)
  val textNow by androidx.compose.runtime.rememberUpdatedState(value.text)
  val onDoneNow by androidx.compose.runtime.rememberUpdatedState(onDone)
  val finish = {
    keyboard?.hide()
    onDoneNow(textNow)
  }
  androidx.activity.compose.BackHandler(onBack = finish)
  val boxLeft = at.x - pad
  val boxTop = at.y - pad
  val boxW = w + 2 * pad
  val boxH = maxOf(textHeight.toFloat(), minH, edit.fontSize * scale * TextItem.LINE) + 2 * pad
  Box(
    Modifier.fillMaxSize().pointerInput(canvas) {
      detectTapGestures { tap ->
        finish()
        // Tapping another text box goes straight on to that one.
        canvas.runSafely { textAt(worldOf(tap.x, tap.y))?.let { editText(it.id) } }
      }
    }
  ) {
    // Its frame: a band round the box that moves it (the text inside is for typing).
    val band = with(density) { 14.dp.toPx() }
    Box(
      Modifier.placedAt({ boxLeft - band }, { boxTop - band })
        .size(with(density) { (boxW + 2 * band).toDp() }, with(density) { (boxH + 2 * band).toDp() })
        .moves(latest = { latest }, scale = scaleNow, change = { onChangeNow(it) })
    )
    BasicTextField(
      value,
      { value = it },
      textStyle = TextStyle(color = ink, fontSize = fontSp, lineHeight = lineSp, fontFamily = FontFamily.SansSerif, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
      cursorBrush = SolidColor(c.accent),
      onTextLayout = { textHeight = kotlin.math.ceil(it.multiParagraph.height).toInt() },
      modifier =
        Modifier.placedAt({ boxLeft }, { boxTop })
          .width(with(density) { boxW.toDp() })
          .height(with(density) { boxH.toDp() })
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
      readOnly = !typing,
    )
    // Picked up, not yet typing: the whole box moves when dragged, and a tap starts typing.
    if (!typing) {
      Box(
        Modifier.placedAt({ boxLeft }, { boxTop })
          .size(with(density) { boxW.toDp() }, with(density) { boxH.toDp() })
          .moves(latest = { latest }, scale = scaleNow, change = { onChangeNow(it) }, onTap = { typing = true })
      )
    }
    // Sizing the box, like a rectangle: a dot in the middle of each side moves that side (left and
    // right set the width, top and bottom the height), the opposite side staying put; the chip at the
    // corner moves the corner. The text keeps its size. In world units, so it's the same at any zoom.
    SideDot(boxLeft, boxTop + boxH / 2, { latest }, onChangeNow) { start, d ->
      val nw = (start.width - d.x / scaleNow()).coerceIn(MIN_TEXT_WIDTH, MAX_TEXT_WIDTH)
      start.copy(width = nw, origin = com.tali.notes.geometry.Pt(start.origin.x + (start.width - nw), start.origin.y))
    }
    SideDot(boxLeft + boxW, boxTop + boxH / 2, { latest }, onChangeNow) { start, d ->
      start.copy(width = (start.width + d.x / scaleNow()).coerceIn(MIN_TEXT_WIDTH, MAX_TEXT_WIDTH))
    }
    SideDot(boxLeft + boxW / 2, boxTop, { latest }, onChangeNow) { start, d ->
      val was = contentNow(start)
      // Never smaller than the text in it: the bottom stays where it was.
      val now = (was - d.y / scaleNow()).coerceIn(textHeight / scaleNow(), MAX_TEXT_HEIGHT)
      start.copy(height = now, origin = com.tali.notes.geometry.Pt(start.origin.x, start.origin.y + (was - now)))
    }
    SideDot(boxLeft + boxW / 2, boxTop + boxH, { latest }, onChangeNow) { start, d ->
      start.copy(height = (contentNow(start) + d.y / scaleNow()).coerceIn(0f, MAX_TEXT_HEIGHT))
    }
    val chip = with(density) { 30.dp.toPx() }
    val chipGap = with(density) { 10.dp.toPx() }
    // The resize chip at the bottom-right corner: the corner follows the finger.
    EditorChip(Icons.Outlined.OpenInFull, stringResource(R.string.resize_the_text_box), { boxLeft + boxW + chipGap - chip / 2 }, { boxTop + boxH + chipGap - chip / 2 }, rotate = 90f) {
      var total = Offset.Zero
      var start = latest
      var startContent = 0f
      detectDragGestures(
        onDragStart = {
          total = Offset.Zero
          start = latest
          startContent = contentNow(start)
        }
      ) { change, delta ->
        change.consume()
        total += delta
        val k = scaleNow()
        onChangeNow(start.copy(width = (start.width + total.x / k).coerceIn(MIN_TEXT_WIDTH, MAX_TEXT_WIDTH), height = (startContent + total.y / k).coerceIn(0f, MAX_TEXT_HEIGHT)))
      }
    }
    // The move chip at the bottom-left corner: drags the whole box.
    EditorChip(Icons.Outlined.OpenWith, stringResource(R.string.move_the_text_box), { boxLeft - chipGap - chip / 2 }, { boxTop + boxH + chipGap - chip / 2 }) {
      var start = latest
      var total = Offset.Zero
      detectDragGestures(onDragStart = {
        start = latest
        total = Offset.Zero
      }) { change, delta ->
        change.consume()
        total += delta
        val k = scaleNow()
        onChangeNow(start.copy(origin = com.tali.notes.geometry.Pt(start.origin.x + total.x / k, start.origin.y + total.y / k)))
      }
    }
    // The bar sits just above the box, or below the toolbars if the box is near the top.
    val barY = with(density) { (boxTop - 60.dp.toPx()).coerceAtLeast(84.dp.toPx()) }
    Box(Modifier.placedAt({ boxLeft.coerceAtLeast(with(density) { 8.dp.toPx() }) }, { barY }).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
      FloatingBar(Modifier.height(48.dp), crown = CloudCrown.NONE) {
        SizeButton("A−") { onChange(edit.copy(fontSize = (edit.fontSize / 1.15f).coerceAtLeast(MIN_TEXT_SIZE))) }
        SizeButton("A+") { onChange(edit.copy(fontSize = (edit.fontSize * 1.15f).coerceAtMost(MAX_TEXT_SIZE))) }
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(c.line))
        val ring = rememberSlidingSelection()
        Row(Modifier.colorRing(ring, Color(edit.color)), verticalAlignment = Alignment.CenterVertically) {
          colors.take(6).forEach { col -> ColorDot(Color(col), selected = col == edit.color, size = 24.dp, selection = ring) { onChange(edit.copy(color = col)) } }
        }
        Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(24.dp).background(c.line))
        ToolButton(Icons.Outlined.DeleteOutline, stringResource(R.string.delete_text)) {
          keyboard?.hide()
          onDone("")
        }
        ToolButton(Icons.Outlined.Check, stringResource(R.string.done), onClick = finish)
      }
    }
  }
}

/**
 * Places this at [x], [y] (screen pixels, read at placement) at its own size, even past the
 * screen's edges: a box partly off screen keeps its width instead of being squeezed.
 */
private fun Modifier.placedAt(x: () -> Float, y: () -> Float) =
  layout { m, cs ->
    val p = m.measure(androidx.compose.ui.unit.Constraints())
    layout(cs.maxWidth, cs.maxHeight) { p.place(x().roundToInt(), y().roundToInt()) }
  }

/** Dragging this moves the text box. */
private fun Modifier.moves(latest: () -> TextEdit, scale: () -> Float, change: (TextEdit) -> Unit, onTap: () -> Unit = {}) =
  pointerInput(Unit) { detectTapGestures { onTap() } }.pointerInput(Unit) {
    var start = latest()
    var total = Offset.Zero
    detectDragGestures(onDragStart = {
      start = latest()
      total = Offset.Zero
    }) { ch, delta ->
      ch.consume()
      total += delta
      val k = scale()
      change(start.copy(origin = com.tali.notes.geometry.Pt(start.origin.x + total.x / k, start.origin.y + total.y / k)))
    }
  }

/** A round white chip by the box's corner, with its own drag. */
@Composable
private fun EditorChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, x: () -> Float, y: () -> Float, rotate: Float = 0f, drag: suspend androidx.compose.ui.input.pointer.PointerInputScope.() -> Unit) {
  val c = Tali.colors
  Box(
    Modifier.placedAt(x, y)
      .size(30.dp)
      .pointerInput(Unit) { detectTapGestures { } }
      .pointerInput(Unit, drag)
      .graphicsLayer {
        shadowElevation = 3.dp.toPx()
        shape = CircleShape
        clip = true
      }
      .background(c.surface),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, label, tint = c.accent, modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = rotate })
  }
}

/**
 * A side dot of the text box at [x], [y] (screen pixels): dragging it reshapes the box from how
 * it was when the drag began ([resize] gets that and how far the finger has gone).
 */
@Composable
private fun SideDot(x: Float, y: Float, latest: () -> TextEdit, change: (TextEdit) -> Unit, resize: (start: TextEdit, moved: Offset) -> TextEdit) {
  val c = Tali.colors
  val xs by androidx.compose.runtime.rememberUpdatedState(x)
  val ys by androidx.compose.runtime.rememberUpdatedState(y)
  val half = with(LocalDensity.current) { 16.dp.toPx() }
  Box(
    Modifier.placedAt({ xs - half }, { ys - half })
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
  Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = if (label == "A+") stringResource(R.string.bigger_text) else stringResource(R.string.smaller_text), onClick = onClick), contentAlignment = Alignment.Center) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = c.ink)
  }
}

/** How narrow and how wide a typed block can be dragged, in world units (A4 is 1322 wide). */
private const val MIN_TEXT_WIDTH = 60f
private const val MAX_TEXT_WIDTH = 2400f

/** The text sizes A−/A+ keep to. */
private const val MIN_TEXT_SIZE = 10f
private const val MAX_TEXT_SIZE = 160f

/** How tall a text box can be set, in world units (an A4 page is 1871 tall). */
private const val MAX_TEXT_HEIGHT = 4000f
