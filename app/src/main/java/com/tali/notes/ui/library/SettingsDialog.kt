package com.tali.notes.ui.library

import com.tali.notes.tr
import androidx.compose.ui.res.stringResource
import com.tali.notes.R
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
fun SettingsDialog(settings: AppSettings, onTheme: (ThemeMode) -> Unit, onMinimal: (Boolean) -> Unit, onPdfsToLibrary: (Boolean) -> Unit, onTour: () -> Unit, onDismiss: () -> Unit) {
  val c = Tali.colors
  DialogCard(onDismiss, width = 520) {
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(18.dp))
    CardBody {
    // The app's language: the tablet's, or one of its own. Each language is named in itself.
    val context = androidx.compose.ui.platform.LocalContext.current
    val language = androidx.compose.runtime.remember { com.tali.notes.AppLanguage.current(context) }
    SectionLabel(stringResource(R.string.language))
    Spacer(Modifier.height(8.dp))
    Segmented(
      listOf("") + com.tali.notes.AppLanguage.available,
      language ?: "",
      { tag -> if (tag.isEmpty()) tr(R.string.system_default) else com.tali.notes.AppLanguage.nativeName(tag) },
      { tag -> context.findActivity()?.let { com.tali.notes.AppLanguage.set(it, tag.ifEmpty { null }) } },
      Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel(stringResource(R.string.theme))
    Spacer(Modifier.height(8.dp))
    Segmented(
      listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
      settings.themeMode,
      { when (it) { ThemeMode.SYSTEM -> tr(R.string.system_default); ThemeMode.LIGHT -> tr(R.string.light); ThemeMode.DARK -> tr(R.string.dark) } },
      { com.tali.notes.ui.components.ThemeFade.beforeChange(); onTheme(it) },
      Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel(stringResource(R.string.style))
    Spacer(Modifier.height(8.dp))
    Segmented(listOf(false, true), settings.minimal, { if (it) tr(R.string.minimal) else tr(R.string.cloudy) }, { com.tali.notes.ui.components.ThemeFade.beforeChange(); onMinimal(it) }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(6.dp))
    Text(
      if (settings.minimal) stringResource(R.string.clean_edges_on_bars_and_cards)
      else stringResource(R.string.soft_cloud_edges_on_bars_and),
      style = MaterialTheme.typography.bodySmall,
      color = c.inkMuted,
    )
    Spacer(Modifier.height(18.dp))
    SectionLabel(stringResource(R.string.pdfs_from_other_apps))
    Spacer(Modifier.height(4.dp))
    com.tali.notes.ui.components.ToggleRow(
      stringResource(R.string.add_them_to_my_notebooks),
      settings.pdfsToLibrary,
      onPdfsToLibrary,
      detail = if (settings.pdfsToLibrary) stringResource(R.string.a_pdf_you_open_with_tali)
      else stringResource(R.string.a_pdf_you_open_with_tali_2),
    )
    }
    Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      // The walk through the app, again.
      com.tali.notes.ui.components.QuietButton(stringResource(R.string.take_the_tour), onClick = onTour)
      Spacer(Modifier.weight(1f))
      PrimaryButton(stringResource(R.string.done), onClick = onDismiss)
    }
  }
}

private tailrec fun android.content.Context.findActivity(): android.app.Activity? =
  when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
  }
