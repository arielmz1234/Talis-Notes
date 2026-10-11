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
  val L100 = Color(0xFFE7D1FE)
  val L200 = Color(0xFFDFC4FF)
  val L300 = Color(0xFFD7BAFF)
  val L400 = Color(0xFFD1AEFF)
}

/** Light blues that pair with the lilacs, lightest to deepest. */
object Sky {
  val S50 = Color(0xFFE8F1FF)
  val S100 = Color(0xFFD6E6FF)
  val S200 = Color(0xFFBFD9FF)
  val S300 = Color(0xFF9DC2FA)
  val S400 = Color(0xFF7FA6F2)
  val Blue = Color(0xFF4F7BE8)
  val BlueDeep = Color(0xFF2F5AD1)
}

private val Light =
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

private val Dark =
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
  )

/**
 * The notebook's toolbars in dark mode while the page is white: soft lilac clouds rather than
 * deep plum, so the bars sit gently on the paper instead of looking like holes in it.
 */
private val Mist =
  Light.copy(
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
 * Colours for the bars over a notebook: dark ones on dark paper; on white paper the usual light
 * ones, or in dark mode the softer [Mist] lilac.
 */
fun notebookColors(systemDark: Boolean, darkPaper: Boolean): TaliColors =
  when {
    darkPaper -> Dark
    systemDark -> Mist
    else -> Light
  }

val LocalTaliColors = staticCompositionLocalOf { Light }

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

// Hebrew isn't tracked (spread-out letters read as broken words), so no letter spacing at all.
private fun TextStyle.inHeebo() = copy(fontFamily = Heebo, letterSpacing = 0.em)

private val HebrewTypography =
  TaliTypography.run {
    copy(
      displayLarge = displayLarge.inHeebo(), displayMedium = displayMedium.inHeebo(), displaySmall = displaySmall.inHeebo(),
      headlineLarge = headlineLarge.inHeebo(), headlineMedium = headlineMedium.inHeebo(), headlineSmall = headlineSmall.inHeebo(),
      titleLarge = titleLarge.inHeebo(), titleMedium = titleMedium.inHeebo(), titleSmall = titleSmall.inHeebo(),
      bodyLarge = bodyLarge.inHeebo(), bodyMedium = bodyMedium.inHeebo(), bodySmall = bodySmall.inHeebo(),
      labelLarge = labelLarge.inHeebo(), labelMedium = labelMedium.inHeebo(), labelSmall = labelSmall.inHeebo(),
    )
  }

object Tali {
  val colors: TaliColors
    @Composable get() = LocalTaliColors.current
}

@Composable
fun TaliTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) = TaliColorsScope(if (darkTheme) Dark else Light, content)

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
  // In Hebrew every style is set in Heebo (same sizes and weights, no letter spacing).
  val hebrew = androidx.compose.ui.platform.LocalConfiguration.current.locales[0].language.let { it == "he" || it == "iw" }
  CompositionLocalProvider(LocalTaliColors provides c) { MaterialTheme(colorScheme = scheme, typography = if (hebrew) HebrewTypography else TaliTypography) {
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
