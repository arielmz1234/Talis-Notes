package com.tali.notes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PaperSwatch
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.SectionLabel

@Composable
fun DialogCard(onDismiss: () -> Unit, width: Int = 520, content: @Composable () -> Unit) {
  val c = Tali.colors
  Dialog(onDismissRequest = onDismiss) {
    Column(
      Modifier.width(width.dp).clip(RoundedCornerShape(24.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(24.dp)).padding(28.dp)
    ) {
      content()
    }
  }
}

@Composable
fun TitleField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, placeholder: String, onDone: () -> Unit) {
  val c = Tali.colors
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
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
      textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
      cursorBrush = SolidColor(c.accent),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { onDone() }),
      modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
  }
}

@Composable
fun NewNotebookDialog(defaultPaper: PaperStyle, folderName: String? = null, onDismiss: () -> Unit, onCreate: (String, PaperStyle) -> Unit) {
  val c = Tali.colors
  var title by remember { mutableStateOf(TextFieldValue("")) }
  var paper by remember { mutableStateOf(defaultPaper) }
  DialogCard(onDismiss, width = 600) {
    Text("New notebook", style = MaterialTheme.typography.titleLarge, color = c.ink)
    if (folderName != null) {
      Spacer(Modifier.height(4.dp))
      Text("In $folderName", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    }
    Spacer(Modifier.height(18.dp))
    TitleField(title, { title = it }, "Statics, week 3") { onCreate(title.text, paper) }
    Spacer(Modifier.height(22.dp))
    SectionLabel("Paper")
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      PaperStyle.entries.forEach { style ->
        val on = style == paper
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.RadioButton) { paper = style }.padding(4.dp)) {
          PaperSwatch(style, Modifier.size(88.dp, 72.dp).then(if (on) Modifier.border(2.dp, c.accent, RoundedCornerShape(8.dp)) else Modifier))
          Spacer(Modifier.height(6.dp))
          Text(style.label, style = MaterialTheme.typography.labelMedium, color = if (on) c.accent else c.inkMuted)
        }
      }
    }
    Spacer(Modifier.height(26.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton("Cancel", onClick = onDismiss)
      PrimaryButton("Create") { onCreate(title.text, paper) }
    }
  }
}

@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) =
  NameDialog(title = "Rename notebook", initial = current, placeholder = "Notebook name", confirm = "Rename", onDismiss = onDismiss, onConfirm = onRename)

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
      QuietButton("Cancel", onClick = onDismiss)
      PrimaryButton(confirm) { onConfirm(value.text) }
    }
  }
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, destructive: Boolean = false, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  val c = Tali.colors
  DialogCard(onDismiss, width = 460) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(10.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton("Cancel", onClick = onDismiss)
      if (destructive) QuietButton(confirm, color = c.danger, onClick = onConfirm) else PrimaryButton(confirm, onClick = onConfirm)
    }
  }
}
