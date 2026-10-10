package com.tali.notes.ui.notebook

import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.ui.layout.onGloballyPositioned
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
  recording: Boolean = false,
  onVoice: () -> Unit = {},
  onVoiceAnchor: (androidx.compose.ui.layout.LayoutCoordinates) -> Unit = {},
) {
  val c = Tali.colors
  FloatingBar(modifier.height(MAIN_TOOLBAR_HEIGHT), crown = CloudCrown.EDGE) {
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
          // A starred pen (in the S Pen button's cycle) wears a small gold star beside its tip.
          if (slot.starred) {
            Icon(
              androidx.compose.material.icons.Icons.Filled.Star,
              "Starred",
              tint = Color(0xFFF5B83D),
              modifier = Modifier.align(Alignment.TopEnd).offset(x = (-1).dp, y = lift + 1.dp).size(10.dp),
            )
          }
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
      tool == Tool.PEN && settings.toolbarColorsFor(settings.activeSlot) > 0,
      enter = fadeIn(tween(180)) + expandHorizontally(tween(220, easing = EaseOutStrong), expandFrom = Alignment.Start),
      exit = fadeOut(tween(120)) + shrinkHorizontally(tween(160, easing = EaseOutStrong), shrinkTowards = Alignment.Start),
    ) {
      val slot = settings.activeSlot
      fun shown(col: Int) = Color(if (darkPaper) com.tali.notes.ink.Brushes.onDarkPaper(col) else col)
      val ring = rememberSlidingSelection()
      // Changing how many colours there are eases the toolbar to its new width instead of jumping.
      Row(Modifier.animateContentSize(tween(220, easing = EaseOutStrong)).padding(start = 2.dp).colorRing(ring, shown(slot.color)), verticalAlignment = Alignment.CenterVertically) {
        settings.paletteFor(slot).take(settings.toolbarColorsFor(slot)).forEach { col -> ColorDot(shown(col), selected = col == slot.color, size = 22.dp, selection = ring) { onQuickColor(col) } }
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
        if (settings.toolbarColorsFor(settings.activeSlot) > 0) Box(Modifier.padding(start = 6.dp, end = 2.dp).width(1.dp).height(20.dp).background(c.line))
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
    val items: List<Pair<String, @Composable () -> Unit>> = listOf(
      "Eraser" to { ToolButton(TaliIcons.Eraser, "Eraser", selected = tool == Tool.ERASER, blue = true, selection = tools) { onTool(Tool.ERASER) } },
      "Lasso" to {
        when {
          settings.lassoShot -> ToolButton(Icons.Outlined.Screenshot, "Screenshot lasso", selected = tool == Tool.LASSO, blue = true, selection = tools) { onTool(Tool.LASSO) }
          else -> ToolButton(if (settings.lassoFree) TaliIcons.FreeSelect else TaliIcons.LassoDotted, if (settings.lassoFree) "Free-form select" else "Lasso", selected = tool == Tool.LASSO, blue = true, selection = tools) { onTool(Tool.LASSO) }
        }
      },
      "Fill" to { ToolButton(Icons.Outlined.FormatColorFill, "Fill", selected = tool == Tool.FILL, blue = true, selection = tools) { onTool(Tool.FILL) } },
      "Laser" to { ToolButton(TaliIcons.Laser, "Laser", selected = tool == Tool.LASER, blue = true, selection = tools) { onTool(Tool.LASER) } },
      "Shapes" to {
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
      },
      "Insert" to {
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
      },
      // A voice memo, kept beside the page; pressed again while recording, it stops.
      "Voice" to {
        androidx.compose.foundation.layout.Box(Modifier.onGloballyPositioned(onVoiceAnchor)) {
          ToolButton(Icons.Outlined.Mic, if (recording) "Voice memo" else "Record a voice memo", selected = recording, blue = true, onClick = onVoice)
        }
      },
      // Typing: a text block where the next line would go.
      "Keyboard" to { ToolButton(Icons.Outlined.Keyboard, "Type text", blue = true, onClick = onKeyboard) },
    )
    ToolStrip(items, toolKey, tools)
  }
}

/**
 * The tools after the pens. When they all fit (almost always) they simply sit in a row. Only
 * when the bar runs out of room (held upright with many pens and colours) they're split into
 * pages with an arrow at the end, and the arrow slides to the next page, so nothing is cut off.
 */
@Composable
private fun ToolStrip(items: List<Pair<String, @Composable () -> Unit>>, selectedKey: String?, selection: com.tali.notes.ui.components.SlidingSelection) {
  val c = Tali.colors
  androidx.compose.foundation.layout.BoxWithConstraints {
    val item = 44.dp
    val arrow = 30.dp
    if (maxWidth >= item * items.size + 2.dp) {
      Row(Modifier.slidingHighlight(selection, selectedKey, SolidColor(c.blueSoft), 14.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEach { (key, content) -> androidx.compose.runtime.key(key) { content() } }
        Spacer(Modifier.width(2.dp))
      }
      return@BoxWithConstraints
    }
    val per = ((maxWidth - arrow) / item).toInt().coerceIn(1, items.size)
    val pages = items.chunked(per)
    var page by remember { mutableStateOf(0) }
    if (page >= pages.size) page = pages.size - 1
    // A tool chosen elsewhere (from a panel, say) brings its page into view.
    LaunchedEffect(selectedKey, pages.size) {
      val at = pages.indexOfFirst { p -> p.any { it.first == selectedKey } }
      if (at >= 0) page = at
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      androidx.compose.animation.AnimatedContent(
        targetState = page,
        transitionSpec = {
          val forward = targetState > initialState
          (androidx.compose.animation.slideInHorizontally(tween(240, easing = EaseOutStrong)) { w -> if (forward) w / 2 else -w / 2 } + fadeIn(tween(160, delayMillis = 40)))
            .togetherWith(androidx.compose.animation.slideOutHorizontally(tween(200, easing = EaseOutStrong)) { w -> if (forward) -w / 2 else w / 2 } + fadeOut(tween(120)))
            .using(androidx.compose.animation.SizeTransform(clip = true))
        },
        label = "toolPage",
      ) { p ->
        Row(Modifier.slidingHighlight(selection, selectedKey, SolidColor(c.blueSoft), 14.dp), verticalAlignment = Alignment.CenterVertically) {
          pages[p].forEach { (key, content) -> androidx.compose.runtime.key(key) { content() } }
        }
      }
      // The arrow turns round on the last page, pointing back to the first.
      val last = page == pages.size - 1
      val turn by animateFloatAsState(if (last) 180f else 0f, tween(220, easing = EaseOutStrong), label = "toolArrow")
      Box(
        Modifier.size(arrow, 44.dp).clip(RoundedCornerShape(10.dp))
          .clickable(onClickLabel = if (last) "First tools" else "More tools", role = Role.Button) { page = if (last) 0 else page + 1 },
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, if (last) "First tools" else "More tools", tint = c.inkMuted, modifier = Modifier.size(22.dp).rotate(turn))
      }
    }
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
