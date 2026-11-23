package com.tali.notes.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.tali.notes.R

/**
 * Lilac and sky: soft lilac surfaces from Tali's palette (#E6D8FE → #D1AEFF) meeting light blues
 * (#E6F0FF → #7FA6F2), plum-tinted ink for text, and violet-to-blue for actions so buttons stay
 * readable on the pale tones.
 */
@Immutable
data class TaliColors(
  val background: Color,
  val surface: Color,
  val sidebar: Color,
  val sunken: Color,
  val line: Color,
  val ink: Color,
  val inkMuted: Color,
  val inkFaint: Color,
  val accent: Color,
  val onAccent: Color,
  val accentSoft: Color,
  val lilac: Color,
  val lilacDeep: Color,
  val amber: Color,
  val danger: Color,
  val scrim: Color,
  val paper: Color,
  /** Secondary blue for tools, handles and links. */
  val blue: Color,
  val blueSoft: Color,
  val sky: Color,
  /** Bottom of the sidebar gradient (lilac at the top fading to light blue). */
  val sidebarEnd: Color,
  val isDark: Boolean,
  /** Cloud surfaces (toolbars, cards, dialogs): top, belly, hairline edge and the shadow below. */
  val cloudTop: Color,
  val cloudBelly: Color,
  val cloudEdge: Color,
  val cloudShadow: Color,
  /** The sky band behind the library's heading, left to right. */
  val skyFrom: Color = Color(0xFFBFD4FF),
  val skyTo: Color = Color(0xFFDCCBFF),
  /** The big sky clouds: top, belly, underside and their shadow (ARGB). */
  val skyCloudTop: Int = 0xFFFFFFFF.toInt(),
  val skyCloudMid: Int = 0xFFD3D9FA.toInt(),
  val skyCloudLow: Int = 0xFFB3B8EF.toInt(),
  val skyCloudShadow: Int = 0x296256C8,
  /** Tinted shadows (cards, the gliding highlight) and the mist's tone in dark mode. */
  val glow: Color = Color(0xFF3B2A7A),
  val dusk: Color = Color(0xFFB7A6EE),
  /** Around the pages in a notebook. */
  val desk: Color = Color(0xFFE9E4F2),
) {
  /** The signature violet-to-blue used on primary buttons. */
  val accentBrush: Brush
    get() = Brush.linearGradient(listOf(accent, blue))

  /** Lilac-to-sky wash for highlights and selected rows. */
  val washBrush: Brush
    get() = Brush.horizontalGradient(listOf(lilac, sky))
}

/** Tali's palette, lightest to deepest. */
object Lilac {
  val L50 = Color(0xFFE6D8FE)
  val L200 = Color(0xFFDFC4FF)
  val L400 = Color(0xFFD1AEFF)
}

/** Light blues that pair with the lilacs, lightest to deepest. */
object Sky {
  val S50 = Color(0xFFE8F1FF)
  val S100 = Color(0xFFD6E6FF)
  val Blue = Color(0xFF4F7BE8)
}

private val LilacLight =
  TaliColors(
    background = Color(0xFFF7F3FF),
    surface = Color(0xFFFFFFFF),
    sidebar = Color(0xFFEFE6FF),
    sunken = Color(0xFFEEE6FC),
    line = Color(0xFFE3D8F7),
    ink = Color(0xFF1F1A2E),
    inkMuted = Color(0xFF665D7D),
    inkFaint = Color(0xFF9D94B5),
    accent = Color(0xFF7A4ED6),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Lilac.L50,
    lilac = Lilac.L200,
    lilacDeep = Lilac.L400,
    amber = Color(0xFFF2B53A),
    danger = Color(0xFFD1435B),
    scrim = Color(0x661F1A2E),
    paper = Color(0xFFFFFFFF),
    blue = Sky.Blue,
    blueSoft = Sky.S50,
    sky = Sky.S100,
    sidebarEnd = Color(0xFFE6EFFF),
    isDark = false,
    cloudTop = Color(0xFFFFFFFF),
    cloudBelly = Color(0xFFE3E8FC),
    cloudEdge = Color(0xFFCDD4F5),
    cloudShadow = Color(0x3D6256C8),
  )

