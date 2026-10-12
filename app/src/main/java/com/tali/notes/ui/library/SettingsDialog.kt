package com.tali.notes.ui.library

import com.tali.notes.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpOffset
import com.tali.notes.AppLanguage
import com.tali.notes.appLocale
import com.tali.notes.ui.components.EaseOutStrong
import kotlinx.coroutines.delay
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
    SectionLabel(stringResource(R.string.language))
    Spacer(Modifier.height(8.dp))
    LanguagePicker(Modifier.fillMaxWidth())
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

/**
 * The app's language: a field naming the one in use that opens a list of them all, each named in
 * itself (and, under it, in the language shown now), the tablet's own first. Rows drift in one after
 * another as it opens. Choosing one restarts the screen in it.
 */
@Composable
private fun LanguagePicker(modifier: Modifier = Modifier) {
  val c = Tali.colors
  val context = LocalContext.current
  val current = remember { AppLanguage.current(context) }
  var open by remember { mutableStateOf(false) }
  var width by remember { mutableIntStateOf(0) }
  val density = LocalDensity.current
  val turn by animateFloatAsState(if (open) 180f else 0f, tween(240, easing = EaseOutStrong), label = "chevron")
  val tablet = remember { AppLanguage.tablet(context) }
  Box(modifier) {
    Row(
      Modifier.fillMaxWidth().height(52.dp).onSizeChanged { width = it.width }.clip(RoundedCornerShape(16.dp)).background(c.surface)
        .border(1.dp, if (open) c.accent else c.line, RoundedCornerShape(16.dp))
        .clickable(onClickLabel = stringResource(R.string.language), role = Role.Button) { open = true }
        .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Language, null, tint = c.accent, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(12.dp))
      Text(if (current == null) stringResource(R.string.system_default) else AppLanguage.nativeName(current), style = MaterialTheme.typography.titleSmall.copy(fontFamily = familyFor(current)), color = c.ink)
      if (current == null) {
        Spacer(Modifier.width(8.dp))
        Text(AppLanguage.nativeName(tablet), style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
      }
      Spacer(Modifier.weight(1f))
      Icon(Icons.Outlined.KeyboardArrowDown, null, tint = c.inkMuted, modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn })
    }
    DropdownMenu(
      expanded = open,
      onDismissRequest = { open = false },
      shape = RoundedCornerShape(16.dp),
      containerColor = c.surface,
      offset = DpOffset(0.dp, 6.dp),
      modifier = Modifier.width(with(density) { width.toDp() }).heightIn(max = 420.dp),
    ) {
      (listOf<String?>(null) + AppLanguage.available).forEachIndexed { i, tag ->
        val on = tag == current
        // Each row drifts up into place a moment after the one above it.
        val shown = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
          delay(i * 30L)
          shown.animateTo(1f, tween(260, easing = EaseOutStrong))
        }
        val title = if (tag == null) stringResource(R.string.system_default) else AppLanguage.nativeName(tag)
        val detail =
          if (tag == null) stringResource(R.string.follows_the_tablet_n, AppLanguage.nativeName(tablet))
          else AppLanguage.nameIn(tag, appLocale()).takeIf { it != title }
        DropdownMenuItem(
          text = {
            Column {
              Text(title, style = MaterialTheme.typography.titleSmall.copy(fontFamily = familyFor(tag)), color = if (on) c.accent else c.ink)
              if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
            }
          },
          trailingIcon = { if (on) Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(20.dp)) },
          onClick = {
            open = false
            if (!on) context.findActivity()?.let { AppLanguage.set(it, tag) }
          },
          modifier = Modifier.graphicsLayer {
            alpha = shown.value
            translationY = (1f - shown.value) * 8.dp.toPx()
          },
        )
      }
    }
  }
}

/** Each language's own name in the font the app uses for it, so עברית and العربية look as firm as the rest. */
@Composable
private fun familyFor(tag: String?) =
  when (tag) {
    "he" -> com.tali.notes.theme.Heebo
    "ar" -> com.tali.notes.theme.Vazirmatn
    null -> MaterialTheme.typography.titleSmall.fontFamily
    else -> com.tali.notes.theme.Figtree
  }
