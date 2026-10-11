package com.tali.notes.ui.library

import com.tali.notes.tr
import androidx.compose.ui.res.stringResource
import com.tali.notes.R
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tali.notes.model.PaperStyle
import com.tali.notes.data.AppSettings
import com.tali.notes.data.COVER_COLORS
import com.tali.notes.model.CustomPaper
import com.tali.notes.ui.components.NewPaperDialog
import com.tali.notes.ui.components.PaperPicker
import com.tali.notes.ui.components.Segmented
import com.tali.notes.ui.components.ToggleRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PaperSwatch
import com.tali.notes.ui.components.cloudSurface
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.SectionLabel
import com.tali.notes.ui.components.rememberSlidingSelection
import com.tali.notes.ui.components.slidingHighlight
import com.tali.notes.ui.components.slidingOption

@Composable
fun DialogCard(onDismiss: () -> Unit, width: Int = 520, dim: Boolean = true, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  val c = Tali.colors
  // The dialog's window fills the screen and the card is laid out inside it. A wrap-content window
  // had to be resized and re-centred by the system on every frame when the card's height animated
  // (a section folding away), which stuttered; now that's just a layout pass.
  Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
    if (!dim) {
      // No tint of its own: the card floats on its own shadow. (It asks for no dimming at all rather
      // than a dimming of 0: the system keeps one dim layer behind the top window that wants one,
      // so a 0 here also wiped the tint of a dialog underneath, like the pages overview.)
      val window = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
      androidx.compose.runtime.SideEffect { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND) }
    }
    com.tali.notes.ui.components.KeepFullScreen()
    androidx.compose.foundation.layout.Box(
      Modifier.fillMaxSize()
        // A tap around the card closes it, as before.
        .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(24.dp),
      contentAlignment = Alignment.Center,
    ) {
      // A theme change (from Settings) cross-fades the card instead of switching at once.
      androidx.compose.animation.Crossfade(Tali.colors, animationSpec = androidx.compose.animation.core.tween(450), label = "cardTheme") { colors ->
      com.tali.notes.theme.TaliColorsScope(colors) {
      Column(
        // The same soft cloud surface as the notebook panels, floating on a lilac shadow.
        Modifier.widthIn(max = width.dp)
          .fillMaxWidth()
          // Taps on the card stay on the card.
          .pointerInput(Unit) { detectTapGestures { } }
          .cloudSurface(24.dp, shadowDepth = 12.dp)
          .padding(28.dp)
      ) {
        content()
      }
      }
      }
    }
  }
}

@Composable
fun TitleField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, placeholder: String, autoFocus: Boolean = true, onDone: () -> Unit) {
  val c = Tali.colors
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { if (autoFocus) focus.requestFocus() }
  val shape = RoundedCornerShape(14.dp)
  androidx.compose.foundation.layout.Box(
    Modifier.fillMaxWidth().height(52.dp).clip(shape).background(c.background).border(1.dp, c.line, shape).padding(horizontal = 16.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    if (value.text.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.inkFaint)
    BasicTextField(
      value,
      onChange,
      singleLine = true,
      textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink, textDirection = androidx.compose.ui.text.style.TextDirection.Content),
      cursorBrush = SolidColor(c.accent),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { onDone() }),
      modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
  }
}

/** Everything chosen in the new notebook card. */
data class NewNotebook(
  val title: String,
  val paper: PaperStyle,
  val customPaper: String?,
  val paged: Boolean,
  val dark: Boolean,
  val cover: Int,
  /** A4 pages on their side. */
  val landscape: Boolean = false,
)

/**
 * A new notebook: name, paper (her own papers included, and a new one can be made right here),
 * A4 pages or an endless canvas, dark paper, and the cover colour.
 */
