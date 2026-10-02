package com.tali.notes.ui.notebook

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

@Composable
private fun PanelColumn(width: Int, content: @Composable () -> Unit) {
  // Never taller than the space under the toolbar, so the whole panel stays on screen.
  val maxHeight = (LocalConfiguration.current.screenHeightDp - 190).coerceIn(280, 640)
  Column(Modifier.width(width.dp).heightIn(max = maxHeight.dp).verticalScroll(rememberScrollState())) { content() }
}

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
  var removeTarget by remember { mutableStateOf<Int?>(null) }
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
    Text(slot.type.label, style = MaterialTheme.typography.titleMedium, color = c.ink)
    Spacer(Modifier.height(10.dp))
    StrokePreview(slot.type, slot.color, slot.size, slot.lineStyle)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("Size", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(44.dp))
      TaliSlider(slot.size, slot.type.minSize..slot.type.maxSize, { v -> vm.updateActivePen { it.copy(size = v) } }, Modifier.weight(1f))
      Text(String.format("%.1f", slot.size), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(40.dp).padding(start = 8.dp))
    }
    if (slot.type.takesLineStyle) {
      Spacer(Modifier.height(10.dp))
      SectionLabel("Line")
      Spacer(Modifier.height(8.dp))
      LineStylePicker(slot.lineStyle, vm::setPenLine)
    }
    Spacer(Modifier.height(10.dp))
    SectionLabel("Colours")
    Spacer(Modifier.height(8.dp))
    val ring = rememberSlidingSelection()
    FlowRow(Modifier.colorRing(ring, Color(slot.color)), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      settings.savedColors.forEach { col ->
        Box {
          ColorDot(Color(col), selected = col == slot.color, selection = ring, onLongClick = { removeTarget = col }) { vm.pickPenColor(col) }
          DropdownMenu(expanded = removeTarget == col, onDismissRequest = { removeTarget = null }) {
            DropdownMenuItem(text = { Text("Remove colour") }, onClick = {
              vm.removeColor(col)
              removeTarget = null
            })
          }
        }
      }
      Box(
        Modifier.size(38.dp).clip(CircleShape).clickable(onClickLabel = "Add a colour") { picking = !picking },
        contentAlignment = Alignment.Center,
      ) {
        Canvas(Modifier.size(30.dp)) { dashedCircle(c.inkFaint, size.width / 2 - 2f) }
        Icon(Icons.Outlined.Add, null, tint = c.inkMuted, modifier = Modifier.size(18.dp))
      }
    }
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

/** Draws a sample stroke with the real Ink brush, so size and style changes show exactly as they'll look. */
@Composable
fun StrokePreview(type: PenType, color: Int, size: Float, line: LineStyle = LineStyle.SOLID) {
  val c = Tali.colors
  val renderer = remember { Brushes.renderer() }
  val w = 392f
  val h = 70f
  val stroke = remember(type, color, size, line) { StrokeFactory.previewStroke(Brushes.brush(type, color, size, line), w, h) }
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
      TaliSlider(settings.eraserSize, 6f..64f, vm::setEraserSize, Modifier.weight(1f))
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
    PanelTitle("Lasso")
    Text("Circle anything to move, resize, recolour or copy it. Tap a single item to pick just that one.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))
    SectionLabel("Picks up")
    Spacer(Modifier.height(4.dp))
    Spacer(Modifier.height(4.dp))
    FilterChips {
      PenType.offered.forEach { type -> FilterChip(type.label, settings.lassoHits(type)) { vm.setLassoAffects(type, it) } }
      FilterChip("Images", settings.lassoSelectsImages, vm::setLassoImages)
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShapesPanel(settings: AppSettings, current: ShapeKind, vm: NotebookViewModel) {
  val c = Tali.colors
  PanelColumn(588) {
    PanelTitle("Shapes")
    ToggleRow("Hold to perfect shapes", settings.holdToShape, vm::setHoldToShape, detail = "Pause at the end of a stroke and it snaps into a clean line or shape.")
    if (settings.holdToShape) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Hold", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, modifier = Modifier.width(44.dp))
        TaliSlider(settings.holdDelayMs.toFloat(), 250f..1000f, { vm.setHoldDelay(it.toLong()) }, Modifier.weight(1f))
        Text(String.format("%.2f s", settings.holdDelayMs / 1000f), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(56.dp).padding(start = 8.dp))
      }
    }
    ShapeGroup.entries.forEach { group ->
      Spacer(Modifier.height(12.dp))
      SectionLabel(group.label)
      Spacer(Modifier.height(8.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ShapeKind.entries.filter { it.group == group }.forEach { kind ->
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
