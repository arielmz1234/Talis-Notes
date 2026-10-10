package com.tali.notes.ui.components

import androidx.compose.ui.layout.onSizeChanged
import android.net.Uri
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tali.notes.model.CustomPaper
import com.tali.notes.model.PaperStyle
import com.tali.notes.talisApp
import com.tali.notes.theme.Tali
import com.tali.notes.ui.library.ConfirmDialog
import com.tali.notes.ui.library.DialogCard
import com.tali.notes.ui.library.TitleField
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Paper choices: the built-in styles, then her own backgrounds, then a tile to make a new one. Her
 * own ones can be removed with a long press.
 */
/** The paper picker's More unfolding the rest (an entry), and folding them away (quicker, as exits are). */
private const val PAPERS_OPEN_MS = 260
private const val PAPERS_CLOSE_MS = 180

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun PaperPicker(
  style: PaperStyle,
  customId: String?,
  customs: List<CustomPaper>,
  onStyle: (PaperStyle) -> Unit,
  onCustom: (CustomPaper) -> Unit,
  onAdd: () -> Unit,
  onRemove: (CustomPaper) -> Unit,
  modifier: Modifier = Modifier,
  tileWidth: Dp = 62.dp,
  perRow: Int = 4,
  lineWeight: Float? = null,
  onLineWeight: ((Float) -> Unit)? = null,
  /** Tapping the paper already in use opens its edit card (its line thickness, and more for hers). */
  onEditCurrent: (() -> Unit)? = null,
  /** Show the papers as dark pages (dark paper, or the dark theme). */
  dark: Boolean = Tali.colors.isDark,
  /** Her colours for the built-in papers. */
  looks: Map<String, com.tali.notes.model.PaperLook> = emptyMap(),
) {
  val c = Tali.colors
  var removing by remember { mutableStateOf<CustomPaper?>(null) }
  var expanded by remember { mutableStateOf(false) }
  val glide = rememberSlidingSelection()
  // As many tiles as fit across (up to perRow); folded, that's papers plus More on one line. The
  // width is read once laid out, not with BoxWithConstraints: this picker also sits in a dropdown
  // menu, which asks its content for its natural width, and BoxWithConstraints can't answer that
  // (it crashed opening the paper menu).
  var widthPx by remember { androidx.compose.runtime.mutableIntStateOf(0) }
  val density = androidx.compose.ui.platform.LocalDensity.current
  val fit =
    if (widthPx == 0) maxOf(2, perRow)
    else with(density) { ((widthPx.toDp() + 4.dp) / (tileWidth + 4.dp)).toInt() }.coerceIn(2, maxOf(2, perRow))
  Box(modifier.onSizeChanged { widthPx = it.width }) {
  // Folded: the papers she's most likely to want first, as many as fit beside More. A paper in use
  // that isn't among them takes the first place, so it shows; More always stays More. The row only
  // changes while folded, so a paper picked from the open list doesn't jump up under her finger.
  var shownCurrent by remember { mutableStateOf(customId ?: style.name) }
  if (!expanded) shownCurrent = customId ?: style.name
  val usual = (FOLDED + PaperStyle.entries.map { it.name }.filterNot { it in FOLDED } + customs.map { it.id }).take(fit - 1)
  val folded = if (shownCurrent in usual || (PaperStyle.entries.none { it.name == shownCurrent } && customs.none { it.id == shownCurrent })) usual else (listOf(shownCurrent) + usual).take(fit - 1)
  // The highlight is drawn by the whole column, so it glides between the two rows too.
  Column(Modifier.slidingHighlight(glide, customId ?: style, SolidColor(c.accentSoft), 10.dp)) {
    val tile: @Composable (Any, String, Boolean, () -> Unit, (() -> Unit)?, @Composable (Modifier) -> Unit) -> Unit = { key, label, on, click, longClick, swatch ->
      val edge by animateColorAsState(if (on) c.accent else Color.Transparent, tween(220), label = "swatchEdge")
      val ink by animateColorAsState(if (on) c.accent else c.inkMuted, tween(220), label = "paperLabel")
      Column(
        Modifier.slidingOption(glide, key)
          .width(tileWidth)
          .clip(RoundedCornerShape(10.dp))
          .combinedClickable(interactionSource = null, indication = null, role = Role.RadioButton, onClickLabel = label, onClick = click, onLongClick = longClick)
          .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box {
          swatch(Modifier.width(tileWidth * 0.62f).aspectRatio(1.22f).border(1.5.dp, edge, RoundedCornerShape(8.dp)))
          // The paper in use wears a small pencil: tapping it again edits it.
          if (on && onEditCurrent != null && key != "more" && key != "own") {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp, y = (-5).dp).size(16.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
              Icon(Icons.Outlined.Edit, null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
          }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
      }
    }
    // Every choice in order: the built-in papers, then hers.
    val all: List<Pair<String, @Composable () -> Unit>> =
      PaperStyle.entries.map { s ->
        val on = customId == null && s == style
        s.name to @Composable { tile(s, s.label, on, { if (on && onEditCurrent != null) onEditCurrent() else onStyle(s) }, null) { m -> PaperSwatch(s, m, dark, looks[s.name], com.tali.notes.canvas.PaperRenderer.weightOf(s, null)) } }
      } +
        customs.map { cp ->
          val on = cp.id == customId
          cp.id to @Composable { tile(cp.id, cp.name, on, { if (on && onEditCurrent != null) onEditCurrent() else onCustom(cp) }, { removing = cp }) { m -> CustomPaperSwatch(cp, m, dark) } }
        }
    // The first row never moves: as many papers as fit (Blank, Lines and Grid first), then More.
    // Opening unfolds the rest underneath, so nothing she's looking at jumps.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = fit) {
      folded.forEach { name -> all.firstOrNull { it.first == name }?.second?.invoke() }
      val turn = animateFloatAsState(if (expanded) 180f else 0f, tween(PAPERS_OPEN_MS, easing = EaseOutStrong), label = "moreArrow")
      tile("more", if (expanded) "Fewer" else "More", false, { expanded = !expanded }, null) { m ->
        Box(m.clip(RoundedCornerShape(8.dp)).background(c.sunken), contentAlignment = Alignment.Center) {
          Icon(
            Icons.Outlined.KeyboardArrowDown,
            null,
            tint = c.inkMuted,
            // Turned in the draw phase, so the arrow doesn't recompose the picker every frame.
            modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn.value },
          )
        }
      }
    }
    // The rest, then Your own. The height eases open with a strong ease-out while the tiles drift
    // down into place and fade in, row after row; folding is quicker, and they fade before the
    // height closes over them.
    AnimatedVisibility(
      expanded,
      enter = expandVertically(tween(PAPERS_OPEN_MS, easing = EaseOutStrong), expandFrom = Alignment.Top),
      exit = shrinkVertically(tween(PAPERS_CLOSE_MS, easing = EaseOutStrong), shrinkTowards = Alignment.Top) + fadeOut(tween(PAPERS_CLOSE_MS / 2)),
    ) {
      val rest = all.filterNot { it.first in folded }
      FlowRow(
        Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxItemsInEachRow = fit,
      ) {
        val items = rest.map { it.second } + @Composable {
          tile("own", "Your own", false, onAdd, null) { m ->
            Box(m.clip(RoundedCornerShape(8.dp)).border(1.dp, c.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
              Icon(Icons.Outlined.Add, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
          }
        }
        items.forEachIndexed { i, item ->
          val row = i / fit
          val shown = transition.animateFloat({ tween(PAPERS_OPEN_MS - 40, delayMillis = minOf(row, 3) * 40, easing = EaseOutStrong) }, label = "paperIn") { if (it == EnterExitState.Visible) 1f else 0f }
          Box(Modifier.graphicsLayer { alpha = shown.value; translationY = (1f - shown.value) * -8.dp.toPx() }) { item() }
        }
      }
    }
  if (lineWeight != null && onLineWeight != null) LineWeightRow(lineWeight, onLineWeight)
  }
  }
  removing?.let { cp ->
    ConfirmDialog(
      title = "Remove “${cp.name}”?",
      body = "It disappears from the paper choices. Notebooks using it switch to blank paper; your writing stays.",
      confirm = "Remove",
      destructive = true,
      onDismiss = { removing = null },
    ) {
      onRemove(cp)
      removing = null
    }
  }
}

/** A small picture of one of her backgrounds. */
@Composable
fun CustomPaperSwatch(cp: CustomPaper, modifier: Modifier = Modifier, dark: Boolean = false) {
  val image = cp.image
  if (image != null) {
    val bmp = rememberPaperImage(image, 240)
    // On dark paper the picture is dimmed, as the page shows it.
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(if (dark) Color(com.tali.notes.canvas.PaperRenderer.DARK_PAPER) else Color.White)) {
      if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, alpha = cp.opacity.coerceIn(0.15f, 1f) * (if (dark) 0.4f else 1f), modifier = Modifier.fillMaxSize())
    }
    return
  }
  PatternSwatch(cp, modifier, dark)
}

/** Her designed pattern, drawn as if the swatch showed a strip about 60 mm wide. */
@Composable
private fun PatternSwatch(cp: CustomPaper, modifier: Modifier, dark: Boolean = false) {
  Canvas(modifier) {
    // On dark paper: the dark page, her line colour lightened (as the page draws it).
    drawRoundRect(if (dark) Color(com.tali.notes.canvas.PaperRenderer.DARK_PAPER) else Color(cp.paperColor), cornerRadius = CornerRadius(8.dp.toPx()))
    val step = size.width * cp.spacingMm / 60f
    val line = Color(cp.faded(if (dark) (com.tali.notes.ink.Brushes.onDarkPaper(cp.lineColor) and 0x00FFFFFF) or 0x70000000 else cp.lineColor))
    // Her line thickness, toned down for the small picture (Normal draws as before).
    val weight = (cp.lineWeight ?: com.tali.notes.canvas.PaperRenderer.lineWeight).coerceIn(0.3f, 5f)
    val w = 0.5f + 0.5f * weight
    if (step >= 2f) {
      when (cp.pattern) {
        PaperStyle.BLANK -> Unit
        PaperStyle.DOTS -> {
          var y = step
          while (y < size.height) {
            var x = step
            while (x < size.width) {
              drawCircle(line, maxOf(1f, step / 9f) * (0.6f + 0.4f * weight.coerceAtMost(3f)), Offset(x, y))
              x += step
            }
            y += step
          }
        }
        else -> {
          var y = step
          while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), density * w)
            y += step
          }
          if (cp.pattern != PaperStyle.LINES) {
            var x = step
            while (x < size.width) {
              drawLine(line, Offset(x, 0f), Offset(x, size.height), density * w)
              x += step
            }
          }
        }
      }
    }
    if (cp.margin) drawLine(Color(cp.faded(0xFFF0A0B4.toInt())), Offset(size.width * 0.2f, 0f), Offset(size.width * 0.2f, size.height), 1.2f * density * w)
    drawRoundRect(Color(0x22000000), cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(density * 0.6f))
  }
}

