package com.tali.notes.ui.components

import android.net.Uri
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
import androidx.compose.material.icons.outlined.KeyboardArrowUp
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

/**
 * Paper choices: the built-in styles, then her own backgrounds, then a tile to make a new one. Her
 * own ones can be removed with a long press.
 */
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
) {
  val c = Tali.colors
  var removing by remember { mutableStateOf<CustomPaper?>(null) }
  var expanded by remember { mutableStateOf(false) }
  FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = perRow) {
    val tile: @Composable (String, Boolean, () -> Unit, (() -> Unit)?, @Composable (Modifier) -> Unit) -> Unit = { label, on, click, longClick, swatch ->
      Column(
        Modifier.width(tileWidth)
          .clip(RoundedCornerShape(10.dp))
          .background(if (on) c.accentSoft else Color.Transparent)
          .combinedClickable(role = Role.RadioButton, onClickLabel = label, onClick = click, onLongClick = longClick)
          .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        swatch(Modifier.width(tileWidth * 0.62f).aspectRatio(1.22f).then(if (on) Modifier.border(1.5.dp, c.accent, RoundedCornerShape(8.dp)) else Modifier))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.sp), color = if (on) c.accent else c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp))
      }
    }
    // Every choice in order: the built-in papers, then hers.
    val all: List<Pair<String, @Composable () -> Unit>> =
      PaperStyle.entries.map { s -> s.name to @Composable { tile(s.label, customId == null && s == style, { onStyle(s) }, null) { m -> PaperSwatch(s, m) } } } +
        customs.map { cp -> cp.id to @Composable { tile(cp.name, cp.id == customId, { onCustom(cp) }, { removing = cp }) { m -> CustomPaperSwatch(cp, m) } } }
    // Folded: Blank, Lines and Grid, then More. Open: every paper, hers, and Your own last.
    val current = customId ?: style.name
    val shown = if (expanded) all else FOLDED.mapNotNull { name -> all.firstOrNull { it.first == name } }
    shown.forEach { it.second() }
    // While folded, More stands in for a paper in use that isn't one of the three.
    val hiddenCurrent = !expanded && current !in FOLDED
    val currentCustom = customs.firstOrNull { it.id == customId }
    tile(if (expanded) "Fewer" else if (hiddenCurrent) (currentCustom?.name ?: style.label) else "More", hiddenCurrent, { expanded = !expanded }, null) { m ->
      Box(m.clip(RoundedCornerShape(8.dp)).background(c.sunken), contentAlignment = Alignment.Center) {
        if (hiddenCurrent) {
          if (currentCustom != null) CustomPaperSwatch(currentCustom, Modifier.fillMaxSize()) else PaperSwatch(style, Modifier.fillMaxSize())
        }
        Icon(
          if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
          null,
          tint = c.inkMuted,
          modifier = Modifier.size(20.dp).then(if (hiddenCurrent) Modifier.clip(RoundedCornerShape(6.dp)).background(c.surface.copy(alpha = 0.85f)) else Modifier),
        )
      }
    }
    if (expanded) {
      tile("Your own", false, onAdd, null) { m ->
        Box(m.clip(RoundedCornerShape(8.dp)).border(1.dp, c.line, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
          Icon(Icons.Outlined.Add, null, tint = c.accent, modifier = Modifier.size(18.dp))
        }
      }
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
fun CustomPaperSwatch(cp: CustomPaper, modifier: Modifier = Modifier) {
  val image = cp.image
  if (image != null) {
    val bmp = rememberPaperImage(image, 240)
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(Color.White)) {
      if (bmp != null) Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    return
  }
  PatternSwatch(cp, modifier)
}

/** Her designed pattern, drawn as if the swatch showed a strip about 60 mm wide. */
@Composable
private fun PatternSwatch(cp: CustomPaper, modifier: Modifier) {
  Canvas(modifier) {
    drawRoundRect(Color(cp.paperColor), cornerRadius = CornerRadius(8.dp.toPx()))
    val step = size.width * cp.spacingMm / 60f
    val line = Color(cp.lineColor)
    if (step >= 2f) {
      when (cp.pattern) {
        PaperStyle.BLANK -> Unit
        PaperStyle.DOTS -> {
          var y = step
          while (y < size.height) {
            var x = step
            while (x < size.width) {
              drawCircle(line, maxOf(1f, step / 9f), Offset(x, y))
              x += step
            }
            y += step
          }
        }
        else -> {
          var y = step
          while (y < size.height) {
            drawLine(line, Offset(0f, y), Offset(size.width, y), density)
            y += step
          }
          if (cp.pattern != PaperStyle.LINES) {
            var x = step
            while (x < size.width) {
              drawLine(line, Offset(x, 0f), Offset(x, size.height), density)
              x += step
            }
          }
        }
      }
    }
    if (cp.margin) drawLine(Color(0xFFF0A0B4), Offset(size.width * 0.2f, 0f), Offset(size.width * 0.2f, size.height), 1.2f * density)
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
fun NewPaperDialog(onDismiss: () -> Unit, importImage: suspend (Uri) -> String?, onSave: (CustomPaper) -> Unit) {
  val c = Tali.colors
  val scope = rememberCoroutineScope()
  var picture by remember { mutableStateOf(false) }
  var draft by remember { mutableStateOf(CustomPaper(id = UUID.randomUUID().toString().take(8), name = "")) }
  var name by remember { mutableStateOf(TextFieldValue("")) }
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
    Text("Your own paper", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text("Saved with your papers, so every notebook can use it.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
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
              LINE_COLORS.forEach { col -> ColorDot(Color(col), selected = col == draft.lineColor, size = 26.dp) { draft = draft.copy(lineColor = col) } }
            }
          }
          Spacer(Modifier.height(6.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Paper", Modifier.width(76.dp))
            PAPER_COLORS.forEach { col -> ColorDot(Color(col), selected = col == draft.paperColor, size = 26.dp) { draft = draft.copy(paperColor = col) } }
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
      if (canSave) PrimaryButton("Save paper") { save() }
      else PrimaryButton("Choose a picture") { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
  }
}