@Composable
fun NewNotebookDialog(
  settings: AppSettings,
  folderName: String? = null,
  /** What it's called if she leaves the name empty (shown as the hint). */
  suggestedName: String = stringResource(R.string.new_notebook),
  defaultCover: Int,
  onDismiss: () -> Unit,
  importImage: suspend (android.net.Uri) -> String?,
  onAddPaper: (CustomPaper) -> Unit,
  onRemovePaper: (CustomPaper) -> Unit,
  onCreate: (NewNotebook) -> Unit,
) {
  val c = Tali.colors
  var title by remember { mutableStateOf(TextFieldValue("")) }
  var paper by remember { mutableStateOf(settings.defaultPaper) }
  var custom by remember { mutableStateOf(settings.defaultCustomPaper?.takeIf { id -> settings.customPapers.any { it.id == id } }) }
  var paged by remember { mutableStateOf(settings.defaultPaged) }
  var dark by remember { mutableStateOf(false) }
  var landscape by remember { mutableStateOf(settings.defaultLandscape) }
  var cover by remember { mutableStateOf(defaultCover) }
  var makingPaper by remember { mutableStateOf(false) }
  val create = { onCreate(NewNotebook(title.text.ifBlank { suggestedName }, paper, custom, paged, dark, cover, landscape = paged && landscape)) }
  DialogCard(onDismiss, width = 640) {
    // The choices scroll and give way first, so Cancel and Create below always fit on screen.
    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          Text(stringResource(R.string.new_notebook), style = MaterialTheme.typography.titleLarge, color = c.ink)
          if (folderName != null) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.in_n, folderName), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
          }
        }
        com.tali.notes.ui.components.DriftingCloud(width = 64.dp, variant = 2, driftDp = 4f)
      }
      Spacer(Modifier.height(10.dp))
      // Done on the keyboard just puts the keyboard away: she keeps choosing the paper and the
      // rest, and the notebook is made only with Create.
      val focus = androidx.compose.ui.platform.LocalFocusManager.current
      val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
      TitleField(title, { title = it }, suggestedName, autoFocus = false) {
        keyboard?.hide()
        focus.clearFocus()
      }
      // Laid out compactly so the whole card fits lying down without scrolling.
      Spacer(Modifier.height(14.dp))
      SectionLabel(stringResource(R.string.paper))
      Spacer(Modifier.height(4.dp))
      PaperPicker(
        style = paper,
        customId = custom,
        customs = settings.customPapers,
        onStyle = {
          paper = it
          custom = null
        },
        onCustom = { custom = it.id },
        onAdd = { makingPaper = true },
        onRemove = {
          if (custom == it.id) custom = null
          onRemovePaper(it)
        },
        tileWidth = 80.dp,
        perRow = 7,
        dark = dark || c.isDark,
        looks = settings.paperLooks,
      )
      Spacer(Modifier.height(12.dp))
      // Pages and orientation side by side. With an endless canvas, orientation fades out but keeps
      // its place, so nothing below jumps.
      Row {
        Column(Modifier.weight(1f)) {
          SectionLabel(stringResource(R.string.pages))
          Spacer(Modifier.height(6.dp))
          Segmented(listOf(true, false), paged, { if (it) tr(R.string.a4_pages) else tr(R.string.endless) }, { paged = it }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(14.dp))
        val shown by androidx.compose.animation.core.animateFloatAsState(if (paged) 1f else 0f, androidx.compose.animation.core.tween(if (paged) 200 else 140), label = "orientation")
        Column(Modifier.weight(1f).graphicsLayer { alpha = shown }) {
          SectionLabel(stringResource(R.string.orientation))
          Spacer(Modifier.height(6.dp))
          Segmented(listOf(false, true), landscape, { if (it) tr(R.string.landscape) else tr(R.string.portrait) }, { if (paged) landscape = it }, Modifier.fillMaxWidth())
        }
      }
      Spacer(Modifier.height(6.dp))
      Text(
        when {
          !paged -> stringResource(R.string.one_page_that_goes_on_forever)
          landscape -> stringResource(R.string.wide_a4_pages_on_their_side)
          else -> stringResource(R.string.upright_a4_pages_one_under_the)
        },
        style = MaterialTheme.typography.bodySmall,
        color = c.inkMuted,
      )
      Spacer(Modifier.height(6.dp))
      ToggleRow(stringResource(R.string.dark_paper), dark, { dark = it }, detail = stringResource(R.string.a_black_page_your_pens_switch))
      Spacer(Modifier.height(8.dp))
      SectionLabel(stringResource(R.string.cover))
      Spacer(Modifier.height(6.dp))
      val ring = rememberSlidingSelection()
      androidx.compose.foundation.layout.FlowRow(
        Modifier.padding(start = 3.dp).slidingHighlight(ring, cover, SolidColor(c.ink), corner = 100.dp, ring = 2.dp, above = true),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        com.tali.notes.data.PICK_COLORS.forEach { col ->
          Box(
            Modifier.slidingOption(ring, col)
              .size(26.dp)
              .clip(androidx.compose.foundation.shape.CircleShape)
              .background(Color(col))
              .clickable(interactionSource = null, indication = null, role = Role.RadioButton) { cover = col }
          )
        }
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
      PrimaryButton(stringResource(R.string.create)) { create() }
    }
  }
  if (makingPaper) {
    NewPaperDialog(onDismiss = { makingPaper = false }, importImage = importImage) {
      onAddPaper(it)
      custom = it.id
      makingPaper = false
    }
  }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) =
  NameDialog(title = stringResource(R.string.rename_notebook), initial = current, placeholder = stringResource(R.string.notebook_name), confirm = stringResource(R.string.rename), onDismiss = onDismiss, onConfirm = onRename)

/** A dialog with one name field, used for renaming and for new folders. */
@Composable
fun NameDialog(title: String, initial: String, placeholder: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  val c = Tali.colors
  var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
  DialogCard(onDismiss) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(18.dp))
    TitleField(value, { value = it }, placeholder) { onConfirm(value.text) }
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
      PrimaryButton(confirm) { onConfirm(value.text) }
    }
  }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val c = Tali.colors
  // Confirmations don't darken the screen (the owner found the tint heavy-handed).
  DialogCard(onDismiss, width = 460, dim = false) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
      if (destructive) QuietButton(confirm, color = c.danger, onClick = onConfirm) else PrimaryButton(confirm, onClick = onConfirm)
    }
  }
}

/**
 * The middle of a card, between its title and its buttons. Cards are laid out to fit the screen
 * without scrolling; on a screen too short for one, this part scrolls, so the buttons under it
 * are never cut off.
 */
@Composable
fun androidx.compose.foundation.layout.ColumnScope.CardBody(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
  Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()), content = content)
}
