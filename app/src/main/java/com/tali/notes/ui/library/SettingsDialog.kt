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
import com.tali.notes.ui.components.ButtonRow
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
import com.tali.notes.theme.ColorTheme
import com.tali.notes.theme.TaliColors
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import com.tali.notes.ui.library.CardBody
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.SectionLabel
import com.tali.notes.ui.components.Segmented
import com.tali.notes.ui.components.slidingHighlight
import com.tali.notes.ui.components.slidingOption
import androidx.compose.foundation.interaction.collectIsPressedAsState

/** The app's settings, from the library's sidebar: light or dark, and the cloudy or minimal look. */
@Composable
fun SettingsDialog(settings: AppSettings, onTheme: (ThemeMode) -> Unit, onColors: (com.tali.notes.theme.ColorTheme) -> Unit, onMinimal: (Boolean) -> Unit, onPdfsToLibrary: (Boolean) -> Unit, onTour: () -> Unit, onFeatures: () -> Unit, onDismiss: () -> Unit) {
  val c = Tali.colors
  DialogCard(onDismiss, width = 600) {
    Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(18.dp))
    CardBody {
    SectionLabel(stringResource(R.string.language))
    Spacer(Modifier.height(8.dp))
    LanguagePicker(Modifier.fillMaxWidth())
    Spacer(Modifier.height(18.dp))
    SectionLabel(stringResource(R.string.colours))
    Spacer(Modifier.height(8.dp))
    ColorThemePicker(settings.colorTheme) { com.tali.notes.ui.components.ThemeFade.beforeChange(); onColors(it) }
    Spacer(Modifier.height(18.dp))
    // Light or dark beside cloudy or minimal, so the card still fits a landscape tablet; one under
    // the other on a narrow phone, where side by side cut "System default" short.
    val themeChoice: @Composable (Modifier) -> Unit = { m ->
      Column(m) {
        SectionLabel(stringResource(R.string.theme))
        Spacer(Modifier.height(8.dp))
        Segmented(
          listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK),
          settings.themeMode,
          { when (it) { ThemeMode.SYSTEM -> tr(R.string.system_default); ThemeMode.LIGHT -> tr(R.string.light); ThemeMode.DARK -> tr(R.string.dark) } },
          { com.tali.notes.ui.components.ThemeFade.beforeChange(); onTheme(it) },
          Modifier.fillMaxWidth(),
        )
      }
    }
    val styleChoice: @Composable (Modifier) -> Unit = { m ->
      Column(m) {
        SectionLabel(stringResource(R.string.style))
        Spacer(Modifier.height(8.dp))
        Segmented(listOf(false, true), settings.minimal, { if (it) tr(R.string.minimal) else tr(R.string.cloudy) }, { com.tali.notes.ui.components.ThemeFade.beforeChange(); onMinimal(it) }, Modifier.fillMaxWidth())
      }
    }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
      if (maxWidth < 440.dp) {
        Column {
          themeChoice(Modifier.fillMaxWidth())
          Spacer(Modifier.height(14.dp))
          styleChoice(Modifier.fillMaxWidth())
        }
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
          themeChoice(Modifier.weight(1.6f))
          styleChoice(Modifier.weight(1f))
        }
      }
    }
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
    ButtonRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
      // The walk through the app, again.
      com.tali.notes.ui.components.QuietButton(stringResource(R.string.take_the_tour), onClick = onTour)
      Spacer(Modifier.width(8.dp))
      // The smaller things the app can do, and how to use or turn them on.
      com.tali.notes.ui.components.QuietButton(stringResource(R.string.tips_and_tricks), onClick = onFeatures)
      Spacer(Modifier.weight(1f))
      PrimaryButton(stringResource(R.string.done), onClick = onDismiss)
    }
    // Who made it, and which version this is.
    Spacer(Modifier.height(14.dp))
    Text(
      stringResource(R.string.made_by_n, "Ariel M.") + " · " + com.tali.notes.BuildConfig.VERSION_NAME,
      style = MaterialTheme.typography.labelSmall,
      color = c.inkFaint,
      modifier = Modifier.fillMaxWidth(),
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
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

/**
 * The four colour families, each tile a small picture of the app in it (in light or dark, as the app
 * is now): its sky with a cloud, a notebook card with a spine and two lines of writing, and its +
 * button in the accent's two tones. The chosen one wears a ring that glides between them.
 */
@Composable
private fun ColorThemePicker(selected: ColorTheme, onPick: (ColorTheme) -> Unit) {
  val c = Tali.colors
  val glide = com.tali.notes.ui.components.rememberSlidingSelection()
  Row(
    Modifier.fillMaxWidth().slidingHighlight(glide, selected, SolidColor(c.accent), 20.dp, ring = 2.5.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    ColorTheme.entries.forEach { theme ->
      val p = if (c.isDark) theme.dark else theme.light
      val on = theme == selected
      val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
      val pressed by press.collectIsPressedAsState()
      val sink by animateFloatAsState(if (pressed) 0.96f else 1f, tween(if (pressed) 90 else 180), label = "themeSink")
      Column(
        Modifier.weight(1f)
          .slidingOption(glide, theme)
          .graphicsLayer { scaleX = sink; scaleY = sink }
          .clip(RoundedCornerShape(20.dp))
          .clickable(interactionSource = press, indication = null, role = Role.RadioButton) { onPick(theme) }
          .padding(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        ThemePreview(p, Modifier.fillMaxWidth().height(78.dp))
        Spacer(Modifier.height(6.dp))
        // Shrinks rather than cut off ("Peac") on a narrow phone or with large text.
        com.tali.notes.ui.components.FitText(stringResource(themeName(theme)), style = MaterialTheme.typography.labelLarge, color = if (on) c.accent else c.inkMuted)

        Spacer(Modifier.height(2.dp))
      }
    }
  }
}

private fun themeName(t: ColorTheme) =
  when (t) {
    ColorTheme.LILAC -> R.string.colours_lilac
    ColorTheme.SKY -> R.string.colours_sky
    ColorTheme.SAGE -> R.string.colours_sage
    ColorTheme.PEACH -> R.string.colours_peach
  }

/** A tiny library in [p]'s colours: sky and a cloud above, a notebook card, the + button. */
@Composable
private fun ThemePreview(p: TaliColors, modifier: Modifier) {
  Canvas(modifier.clip(RoundedCornerShape(15.dp)).border(1.dp, p.line, RoundedCornerShape(15.dp))) {
    val w = size.width
    val h = size.height
    val d = density
    drawRect(p.background)
    // The sky band, fading into the page.
    drawRect(Brush.linearGradient(listOf(p.skyFrom, p.skyTo), end = Offset(w, h * 0.5f)), size = Size(w, h * 0.55f))
    drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to p.background, startY = h * 0.25f, endY = h * 0.55f), size = Size(w, h * 0.55f))
    // A small cloud: three puffs on a flat base.
    val cloud = Color(p.skyCloudTop)
    val cx = w * 0.3f
    val cy = h * 0.27f
    drawCircle(cloud, 7f * d, Offset(cx - 7f * d, cy + 2f * d))
    drawCircle(cloud, 9.5f * d, Offset(cx + 2f * d, cy - 1f * d))
    drawCircle(cloud, 6.5f * d, Offset(cx + 11f * d, cy + 3f * d))
    drawRoundRect(cloud, Offset(cx - 13f * d, cy + 2f * d), Size(30f * d, 7f * d), CornerRadius(4f * d))
    // A notebook card: white, with a coloured spine and two lines of writing.
    val card = Offset(w * 0.12f, h * 0.5f)
    val cs = Size(w * 0.5f, h * 0.38f)
    drawRoundRect(p.glow.copy(alpha = 0.12f), card + Offset(0f, 2f * d), cs, CornerRadius(6f * d))
    drawRoundRect(p.surface, card, cs, CornerRadius(6f * d))
    drawRoundRect(p.lilacDeep, card, Size(4f * d, cs.height), CornerRadius(2f * d))
    drawLine(p.inkFaint, card + Offset(9f * d, cs.height * 0.38f), card + Offset(cs.width * 0.8f, cs.height * 0.38f), 1.6f * d, cap = StrokeCap.Round)
    drawLine(p.blue, card + Offset(9f * d, cs.height * 0.68f), card + Offset(cs.width * 0.55f, cs.height * 0.68f), 1.6f * d, cap = StrokeCap.Round)
    // The + button in the accent's two tones.
    val b = 18f * d
    val bo = Offset(w * 0.86f - b, h * 0.62f)
    drawRoundRect(Brush.linearGradient(listOf(p.accent, p.blue), start = bo, end = bo + Offset(b, b)), bo, Size(b, b), CornerRadius(6f * d))
    val m = bo + Offset(b / 2, b / 2)
    drawLine(p.onAccent, m - Offset(4.5f * d, 0f), m + Offset(4.5f * d, 0f), 1.8f * d, cap = StrokeCap.Round)
    drawLine(p.onAccent, m - Offset(0f, 4.5f * d), m + Offset(0f, 4.5f * d), 1.8f * d, cap = StrokeCap.Round)
  }
}
