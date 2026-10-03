package com.tali.notes.ui.notebook

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tali.notes.data.AppSettings
import com.tali.notes.model.PenSlot
import com.tali.notes.model.CustomShape
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.Tool
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.CloudCrown
import com.tali.notes.ui.components.FloatingBar
import com.tali.notes.ui.components.GeometryIcon
import com.tali.notes.ui.components.PenGlyph
import com.tali.notes.ui.components.ColorDot
import com.tali.notes.ui.components.EaseOutStrong
import com.tali.notes.ui.components.colorRing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import com.tali.notes.ui.components.ShapeIcon
import com.tali.notes.ui.components.TaliIcons
import com.tali.notes.ui.components.ToolButton
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.SolidColor
import com.tali.notes.ui.components.rememberSlidingSelection
import com.tali.notes.ui.components.slidingHighlight
import com.tali.notes.ui.components.slidingOption
import androidx.compose.material.icons.outlined.FormatColorFill

@Composable
fun MainToolbar(
  settings: AppSettings,
  darkPaper: Boolean,
  tool: Tool,
  shapeKind: ShapeKind,
  customShape: CustomShape?,
  onPen: (PenSlot) -> Unit,
  penSettingsOpen: Boolean,
  onPenSettings: () -> Unit,
  onQuickColor: (Int) -> Unit,
  onQuickSize: (Int) -> Unit,
  onSetSize: (Int, Float) -> Unit,
  onTool: (Tool) -> Unit,
  onImage: () -> Unit,
  onCamera: () -> Unit,
  onPdf: () -> Unit,
  onKeyboard: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Tali.colors
  FloatingBar(modifier.height(60.dp), crown = CloudCrown.EDGE) {
    // The pen tray: pens stand at the bottom edge, the active one lifted.
    Row(Modifier.height(56.dp).clipToBounds().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
      settings.pens.forEach { slot ->
        val active = tool == Tool.PEN && slot.id == settings.activePen
        val lift by animateDpAsState(if (active) 2.dp else 16.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "lift")
        Box(
          Modifier.width(34.dp)
            .height(56.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = slot.type.label) { onPen(slot) },
          contentAlignment = Alignment.TopCenter,
        ) {
          // On dark paper the pens show the colour they really write in there.
          PenGlyph(slot.type, Color(if (darkPaper) com.tali.notes.ink.Brushes.onDarkPaper(slot.color) else slot.color), Modifier.offset(y = lift), line = slot.lineStyle)
        }
      }
    }
    // The pen's settings (style, size, colour) open from the little arrow.
    val turn by animateFloatAsState(if (penSettingsOpen) 180f else 0f, label = "turn")
    val arrowWash by animateColorAsState(if (penSettingsOpen) c.accentSoft else Color.Transparent, tween(200), label = "arrowWash")
    Box(
      Modifier.size(28.dp, 44.dp).clip(RoundedCornerShape(10.dp)).background(arrowWash).clickable(onClickLabel = "Pen settings", role = Role.Button, onClick = onPenSettings),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.KeyboardArrowDown, "Pen settings", tint = if (penSettingsOpen) c.accent else c.inkMuted, modifier = Modifier.size(22.dp).rotate(turn))
    }
    // Her first few saved colours (as many as she chose, in her order): switch the pen's colour
    // without opening its settings. The ring glides between them.
    AnimatedVisibility(
      tool == Tool.PEN && settings.toolbarColors > 0,
      enter = fadeIn(tween(180)) + expandHorizontally(tween(220, easing = EaseOutStrong), expandFrom = Alignment.Start),
      exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160, easing = EaseOutStrong), shrinkTowards = Alignment.Start),
    ) {
      val slot = settings.activeSlot
      fun shown(col: Int) = Color(if (darkPaper) com.tali.notes.ink.Brushes.onDarkPaper(col) else col)
      val ring = rememberSlidingSelection()
      // Changing how many colours there are eases the toolbar to its new width instead of jumping.
      Row(Modifier.animateContentSize(tween(220, easing = EaseOutStrong)).padding(start = 2.dp).colorRing(ring, shown(slot.color)), verticalAlignment = Alignment.CenterVertically) {
        settings.paletteFor(slot).take(settings.toolbarColors).forEach { col -> ColorDot(shown(col), selected = col == slot.color, size = 22.dp, selection = ring) { onQuickColor(col) } }
      }
    }
    // Her three thicknesses for this pen. Tap one to write with it; tap the one in use to set it.
    AnimatedVisibility(
      tool == Tool.PEN,
      enter = fadeIn(tween(180)) + expandHorizontally(tween(220, easing = EaseOutStrong), expandFrom = Alignment.Start),
      exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160, easing = EaseOutStrong), shrinkTowards = Alignment.Start),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        // A short line keeps the colours and the thicknesses apart.
        if (settings.toolbarColors > 0) Box(Modifier.padding(start = 6.dp, end = 2.dp).width(1.dp).height(20.dp).background(c.line))
        QuickSizes(settings.activeSlot, onQuickSize, onSetSize)
      }
    }
    Divider()
    // Eraser, lasso and shapes share one highlight that glides between them.
    val tools = rememberSlidingSelection()
    val toolKey = when (tool) {
      Tool.ERASER -> "Eraser"
      Tool.LASSO -> "Lasso"
      Tool.SHAPE -> "Shapes"
      Tool.FILL -> "Fill"
      Tool.LASER -> "Laser"
      else -> null
    }
    Row(Modifier.slidingHighlight(tools, toolKey, SolidColor(c.blueSoft), 14.dp), verticalAlignment = Alignment.CenterVertically) {
      ToolButton(TaliIcons.Eraser, "Eraser", selected = tool == Tool.ERASER, blue = true, selection = tools) { onTool(Tool.ERASER) }
      ToolButton(TaliIcons.Lasso, "Lasso", selected = tool == Tool.LASSO, blue = true, selection = tools) { onTool(Tool.LASSO) }
      ToolButton(Icons.Outlined.FormatColorFill, "Fill", selected = tool == Tool.FILL, blue = true, selection = tools) { onTool(Tool.FILL) }
      ToolButton(TaliIcons.Laser, "Laser", selected = tool == Tool.LASER, blue = true, selection = tools) { onTool(Tool.LASER) }
      // While the shape tool is on, its button shows which shape it will draw.
      Box(
        Modifier.slidingOption(tools, "Shapes").size(44.dp).clip(RoundedCornerShape(14.dp))
          .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = "Shapes") { onTool(Tool.SHAPE) },
        contentAlignment = Alignment.Center,
      ) {
        when {
          tool != Tool.SHAPE -> Icon(Icons.Outlined.Category, "Shapes", tint = c.ink, modifier = Modifier.size(22.dp))
          customShape != null -> GeometryIcon(customShape.geometry, c.blue, size = 26.dp)
          else -> ShapeIcon(shapeKind, c.blue, size = 26.dp)
        }
      }
    }
    Box {
      var inserting by remember { mutableStateOf(false) }
      ToolButton(Icons.Outlined.AddPhotoAlternate, "Insert") { inserting = true }
      DropdownMenu(expanded = inserting, onDismissRequest = { inserting = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
        DropdownMenuItem(
          text = { Column { Text("Picture"); Text("From your photos or files", style = MaterialTheme.typography.bodySmall, color = c.inkFaint) } },
          leadingIcon = { Icon(Icons.Outlined.Image, null, tint = c.blue) },
          onClick = { inserting = false; onImage() },
        )
        DropdownMenuItem(
          text = { Column { Text("Take a photo"); Text("The board, a textbook page, a sketch", style = MaterialTheme.typography.bodySmall, color = c.inkFaint) } },
          leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, tint = c.blue) },
          onClick = { inserting = false; onCamera() },
        )
        DropdownMenuItem(
          text = { Column { Text("PDF pages"); Text("Lecture slides or a problem sheet to write on", style = MaterialTheme.typography.bodySmall, color = c.inkFaint) } },
          leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null, tint = c.blue) },
          onClick = { inserting = false; onPdf() },
        )
      }
    }
    // Typing: a text block where the next line would go.
    ToolButton(Icons.Outlined.Keyboard, "Type text", blue = true, onClick = onKeyboard)
    Spacer(Modifier.width(2.dp))
  }
}