private val LilacDark =
  TaliColors(
    background = Color(0xFF15111E),
    surface = Color(0xFF211B2E),
    sidebar = Color(0xFF1B1627),
    sunken = Color(0xFF2B2340),
    line = Color(0xFF382E50),
    ink = Color(0xFFF0EAFF),
    inkMuted = Color(0xFFB3A8CC),
    inkFaint = Color(0xFF7D7394),
    accent = Color(0xFFC4A8FF),
    onAccent = Color(0xFF21163C),
    accentSoft = Color(0xFF3A2D5C),
    lilac = Color(0xFF4A3A73),
    lilacDeep = Color(0xFF6A54A3),
    amber = Color(0xFFF2B53A),
    danger = Color(0xFFFF7A8E),
    scrim = Color(0x99000000),
    paper = Color(0xFFFFFFFF),
    blue = Color(0xFF8FB0FF),
    blueSoft = Color(0xFF22304F),
    sky = Color(0xFF2E4470),
    sidebarEnd = Color(0xFF161C2B),
    isDark = true,
    cloudTop = Color(0xFF3A335E),
    cloudBelly = Color(0xFF2B2548),
    cloudEdge = Color(0xFF514980),
    cloudShadow = Color(0x38000000),
    skyFrom = Color(0xFF1D2A52),
    skyTo = Color(0xFF30245A),
    skyCloudTop = 0xFFE4E1F7.toInt(),
    skyCloudMid = 0xFF9AA0DC.toInt(),
    skyCloudLow = 0xFF7C7CC8.toInt(),
    skyCloudShadow = 0x40000000,
    desk = Color(0xFF2A2338),
  )

/**
 * The notebook's toolbars in dark mode while the page is white: soft lilac clouds rather than
 * deep plum, so the bars sit gently on the paper instead of looking like holes in it.
 */
private val LilacMist =
  LilacLight.copy(
    surface = Color(0xFFF5EFFF),
    sunken = Color(0xFFE6DBFB),
    line = Color(0xFFD5C7F2),
    ink = Color(0xFF261D3B),
    inkMuted = Color(0xFF5D5279),
    inkFaint = Color(0xFF8C82AA),
    accentSoft = Color(0xFFE0D1FD),
    blueSoft = Color(0xFFE1EAFF),
    cloudTop = Color(0xFFF1E9FF),
    cloudBelly = Color(0xFFDCCEF9),
    cloudEdge = Color(0xFFC6B5EE),
    cloudShadow = Color(0x4D2A1F55),
  )

/**
 * The app's colour families (Settings › Colours). Each has a light and a dark look, and a soft "mist"
 * for a notebook's bars in dark mode while the page is white. The first is Tali's original lilac.
 */
enum class ColorTheme(val light: TaliColors, val dark: TaliColors, val mist: TaliColors) {
  /** Lilac meeting light blue: the original. */
  LILAC(LilacLight, LilacDark, LilacMist),
  /** Calm and neutral: cool greys with a clear blue. */
  SKY(SkyLight, SkyDark, SkyMist),
  /** Soft green, like fresh leaves, with a teal partner. */
  SAGE(SageLight, SageDark, SageMist),
  /** A warm sunset: peach, rose and apricot. */
  PEACH(PeachLight, PeachDark, PeachMist),
}

/** The family chosen in Settings, for the few places that pick a palette themselves. */
val LocalColorTheme = staticCompositionLocalOf { ColorTheme.LILAC }

/** The dark look of the chosen family (the camera always uses it). */
val DarkColors: TaliColors
  @Composable get() = LocalColorTheme.current.dark

/**
 * Colours for the bars over a notebook: dark ones on dark paper; on white paper the usual light
 * ones, or in dark mode the softer mist.
 */
@Composable
fun notebookColors(systemDark: Boolean, darkPaper: Boolean): TaliColors {
  val t = LocalColorTheme.current
  return when {
    darkPaper -> t.dark
    systemDark -> t.mist
    else -> t.light
  }
}

val LocalTaliColors = staticCompositionLocalOf { LilacLight }

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(R.font.figtree, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

@OptIn(ExperimentalTextApi::class)
private fun bricolage(weight: Int, opticalSize: Float) =
  Font(
    R.font.bricolage,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize), FontVariation.width(92f)),
  )

val Figtree = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700))
val BricolageDisplay = FontFamily(bricolage(700, 72f), bricolage(600, 36f))
val BricolageTitle = FontFamily(bricolage(600, 24f))