/** A picture background, scaled down to [maxPx] wide for previews, loaded off the main thread. */
@Composable
fun rememberPaperImage(file: String, maxPx: Int): ImageBitmap? {
  val store = LocalContext.current.talisApp.repository.images
  val state =
    produceState<ImageBitmap?>(null, file, maxPx) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
              val full = store.loadNow(file) ?: return@runCatching null
              val w = minOf(maxPx, full.width)
              android.graphics.Bitmap.createScaledBitmap(full, w, (full.height.toLong() * w / full.width).toInt().coerceAtLeast(1), true).asImageBitmap()
            }
            .getOrNull()
        }
    }
  return state.value
}

/**
 * How heavy the paper's lines, grid and dots are, as four named choices. (A bare slider here read
 * as a stray white line under the papers.)
 */
@Composable
internal fun LineWeightRow(value: Float, onChange: (Float) -> Unit) {
  val c = Tali.colors
  val choices = listOf(0.6f, 1f, 2f, 3.5f, 5f)
  val names = listOf("Thin", "Normal", "Bold", "Bolder", "Heavy")
  Column(Modifier.padding(top = 10.dp)) {
    Text("Line thickness", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
    Spacer(Modifier.height(6.dp))
    Segmented(choices, choices.minBy { abs(it - value) }, { names[choices.indexOf(it)] }, onChange, Modifier.fillMaxWidth())
  }
}

/** How strongly the lines or picture show over the page, as a percentage slider. */
@Composable
private fun OpacityRow(value: Float, onChange: (Float) -> Unit) {
  val c = Tali.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    SectionLabel("Opacity", Modifier.width(76.dp))
    Slider(
      value = value,
      // Whole 5% steps, never so faint that the lines disappear.
      onValueChange = { onChange(((it * 20).roundToInt() / 20f).coerceIn(0.15f, 1f)) },
      valueRange = 0.15f..1f,
      colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.sunken),
      modifier = Modifier.weight(1f),
    )
    Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = c.inkMuted, modifier = Modifier.width(52.dp).padding(start = 8.dp))
  }
}

