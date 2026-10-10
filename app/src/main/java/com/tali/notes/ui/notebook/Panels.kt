package com.tali.notes.ui.notebook

import androidx.compose.ui.graphics.Brush
import android.content.res.Resources
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tali.notes.data.AppSettings
import com.tali.notes.ink.Brushes
import com.tali.notes.ink.StrokeFactory
import com.tali.notes.model.EraserMode
import com.tali.notes.model.CustomShape
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeGroup
import com.tali.notes.model.ShapeKind
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.ColorDot
import com.tali.notes.ui.components.StepSlider
import com.tali.notes.ui.components.PenGlyph
import com.tali.notes.ui.components.GeometryIcon
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.SectionLabel
import com.tali.notes.ui.components.Segmented
import com.tali.notes.ui.components.ShapeIcon
import com.tali.notes.ui.components.ToggleRow
import com.tali.notes.ui.components.dashedCircle
import com.tali.notes.ui.library.NameDialog
import com.tali.notes.ui.library.ConfirmDialog
import com.tali.notes.ui.components.combinedClickableCompat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.SolidColor
import com.tali.notes.ui.components.colorRing
import com.tali.notes.ui.components.rememberSlidingSelection
import com.tali.notes.ui.components.slidingHighlight
import com.tali.notes.ui.components.slidingOption
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect

@Composable
private fun PanelColumn(width: Int, content: @Composable () -> Unit) {
  // Never taller than the space under the toolbar, so the whole panel stays on screen.
  val maxHeight = (LocalConfiguration.current.screenHeightDp - 190).coerceIn(280, 640)
  val scroll = rememberScrollState()
  val c = Tali.colors
  // When there's more below, the first couple of times a panel opens (each time the app starts) it
  // peeks down a little and settles back, so she knows it scrolls. No popup.
  LaunchedEffect(Unit) {
    kotlinx.coroutines.delay(320)
    if (scroll.maxValue > 0 && panelPeeks < 2) {
      panelPeeks++
      val peek = minOf(scroll.maxValue, (36 * Resources.getSystem().displayMetrics.density).toInt())
      scroll.animateScrollTo(peek, tween(260, easing = com.tali.notes.ui.components.EaseOutStrong))
      scroll.animateScrollTo(0, spring(dampingRatio = 0.8f, stiffness = 320f))
    }
  }
  Box(Modifier.width(width.dp).heightIn(max = maxHeight.dp)) {
    Column(Modifier.verticalScroll(scroll)) { content() }
    // A soft fade at the bottom while there's more to scroll to.
    val more by androidx.compose.animation.core.animateFloatAsState(if (scroll.canScrollForward) 1f else 0f, tween(180), label = "panelMore")
    if (more > 0f) {
      Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(28.dp)
          .graphicsLayer { alpha = more }
          .background(Brush.verticalGradient(listOf(c.cloudBelly.copy(alpha = 0f), c.cloudBelly)))
      )
    }
  }
}

/** How many times a panel has peeked to show it scrolls, since the app started. */
private var panelPeeks = 0

@Composable
private fun PanelTitle(text: String) {
  Text(text, style = MaterialTheme.typography.titleLarge, color = Tali.colors.ink)
  Spacer(Modifier.height(14.dp))
}