@Composable
private fun Divider() {
  Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(28.dp).background(Tali.colors.line))
}

@Composable
fun ColorChip(color: Color, onClick: () -> Unit) {
  Box(Modifier.size(28.dp).clip(CircleShape).background(color).clickable(onClick = onClick))
}

/**
 * Three dots, small to large by her three thicknesses, with one highlight gliding between them.
 * Tapping the one in use opens a little slider (− and + in 0.5 steps) to set that thickness.
 */
@Composable
private fun QuickSizes(slot: PenSlot, onPick: (Int) -> Unit, onSet: (Int, Float) -> Unit) {
  val c = Tali.colors
  val sizes = slot.presetSizes
  val current = slot.sizeSlot.coerceIn(0, 2)
  // Dot sizes follow the order of her thicknesses, not their real width (a highlighter would be huge).
  val rank = sizes.indices.sortedBy { sizes[it] }
  val glide = rememberSlidingSelection()
  var editing by remember { mutableStateOf<Int?>(null) }
  Row(Modifier.padding(start = 4.dp).slidingHighlight(glide, current, SolidColor(c.accentSoft), 12.dp), verticalAlignment = Alignment.CenterVertically) {
    sizes.indices.forEach { i ->
      val dot by animateDpAsState((4 + 4 * rank.indexOf(i)).dp, tween(200), label = "sizeDot")
      val ink by animateColorAsState(if (i == current) c.accent else c.inkMuted, tween(200), label = "sizeInk")
      Box {
        Box(
          Modifier.slidingOption(glide, i).size(28.dp, 36.dp).clip(RoundedCornerShape(12.dp))
            .clickable(interactionSource = null, indication = null, role = Role.RadioButton, onClickLabel = "Thickness ${i + 1}") {
              if (i == current) editing = i else onPick(i)
            },
          contentAlignment = Alignment.Center,
        ) {
          Box(Modifier.size(dot).clip(CircleShape).background(ink))
        }
        DropdownMenu(expanded = editing == i, onDismissRequest = { editing = null }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
          Column(Modifier.width(280.dp).padding(horizontal = 14.dp, vertical = 4.dp)) {
            Text("Thickness ${i + 1}", style = MaterialTheme.typography.titleSmall, color = c.ink)
            Row(verticalAlignment = Alignment.CenterVertically) {
              com.tali.notes.ui.components.StepSlider(sizes[i], slot.type.minSize..slot.type.maxSize, 0.5f, { onSet(i, it) }, Modifier.weight(1f))
              Text(String.format(java.util.Locale.US, "%.1f", sizes[i]), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(40.dp).padding(start = 8.dp))
            }
          }
        }
      }
    }
  }
}