/** Papers shown before More. */
private val FOLDED = listOf(PaperStyle.BLANK.name, PaperStyle.LINES.name, PaperStyle.GRID.name)

private val LINE_COLORS = listOf(0xFFDADADA, 0xFFB9D2F6, 0xFFD2C3EE, 0xFFBFE3CF, 0xFFF4C6D4, 0xFF8E95A2).map { it.toInt() }
private val PAPER_COLORS = listOf(0xFFFFFFFF, 0xFFFFF8E8, 0xFFF4EEFF, 0xFFEEF5FF, 0xFFEFF8F2, 0xFFF6F1EA).map { it.toInt() }

/**
 * Making her own paper: design a pattern (lines, grid or dots at her spacing and colours, with an
 * optional margin line), or use a picture such as a planner page or worksheet. A live preview
 * shows an A4 page.
 */
@Composable
fun NewPaperDialog(onDismiss: () -> Unit, importImage: suspend (Uri) -> String?, editing: CustomPaper? = null, onSave: (CustomPaper) -> Unit) {
  val c = Tali.colors
  val scope = rememberCoroutineScope()
  var picture by remember { mutableStateOf(editing?.image != null) }
  var draft by remember { mutableStateOf(editing ?: CustomPaper(id = UUID.randomUUID().toString().take(8), name = "")) }
  var name by remember { mutableStateOf(TextFieldValue(editing?.name ?: "")) }
  var loading by remember { mutableStateOf(false) }
  var failed by remember { mutableStateOf(false) }
  val picker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        loading = true
        failed = false
        scope.launch {
          val file = importImage(uri)
          loading = false
          if (file == null) failed = true else draft = draft.copy(image = file)
        }
      }
    }
  val shown = if (picture) draft else draft.copy(image = null)
  val canSave = !picture || draft.image != null
  fun save() {
    if (!canSave) return
    val fallback = if (picture) "My picture" else "My ${draft.pattern.label.lowercase()}"
    onSave(shown.copy(name = name.text.trim().ifBlank { fallback }))
  }
  DialogCard(onDismiss, width = 640) {
    Text(if (editing != null) "Edit paper" else "Your own paper", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text(if (editing != null) "Changes show in every notebook that uses it." else "Saved with your papers, so every notebook can use it.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    Spacer(Modifier.height(16.dp))
    Segmented(listOf(false, true), picture, { if (it) "From a picture" else "Design a pattern" }, { picture = it }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
    Row {
      // An A4 page in miniature.
      Box(Modifier.width(150.dp).aspectRatio(1322f / 1871f).clip(RoundedCornerShape(10.dp)).border(1.dp, c.line, RoundedCornerShape(10.dp))) {
        if (picture && draft.image == null) {
          Box(Modifier.fillMaxSize().background(c.sunken), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Image, null, tint = c.inkFaint, modifier = Modifier.size(36.dp))
          }
        } else {
          CustomPaperSwatch(shown, Modifier.fillMaxSize())
        }
      }
      Spacer(Modifier.width(22.dp))
      Column(Modifier.weight(1f)) {
        if (picture) {
          Text(
            "The picture fills each A4 page, under your writing. Good for planner pages, timetables and printed worksheets.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkMuted,
          )
          Spacer(Modifier.height(16.dp))
          QuietButton(if (loading) "Opening…" else if (draft.image == null) "Choose a picture" else "Choose another picture") {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          }
          if (failed) {
            Spacer(Modifier.height(8.dp))
            Text("That picture couldn't be opened.", style = MaterialTheme.typography.bodySmall, color = c.danger)
          }
          if (draft.image != null) {
            Spacer(Modifier.height(14.dp))
            OpacityRow(draft.opacity) { draft = draft.copy(opacity = it) }
            Text("Fainter pictures keep your writing easy to read.", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
          }
        } else {
          SectionLabel("Pattern")
          Spacer(Modifier.height(8.dp))
          Segmented(listOf(PaperStyle.LINES, PaperStyle.GRID, PaperStyle.DOTS, PaperStyle.BLANK), draft.pattern, { it.label }, { draft = draft.copy(pattern = it) }, Modifier.fillMaxWidth())
          if (draft.pattern != PaperStyle.BLANK) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              SectionLabel("Spacing", Modifier.width(76.dp))
              Slider(
                value = draft.spacingMm,
                onValueChange = { draft = draft.copy(spacingMm = (it * 2).toInt() / 2f) },
                valueRange = 3f..14f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.sunken),
                modifier = Modifier.weight(1f),
              )
              Text("${draft.spacingMm.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }} mm", style = MaterialTheme.typography.labelMedium, color = c.inkMuted, modifier = Modifier.width(52.dp).padding(start = 8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
              SectionLabel("Lines", Modifier.width(76.dp))
              val lineRing = rememberSlidingSelection()
              Row(Modifier.colorRing(lineRing, Color(draft.lineColor))) {
                LINE_COLORS.forEach { col -> ColorDot(Color(col), selected = col == draft.lineColor, size = 26.dp, selection = lineRing) { draft = draft.copy(lineColor = col) } }
              }
            }
            Spacer(Modifier.height(2.dp))
            OpacityRow(draft.opacity) { draft = draft.copy(opacity = it) }
            LineWeightRow(draft.lineWeight ?: com.tali.notes.canvas.PaperRenderer.lineWeight) { draft = draft.copy(lineWeight = it) }
          }
          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Paper", Modifier.width(76.dp))
            val paperRing = rememberSlidingSelection()
            Row(Modifier.colorRing(paperRing, Color(draft.paperColor))) {
              PAPER_COLORS.forEach { col -> ColorDot(Color(col), selected = col == draft.paperColor, size = 26.dp, selection = paperRing) { draft = draft.copy(paperColor = col) } }
            }
          }
          Spacer(Modifier.height(4.dp))
          ToggleRow("Margin line", draft.margin, { draft = draft.copy(margin = it) }, detail = "A red line down the left, like an exercise book")
        }
      }
    }
    Spacer(Modifier.height(18.dp))
    TitleField(name, { name = it }, if (picture) "Name, e.g. Weekly planner" else "Name, e.g. Wide lines", autoFocus = false) { save() }
    Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton("Cancel", onClick = onDismiss)
      if (canSave) PrimaryButton(if (editing != null) "Save changes" else "Save paper") { save() }
      else PrimaryButton("Choose a picture") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
  }
}