@OptIn(ExperimentalTextApi::class)
private fun heebo(weight: Int) = Font(R.font.heebo, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Hebrew: Figtree and Bricolage have no Hebrew letters, so the Hebrew app is set in Heebo throughout. */
val Heebo = FontFamily(heebo(400), heebo(500), heebo(600), heebo(700))

@OptIn(ExperimentalTextApi::class)
private fun vazirmatn(weight: Int) = Font(R.font.vazirmatn, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Arabic: set in Vazirmatn throughout (open and light at small sizes, with line heights close to Figtree's). */
val Vazirmatn = FontFamily(vazirmatn(400), vazirmatn(500), vazirmatn(600), vazirmatn(700))

private val TaliTypography =
  Typography(
    displayLarge = TextStyle(fontFamily = BricolageDisplay, fontWeight = FontWeight(700), fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = (-0.02).em),
    headlineMedium = TextStyle(fontFamily = BricolageDisplay, fontWeight = FontWeight(600), fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = BricolageTitle, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.02.em),
    labelSmall = TextStyle(fontFamily = Figtree, fontWeight = FontWeight(600), fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.06.em),
  )

/**
 * Every style in [family], same sizes and weights. Hebrew and Arabic aren't tracked (spread-out
 * letters read as broken words, and Arabic's joined letters come apart), so no letter spacing at all.
 */
private fun typographyIn(family: FontFamily): Typography {
  fun TextStyle.inIt() = copy(fontFamily = family, letterSpacing = 0.em)
  return TaliTypography.run {
    copy(
      displayLarge = displayLarge.inIt(), displayMedium = displayMedium.inIt(), displaySmall = displaySmall.inIt(),
      headlineLarge = headlineLarge.inIt(), headlineMedium = headlineMedium.inIt(), headlineSmall = headlineSmall.inIt(),
      titleLarge = titleLarge.inIt(), titleMedium = titleMedium.inIt(), titleSmall = titleSmall.inIt(),
      bodyLarge = bodyLarge.inIt(), bodyMedium = bodyMedium.inIt(), bodySmall = bodySmall.inIt(),
      labelLarge = labelLarge.inIt(), labelMedium = labelMedium.inIt(), labelSmall = labelSmall.inIt(),
    )
  }
}

private val HebrewTypography = typographyIn(Heebo)
private val ArabicTypography = typographyIn(Vazirmatn)

object Tali {
  val colors: TaliColors
    @Composable get() = LocalTaliColors.current
}

@Composable
fun TaliTheme(darkTheme: Boolean = isSystemInDarkTheme(), theme: ColorTheme = ColorTheme.LILAC, content: @Composable () -> Unit) =
  CompositionLocalProvider(LocalColorTheme provides theme) { TaliColorsScope(if (darkTheme) theme.dark else theme.light, content) }

/** The minimal look (Settings → Style): no cloud bumps, no sky clouds. */
val LocalMinimal = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Runs [content] with the palette [c], Material's colours included (menus, text fields). */
@Composable
fun TaliColorsScope(c: TaliColors, content: @Composable () -> Unit) {
  val scheme =
    if (c.isDark) {
      darkColorScheme(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentSoft,
        background = c.background,
        surface = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.sunken,
        onSurface = c.ink,
        onSurfaceVariant = c.inkMuted,
        outline = c.line,
        outlineVariant = c.line,
        error = c.danger,
      )
    } else {
      lightColorScheme(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentSoft,
        background = c.background,
        surface = c.surface,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surface,
        surfaceContainerHighest = c.sunken,
        onSurface = c.ink,
        onSurfaceVariant = c.inkMuted,
        outline = c.line,
        outlineVariant = c.line,
        error = c.danger,
      )
    }
  // In Hebrew every style is set in Heebo, in Arabic in Vazirmatn (same sizes and weights, no letter spacing).
  val typography =
    when (androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language) {
      "he", "iw" -> HebrewTypography
      "ar" -> ArabicTypography
      else -> TaliTypography
    }.let { t ->
      // A notebook or the camera keeps the English layout in Hebrew and Arabic: their words still read
      // right to left there (a sentence's full stop at its end, not its start).
      val forcedLtr = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr &&
        androidx.compose.ui.platform.LocalConfiguration.current.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
      if (forcedLtr) byContent(t) else t
    }
  CompositionLocalProvider(LocalTaliColors provides c) { MaterialTheme(colorScheme = scheme, typography = typography) {
      // No hover highlight: with the S Pen hovering it lit rows up, and after pressing an item and
      // moving the pen away the highlight could stay behind. Pressing still shows.
      CompositionLocalProvider(
        androidx.compose.material3.LocalRippleConfiguration provides androidx.compose.material3.RippleConfiguration(
          rippleAlpha = androidx.compose.material.ripple.RippleAlpha(draggedAlpha = 0.16f, focusedAlpha = 0.1f, hoveredAlpha = 0f, pressedAlpha = 0.12f)
        ),
        content = content,
      )
    } }
}

/** Every style reading in its own text's direction. */
private fun byContent(t: androidx.compose.material3.Typography): androidx.compose.material3.Typography {
  fun androidx.compose.ui.text.TextStyle.c() = copy(textDirection = androidx.compose.ui.text.style.TextDirection.Content)
  return t.copy(
    displayLarge = t.displayLarge.c(), displayMedium = t.displayMedium.c(), displaySmall = t.displaySmall.c(),
    headlineLarge = t.headlineLarge.c(), headlineMedium = t.headlineMedium.c(), headlineSmall = t.headlineSmall.c(),
    titleLarge = t.titleLarge.c(), titleMedium = t.titleMedium.c(), titleSmall = t.titleSmall.c(),
    bodyLarge = t.bodyLarge.c(), bodyMedium = t.bodyMedium.c(), bodySmall = t.bodySmall.c(),
    labelLarge = t.labelLarge.c(), labelMedium = t.labelMedium.c(), labelSmall = t.labelSmall.c(),
  )
}
