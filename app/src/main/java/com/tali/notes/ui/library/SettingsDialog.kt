package com.tali.notes.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tali.notes.data.AppSettings
import com.tali.notes.data.ThemeMode
import com.tali.notes.theme.Tali
import com.tali.notes.ui.library.CardBody
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.SectionLabel
import com.tali.notes.ui.components.Segmented

/** The app's settings, from the library's sidebar: light or dark, and the cloudy or minimal look. */
@Composable
fun SettingsDialog(settings: AppSettings, onTheme: (ThemeMode) -> Unit, onMinimal: (Boolean) -> Unit, onPdfsToLibrary: (Boolean) -> Unit, onDismiss: () -> Unit) {
  val c = Tali.colors
  DialogCard(onDismiss, width = 520) {
    Text("Settings", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(18.dp))
    CardBody {
    SectionLabel("Theme")
    Spacer(Modifier.height(8.dp))
    Segmented(
      listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
      settings.themeMode,
      { when (it) { ThemeMode.SYSTEM -> "System default"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } },
      { com.tali.notes.ui.components.ThemeFade.beforeChange(); onTheme(it) },
      Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel("Style")
    Spacer(Modifier.height(8.dp))
    Segmented(listOf(false, true), settings.minimal, { if (it) "Minimal" else "Cloudy" }, { com.tali.notes.ui.components.ThemeFade.beforeChange(); onMinimal(it) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Text(
      if (settings.minimal) "Clean edges on bars and cards, no clouds in the sky, and more room at the top."
      else "Soft cloud edges on bars and cards, and clouds drifting across the sky.",
      style = MaterialTheme.typography.bodySmall,
      color = c.inkMuted,
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel("PDFs from other apps")
    Spacer(Modifier.height(4.dp))
    com.tali.notes.ui.components.ToggleRow(
      "Add them to my notebooks",
      settings.pdfsToLibrary,
      onPdfsToLibrary,
      detail = if (settings.pdfsToLibrary) "A PDF you open with Tali's Notes goes straight into your notebooks, ready to write on."
      else "A PDF you open with Tali's Notes opens just for looking. Tap Save to keep it and write on it.",
    )
    }
    Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      PrimaryButton("Done", onClick = onDismiss)
    }
  }
}