/**
 * A built-in paper's edit card: how it looks, and its line thickness, line colour and paper
 * colour. Changes show at once, on the page behind and in every notebook using this paper. Reset
 * puts it back as it came.
 */
@Composable
fun BuiltInPaperDialog(
  style: PaperStyle,
  weight: Float,
  onWeight: (Float) -> Unit,
  look: com.tali.notes.model.PaperLook?,
  onLook: (com.tali.notes.model.PaperLook) -> Unit,
  onDismiss: () -> Unit,
) {
  val c = Tali.colors
  val current = look ?: com.tali.notes.model.PaperLook()
  DialogCard(onDismiss, width = 520) {
    Text(style.label, style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text("Changes show in every notebook on this paper.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.Top) {
      Box(Modifier.width(110.dp).aspectRatio(1322f / 1871f).clip(RoundedCornerShape(10.dp)).border(1.dp, c.line, RoundedCornerShape(10.dp))) {
        PaperSwatch(style, Modifier.fillMaxSize(), look = look, weight = weight)
      }
      Spacer(Modifier.width(20.dp))
      Column(Modifier.weight(1f)) {
        if (style != PaperStyle.BLANK) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Lines", Modifier.width(64.dp))
            val lineRing = rememberSlidingSelection()
            val line = current.line ?: LINE_COLORS.first()
            Row(Modifier.colorRing(lineRing, Color(line))) {
              LINE_COLORS.forEach { col -> ColorDot(Color(col), selected = col == line, size = 26.dp, selection = lineRing) { onLook(current.copy(line = col)) } }
            }
          }
          Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          SectionLabel("Paper", Modifier.width(64.dp))
          val paperRing = rememberSlidingSelection()
          val paper = current.paper ?: PAPER_COLORS.first()
          Row(Modifier.colorRing(paperRing, Color(paper))) {
            PAPER_COLORS.forEach { col -> ColorDot(Color(col), selected = col == paper, size = 26.dp, selection = paperRing) { onLook(current.copy(paper = col)) } }
          }
        }
        if (style != PaperStyle.BLANK) LineWeightRow(weight, onWeight)
      }
    }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      if (look != null) QuietButton("Reset") { onLook(com.tali.notes.model.PaperLook()) }
      PrimaryButton("Done", onClick = onDismiss)
    }
  }
}