@Composable
private fun TaliSlider(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
  val c = Tali.colors
  Slider(
    value = value,
    onValueChange = onChange,
    valueRange = range,
    modifier = modifier,
    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.sunken),
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PenPanel(settings: AppSettings, vm: NotebookViewModel) {
  val c = Tali.colors
  val slot = settings.activeSlot
  var picking by remember { mutableStateOf(false) }
  PanelColumn(392) {
    val types = rememberSlidingSelection()
    Row(Modifier.slidingHighlight(types, slot.type, SolidColor(c.accentSoft), 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      PenType.offered.forEach { type ->
        val ink by animateColorAsState(Color(if (type == slot.type) slot.color else 0xFF8E95A2.toInt()), tween(220), label = "penInk")
        Box(
          Modifier.slidingOption(types, type)
            .size(58.dp, 66.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClickLabel = type.label) { vm.setPenType(type) },
          contentAlignment = Alignment.BottomCenter,
        ) {
          PenGlyph(type, ink, Modifier.padding(bottom = 0.dp), line = if (type.takesLineStyle) slot.line else LineStyle.SOLID)
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(slot.type.label, style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
      // Only with a pen that has a button to press.
      if (vm.hasStylus) StarToggle(slot.starred, vm::toggleStar)
    }
    Spacer(Modifier.height(10.dp))
    StrokePreview(slot.type, slot.color, slot.size, slot.lineStyle, slot.square, slot.straight)
    Spacer(Modifier.height(12.dp))
    // Her three thicknesses (also beside the colours in the toolbar): pick one, then the slider sets it.
    Segmented(listOf(0, 1, 2), slot.sizeSlot.coerceIn(0, 2), { i -> String.format(java.util.Locale.US, "%.1f", slot.presetSizes[i]) }, vm::pickPenSize, Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Size", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(44.dp))
      StepSlider(slot.size, slot.type.minSize..slot.type.maxSize, 0.5f, { v -> vm.updateActivePen { it.withSize(v) } }, Modifier.weight(1f))
      Text(String.format(java.util.Locale.US, "%.1f", slot.size), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(40.dp).padding(start = 8.dp))
    }
    if (slot.type.takesLineStyle) {
      Spacer(Modifier.height(10.dp))
      SectionLabel("Line")
      Spacer(Modifier.height(8.dp))
      LineStylePicker(slot.lineStyle, vm::setPenLine)
    }
    if (slot.type.isHighlighter) {
      // The highlighter's own choices, in the same gliding switches as everywhere else.
      Spacer(Modifier.height(12.dp))
      SectionLabel("Draws")
      Spacer(Modifier.height(8.dp))
      Segmented(listOf(true, false), slot.straight, { if (it) "Straight lines" else "Freehand" }, { straight -> vm.updateActivePen { it.copy(freehand = !straight) } }, Modifier.fillMaxWidth())
      Spacer(Modifier.height(12.dp))
      SectionLabel("Tip")
      Spacer(Modifier.height(8.dp))
      Segmented(listOf(false, true), slot.square, { if (it) "Square" else "Round" }, { square -> vm.updateActivePen { it.copy(squareTip = square) } }, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    SectionLabel(if (slot.type.isHighlighter) "Highlighter colours" else "Colours")
    Spacer(Modifier.height(8.dp))
    ReorderableColors(
      colors = settings.paletteFor(slot),
      selected = slot.color,
      inToolbar = settings.toolbarColorsFor(slot),
      onPick = vm::pickPenColor,
      onReorder = vm::reorderColors,
      onRemove = vm::removeColor,
      onAdd = { picking = !picking },
    )
    Spacer(Modifier.height(12.dp))
    // The first few colours above sit in the toolbar beside the pens, for every notebook.
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text("In the toolbar with this pen", style = MaterialTheme.typography.bodyMedium, color = c.ink)
        Text("The colours before the line · hold one and drag to move it", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
      }
    }
    Spacer(Modifier.height(8.dp))
    Segmented((0..6).toList(), settings.toolbarColorsFor(slot), { if (it == 0) "None" else "$it" }, vm::setToolbarColors, Modifier.fillMaxWidth())
    if (picking) {
      Spacer(Modifier.height(12.dp))
      ColorPicker(
        initial = slot.color,
        onChange = { col -> vm.updateActivePen { it.copy(color = col) } },
        onSave = { col ->
          vm.saveColor(col)
          vm.pickPenColor(col)
          picking = false
        },
      )
    }
    Spacer(Modifier.height(14.dp))
    SectionLabel("This pen and other tools")
    Spacer(Modifier.height(4.dp))
    ToggleRow("Eraser can erase it", settings.eraserHits(slot.type), { vm.setEraserAffects(slot.type, it) })
    ToggleRow("Lasso can pick it up", settings.lassoHits(slot.type), { vm.setLassoAffects(slot.type, it) })
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      QuietButton("Add a pen", onClick = vm::addPen)
      if (settings.pens.size > 1) QuietButton("Remove this pen", color = c.danger, onClick = vm::removeActivePen)
    }
  }
}

/**
 * The pen's star: starred pens are the ones the S Pen button cycles through. It pops a little when
 * starred (from 85%, a quick spring) and its colour fades.
 */
@Composable
private fun StarToggle(on: Boolean, onToggle: () -> Unit) {
  val c = Tali.colors
  val pop = remember { androidx.compose.animation.core.Animatable(1f) }
  var first by remember { mutableStateOf(true) }
  LaunchedEffect(on) {
    if (first) { first = false; return@LaunchedEffect }
    if (on) {
      pop.snapTo(0.85f)
      pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 900f))
    }
  }
  val tint by animateColorAsState(if (on) STAR_GOLD else c.inkFaint, tween(200), label = "star")
  Box(
    Modifier.size(40.dp).clip(CircleShape)
      .clickable(role = Role.Switch, onClickLabel = if (on) "Unstar this pen" else "Star this pen", onClick = onToggle),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      if (on) Icons.Filled.Star else Icons.Outlined.StarBorder,
      if (on) "Starred: the S Pen button switches to it" else "Not starred",
      tint = tint,
      modifier = Modifier.size(24.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value },
    )
  }
}

private val STAR_GOLD = Color(0xFFF5B83D)

/** Draws a sample stroke with the real Ink brush, so size and style changes show exactly as they'll look. */
@Composable
fun StrokePreview(type: PenType, color: Int, size: Float, line: LineStyle = LineStyle.SOLID, square: Boolean = false, straight: Boolean = false) {
  val c = Tali.colors
  val renderer = remember { Brushes.renderer() }
  val w = 392f
  val h = 70f
  val stroke =
    remember(type, color, size, line, square, straight) {
      val brush = Brushes.brush(type, color, size, line, square)
      // A straight-line highlighter shows a straight stroke across the middle.
      if (straight) StrokeFactory.strokeAlong(listOf(com.tali.notes.geometry.Pt(w * 0.12f, h / 2), com.tali.notes.geometry.Pt(w * 0.88f, h / 2)), brush)
      else StrokeFactory.previewStroke(brush, w, h)
    }
  Canvas(Modifier.fillMaxWidth().height(h.dp).clip(RoundedCornerShape(14.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(14.dp))) {
    drawIntoCanvas { canvas ->
      val nc = canvas.nativeCanvas
      val s = this.size.width / w
      nc.save()
      nc.scale(s, s)
      renderer.draw(nc, stroke, Matrix().apply { setScale(s, s) })
      nc.restore()
    }
  }
}

@Composable
fun EraserPanel(settings: AppSettings, vm: NotebookViewModel) {
  val c = Tali.colors
  PanelColumn(340) {
    PanelTitle("Eraser")
    Segmented(listOf(EraserMode.STROKE, EraserMode.AREA), settings.eraserMode, { if (it == EraserMode.STROKE) "Whole strokes" else "Precise" }, vm::setEraserMode, Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Size", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(44.dp))
      StepSlider(settings.eraserSize, 6f..64f, 1f, vm::setEraserSize, Modifier.weight(1f))
      Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size((settings.eraserSize.coerceAtMost(40f)).dp)) {
          drawCircle(c.sunken)
          drawCircle(c.inkFaint, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    SectionLabel("Erases")
    Spacer(Modifier.height(4.dp))
    Spacer(Modifier.height(4.dp))
    FilterChips {
      PenType.offered.forEach { type -> FilterChip(type.label, settings.eraserHits(type)) { vm.setEraserAffects(type, it) } }
    }
    Spacer(Modifier.height(14.dp))
    Text("Tip: hold the S Pen button to erase with any tool.", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(content: @Composable () -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** A compact on/off chip: blue with a tick when on, quiet outline when off. */
@Composable
private fun FilterChip(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
  val c = Tali.colors
  val shape = RoundedCornerShape(12.dp)
  val wash by animateColorAsState(if (on) c.blueSoft else Color.Transparent, tween(200), label = "chipWash")
  val edge by animateColorAsState(if (on) c.sky else c.line, tween(200), label = "chipEdge")
  val ink by animateColorAsState(if (on) c.blue else c.inkMuted, tween(200), label = "chipInk")
  Row(
    Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
      .clip(shape)
      .background(wash)
      .border(1.dp, edge, shape)
      .clickable(role = Role.Checkbox, onClickLabel = label) { onChange(!on) }
      .padding(start = if (on) 8.dp else 12.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (on) {
      Icon(Icons.Outlined.Check, null, tint = c.blue, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(4.dp))
    }
    Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
  }
}

/**
 * The fill tool: a colour (or "None" to take fills away) and how strongly it covers the paper.
 * Tap inside a closed shape to fill the shape; tap inside an area drawn around by hand to fill it
 * behind the writing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FillPanel(settings: AppSettings, vm: NotebookViewModel) {
  val c = Tali.colors
  PanelColumn(360) {
    PanelTitle("Fill")
    Text("Tap inside a shape, or inside an area you've drawn around, to fill it with colour. Your writing stays on top.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))
    SectionLabel("Colour")
    Spacer(Modifier.height(8.dp))
    val ring = rememberSlidingSelection()
    FlowRow(Modifier.colorRing(ring, settings.fillColor?.let { Color(it) } ?: NO_FILL), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      settings.savedColors.forEach { col -> ColorDot(Color(col), selected = col == settings.fillColor, selection = ring) { vm.setFillColor(col) } }
      // No fill: taps take a fill away.
      Box(
        Modifier.slidingOption(ring, NO_FILL).size(38.dp).clip(CircleShape)
          .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClickLabel = "No fill") { vm.setFillColor(null) },
        contentAlignment = Alignment.Center,
      ) {
        Canvas(Modifier.size(30.dp)) {
          drawCircle(c.surface)
          drawCircle(c.line, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
          drawLine(c.danger, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), 2.dp.toPx())
        }
      }
    }
    if (settings.fillColor != null) {
      Spacer(Modifier.height(12.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Opacity", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(64.dp))
        TaliSlider(settings.fillOpacity, 0.15f..1f, { vm.setFillOpacity(Math.round(it * 20) / 20f) }, Modifier.weight(1f))
        Text("${Math.round(settings.fillOpacity * 100)}%", style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(48.dp).padding(start = 8.dp))
      }
    } else {
      Spacer(Modifier.height(10.dp))
      Text("Tap a filled shape or area to take its fill away.", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
    }
  }
}

/** Stands for "no fill" in the colour ring (a colour no swatch uses). */
private val NO_FILL = Color(0x00FF00FF)

@Composable
fun LassoPanel(settings: AppSettings, vm: NotebookViewModel) {
  val c = Tali.colors
  PanelColumn(340) {
    PanelTitle("Select")
    // The classic lasso loop, or free form: trace over or touch things (circling still works).
    Segmented(listOf(false, true), settings.lassoFree, { if (it) "Free form" else "Lasso" }, vm::setLassoFree, Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Text(
      when {
        settings.lassoShot -> "Draw around anything to take a picture of it: save it to your gallery, share it or copy it."
        settings.lassoFree -> "Trace over or touch anything to pick it up, or circle a group. Then move, resize, recolour or copy it."
        else -> "Circle anything to move, resize, recolour or copy it. Tap a single item with the lasso to pick just that one."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = c.inkMuted,
    )
    Spacer(Modifier.height(6.dp))
    ToggleRow("Screenshot", settings.lassoShot, vm::setLassoShot, detail = "The lasso takes a picture of the rectangle around what you draw, instead of picking it up.")
    Spacer(Modifier.height(14.dp))
    SectionLabel("Picks up")
    Spacer(Modifier.height(4.dp))
    Spacer(Modifier.height(4.dp))
    FilterChips {
      PenType.offered.forEach { type -> FilterChip(type.label, settings.lassoHits(type)) { vm.setLassoAffects(type, it) } }
      FilterChip("Images", settings.lassoSelectsImages, vm::setLassoImages)
    }
    Spacer(Modifier.height(10.dp))
    ToggleRow("Pick up locked items", settings.lassoLocked, vm::setLassoLocked, detail = "To move them or unlock them. Off, the lasso leaves locked items alone.")
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShapesPanel(settings: AppSettings, current: ShapeKind, vm: NotebookViewModel) {
  val c = Tali.colors
  // Two pages: the shapes to draw, and how they're drawn (kept while the app is open).
  var styling by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
  PanelColumn(588) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Shapes", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.weight(1f))
      Segmented(listOf(false, true), styling, { if (it) "Style" else "Choose" }, { styling = it }, Modifier.width(220.dp))
    }
    Spacer(Modifier.height(4.dp))
    if (styling) {
      ShapeStylePicker(settings, vm)
    } else {
      ShapeGroup.entries.forEach { group ->
        Spacer(Modifier.height(12.dp))
        SectionLabel(group.label)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          ShapeKind.offered.filter { it.group == group }.forEach { kind ->
            val on = kind == current && vm.customShapeId == null && vm.tool == com.tali.notes.model.Tool.SHAPE
            ShapeTile(kind.label, on, onClick = { vm.pickShape(kind) }) { tint -> ShapeIcon(kind, tint, size = 30.dp) }
          }
        }
      }
      if (current == ShapeKind.PART_CIRCLE && vm.customShapeId == null) {
        Spacer(Modifier.height(10.dp))
        Text("Part circle: drag from the centre outwards; it starts as a half. Then drag an end dot to make it any % of a circle.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
      }
      MyShapes(settings.customShapes, vm)
    }
  }
}

/**
 * The Shapes tool's own look, apart from the pen she writes with, in two columns that fill the
 * panel (so it all fits a landscape tablet without scrolling): a preview, the outline colours and
 * the fill colours on the left; size, the two opacities (the outline's and the fill's, the shape's
 * own, not the fill tool's), the line style and the shape switches on the right.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShapeStylePicker(settings: AppSettings, vm: NotebookViewModel) {
  val c = Tali.colors
  val look = settings.shapeLook
  val outline = look.color or (0xFF shl 24)
  val fill = look.fill?.let { it or (0xFF shl 24) }
  // Following the pen, its colour, size and line come from the pen in use: those choices rest.
  val own = !settings.shapeFollowsPen
  val resting = Modifier.graphicsLayer { alpha = if (own) 1f else 0.4f }
  Spacer(Modifier.height(12.dp))
  Row(verticalAlignment = Alignment.Top) {
    // Left: how it looks, and its colours.
    Column(Modifier.weight(1f)) {
      ShapeStylePreview(look, Modifier.fillMaxWidth().height(88.dp))
      Spacer(Modifier.height(12.dp))
      SectionLabel("Outline colour")
      Spacer(Modifier.height(6.dp))
      val ring = rememberSlidingSelection()
      FlowRow(resting.colorRing(ring, Color(outline)), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        settings.savedColors.forEach { col -> ColorDot(Color(col), selected = (col or (0xFF shl 24)) == outline, size = 26.dp, selection = ring) { if (own) vm.updateShapeStyle { it.copy(color = col or (0xFF shl 24)) } } }
      }
      Spacer(Modifier.height(10.dp))
      SectionLabel("Fill")
      Spacer(Modifier.height(6.dp))
      val fillRing = rememberSlidingSelection()
      FlowRow(Modifier.colorRing(fillRing, fill?.let { Color(it) } ?: NO_FILL), horizontalArrangement = Arrangement.spacedBy(1.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        NoneDot(fillRing, "No fill", dot = 34.dp) { vm.updateShapeStyle { it.copy(fill = null) } }
        settings.savedColors.forEach { col -> ColorDot(Color(col), selected = (col or (0xFF shl 24)) == fill, size = 26.dp, selection = fillRing) { vm.updateShapeStyle { it.copy(fill = col or (0xFF shl 24)) } } }
      }
    }
    Spacer(Modifier.width(20.dp))
    // Right: thickness, opacities, line style, then the switches.
    Column(Modifier.weight(1.15f)) {
      // Its own style stays the same whatever pen she picks; or it can follow the pen in use.
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Style", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(84.dp))
        Segmented(listOf(false, true), settings.shapeFollowsPen, { if (it) "Like the pen" else "Its own" }, vm::setShapeFollowsPen, Modifier.weight(1f))
      }
      Spacer(Modifier.height(6.dp))
      Row(resting, verticalAlignment = Alignment.CenterVertically) {
        Text("Size", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(84.dp))
        StepSlider(look.size, com.tali.notes.model.ShapeStyle.MIN_SIZE..com.tali.notes.model.ShapeStyle.MAX_SIZE, 0.5f, { v -> if (own) vm.updateShapeStyle { it.copy(size = v) } }, Modifier.weight(1f))
        Text(String.format(java.util.Locale.US, "%.1f", look.size), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(48.dp).padding(start = 8.dp))
      }
      OpacitySlider("Opacity", look.opacity, enabled = true) { v -> vm.updateShapeStyle { it.copy(opacity = v) } }
      OpacitySlider("Fill opacity", look.fillOpacity, enabled = fill != null) { v -> vm.updateShapeStyle { it.copy(fillOpacity = v) } }
      Spacer(Modifier.height(4.dp))
      CompactLineStylePicker(look.line, { l -> if (own) vm.updateShapeStyle { it.copy(line = l) } }, resting.fillMaxWidth())
      Spacer(Modifier.height(8.dp))
      ToggleRow("Snap to other shapes", settings.snapShapes, vm::setSnapShapes, detail = "Ends and edges lock on; shapes line up.")
      ToggleRow("Hold to perfect shapes", settings.holdToShape, vm::setHoldToShape, detail = "Pause at the end of a pen stroke.")
    }
  }
}

/** An opacity from 10% to 100% in 5% steps, with its percentage; faded while it has nothing to act on. */
@Composable
private fun OpacitySlider(label: String, value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
  val c = Tali.colors
  Row(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.4f }, verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(84.dp))
    Slider(
      value = value,
      onValueChange = { onChange((Math.round(it * 20) / 20f).coerceIn(com.tali.notes.model.ShapeStyle.MIN_OPACITY, 1f)) },
      valueRange = com.tali.notes.model.ShapeStyle.MIN_OPACITY..1f,
      enabled = enabled,
      colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.sunken),
      modifier = Modifier.weight(1f),
    )
    Text("${Math.round(value * 100)}%", style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(48.dp).padding(start = 8.dp))
  }
}

/** A box and an arrow drawn in the shape style (opacities too), so each change shows at once. */
@Composable
private fun ShapeStylePreview(look: com.tali.notes.model.ShapeStyle, modifier: Modifier) {
  val c = Tali.colors
  Canvas(modifier.clip(RoundedCornerShape(14.dp)).background(c.paper).border(1.dp, c.line, RoundedCornerShape(14.dp))) {
    val w = (look.size * density * 0.9f).coerceAtLeast(1f)
    val pattern = look.line.pattern
    val effect = if (pattern.isEmpty()) null else PathEffect.dashPathEffect(pattern.mapIndexed { i, v -> if (i % 2 == 0) maxOf((v - 1f) * w, 0.01f) else (v + 1f) * w }.toFloatArray())
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(w, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round, pathEffect = effect)
    val ink = Color(look.color or (0xFF shl 24)).copy(alpha = look.opacity)
    val box = androidx.compose.ui.geometry.Rect(size.width * 0.1f, size.height * 0.2f, size.width * 0.52f, size.height * 0.8f)
    look.fill?.let { drawRect(Color(it or (0xFF shl 24)).copy(alpha = look.fillOpacity), box.topLeft, box.size) }
    drawRect(ink, box.topLeft, box.size, style = stroke)
    val a = Offset(size.width * 0.62f, size.height * 0.74f)
    val b = Offset(size.width * 0.9f, size.height * 0.26f)
    drawLine(ink, a, b, w, cap = androidx.compose.ui.graphics.StrokeCap.Round, pathEffect = effect)
    // The arrowhead stays solid, as on the page.
    val dir = (b - a) / (b - a).getDistance()
    val head = maxOf(10.dp.toPx(), w * 3.5f)
    fun barb(turn: Float) = Offset(dir.x * kotlin.math.cos(turn) - dir.y * kotlin.math.sin(turn), dir.x * kotlin.math.sin(turn) + dir.y * kotlin.math.cos(turn)) * -head
    drawLine(ink, b, b + barb(0.52f), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    drawLine(ink, b, b + barb(-0.52f), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
  }
}

/** The "none" choice in a colour ring: an empty circle with a red slash. */
@Composable
private fun NoneDot(ring: com.tali.notes.ui.components.SlidingSelection, label: String, dot: androidx.compose.ui.unit.Dp = 38.dp, onClick: () -> Unit) {
  val c = Tali.colors
  Box(
    Modifier.slidingOption(ring, NO_FILL).size(dot).clip(CircleShape)
      .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.size(dot - 8.dp)) {
      drawCircle(c.surface)
      drawCircle(c.line, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
      drawLine(c.danger, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.78f, size.height * 0.22f), 2.dp.toPx())
    }
  }
}

/** Her own saved shapes: pick one to draw it, press and hold for rename and delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyShapes(shapes: List<CustomShape>, vm: NotebookViewModel) {
  val c = Tali.colors
  var menuFor by remember { mutableStateOf<String?>(null) }
  var renaming by remember { mutableStateOf<CustomShape?>(null) }
  var deleting by remember { mutableStateOf<CustomShape?>(null) }
  Spacer(Modifier.height(12.dp))
  SectionLabel("My shapes")
  Spacer(Modifier.height(8.dp))
  if (shapes.isEmpty()) {
    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.blueSoft.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.AutoAwesome, null, tint = c.blue, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Text(
        "Draw a shape you use a lot, circle it with the lasso and tap “Save as shape”. Holding the pen at the end of a stroke will then snap to it too.",
        style = MaterialTheme.typography.bodySmall,
        color = c.inkMuted,
      )
    }
    return
  }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    shapes.forEach { shape ->
      val on = vm.customShapeId == shape.id && vm.tool == com.tali.notes.model.Tool.SHAPE
      Box {
        ShapeTile(shape.name, on, onClick = { vm.pickCustomShape(shape) }, onLongClick = { menuFor = shape.id }) { tint -> GeometryIcon(shape.geometry, tint, size = 30.dp) }
        DropdownMenu(expanded = menuFor == shape.id, onDismissRequest = { menuFor = null }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
          DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menuFor = null; renaming = shape })
          DropdownMenuItem(text = { Text("Delete", color = c.danger) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = c.danger) }, onClick = { menuFor = null; deleting = shape })
        }
      }
    }
  }
  Spacer(Modifier.height(6.dp))
  Text("Press and hold a shape to rename or delete it.", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
  renaming?.let { shape ->
    NameDialog("Rename shape", shape.name, "Shape name", "Rename", onDismiss = { renaming = null }) {
      vm.renameCustomShape(shape.id, it)
      renaming = null
    }
  }
  deleting?.let { shape ->
    ConfirmDialog("Delete “${shape.name}”?", "It will no longer be offered here or recognised when you hold the pen. Shapes already on your pages stay.", "Delete", destructive = true, onDismiss = { deleting = null }) {
      vm.deleteCustomShape(shape.id)
      deleting = null
    }
  }
}

@Composable
private fun ShapeTile(label: String, on: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, icon: @Composable (Color) -> Unit) {
  val c = Tali.colors
  // The tiles are cards of their own, so the choice fades across rather than gliding under them.
  val wash by animateColorAsState(if (on) c.blueSoft else c.background, tween(220), label = "tileWash")
  val ink by animateColorAsState(if (on) c.blue else c.ink, tween(220), label = "tileInk")
  val label2 by animateColorAsState(if (on) c.blue else c.inkMuted, tween(220), label = "tileLabel")
  Column(
    Modifier.width(93.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(wash)
      .combinedClickableCompat(onClick = onClick, onLongClick = onLongClick)
      .padding(vertical = 8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    icon(ink)
    Spacer(Modifier.height(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = label2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
  }
}

/**
 * Her saved colours. Tap one to use it; hold one and drag it to a new place (the others make room
 * as it passes); hold one without moving for "Remove colour". A thin line after the first
 * [inToolbar] sets apart the ones beside the pens in the toolbar (clear of the dots and their ring).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReorderableColors(
  colors: List<Int>,
  selected: Int,
  inToolbar: Int,
  onPick: (Int) -> Unit,
  onReorder: (List<Int>) -> Unit,
  onRemove: (Int) -> Unit,
  onAdd: () -> Unit,
) {
  val c = Tali.colors
  val ring = rememberSlidingSelection()
  // While dragging, the order shown follows the finger; it's saved when she lets go.
  var order by remember { mutableStateOf(colors) }
  var dragging by remember { mutableStateOf<Int?>(null) }
  var dragAt by remember { mutableStateOf(Offset.Zero) }
  var removeTarget by remember { mutableStateOf<Int?>(null) }
  if (dragging == null && order != colors) order = colors
  val centres = remember { HashMap<Int, Offset>() }
  FlowRow(Modifier.colorRing(ring, Color(selected)), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
    order.forEachIndexed { i, col ->
      // Keyed by colour, so a dot keeps its drag going as it moves to a new place.
      key(col) {
      val lifted = dragging == col
      val scale by androidx.compose.animation.core.animateFloatAsState(if (lifted) 1.18f else 1f, tween(160), label = "colourLift")
      Box(
        Modifier
          .zIndex(if (lifted) 1f else 0f)
          .onGloballyPositioned { centres[col] = it.boundsInRoot().center }
          .graphicsLayer {
            if (lifted) {
              val home = centres[col] ?: dragAt
              translationX = dragAt.x - home.x
              translationY = dragAt.y - home.y
            }
            scaleX = scale
            scaleY = scale
          }
          .pointerInput(col) {
            var moved = false
            detectDragGesturesAfterLongPress(
              onDragStart = {
                moved = false
                dragging = col
                dragAt = centres[col] ?: Offset.Zero
              },
              onDrag = { change, amount ->
                change.consume()
                moved = moved || amount.getDistance() > 0.5f
                dragAt += amount
                // Make room: take the place of whichever colour the finger is now over.
                val over = order.minByOrNull { other -> centres[other]?.let { (it - dragAt).getDistance() } ?: Float.MAX_VALUE }
                if (over != null && over != col && (centres[over]!! - dragAt).getDistance() < 24.dp.toPx()) {
                  val list = order.toMutableList()
                  list.remove(col)
                  list.add(order.indexOf(over), col)
                  order = list
                }
              },
              onDragEnd = {
                dragging = null
                if (moved) onReorder(order) else removeTarget = col
              },
              onDragCancel = {
                dragging = null
                order = colors
              },
            )
          },
        contentAlignment = Alignment.Center,
      ) {
        ColorDot(Color(col), selected = col == selected, selection = ring) { onPick(col) }
        DropdownMenu(expanded = removeTarget == col, onDismissRequest = { removeTarget = null }) {
          DropdownMenuItem(text = { Text("Remove colour") }, onClick = {
            onRemove(col)
            removeTarget = null
          })
        }
      }
      }
      // The line after the last colour in the toolbar.
      if (i == inToolbar - 1 && i < order.size - 1) {
        key("toolbar-line") {
          Box(Modifier.size(8.dp, 38.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(2.dp, 24.dp).clip(RoundedCornerShape(1.dp)).background(c.accent.copy(alpha = 0.45f)))
          }
        }
      }
    }
    Box(
      Modifier.size(38.dp).clip(CircleShape).clickable(onClickLabel = "Add a colour", onClick = onAdd),
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.size(30.dp)) { dashedCircle(c.inkFaint, size.width / 2 - 2f) }
      Icon(Icons.Outlined.Add, null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
    }
  }
}
