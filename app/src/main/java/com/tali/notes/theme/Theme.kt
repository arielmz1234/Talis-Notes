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
 * Lilac studio: soft lilac surfaces from Tali's palette (#E6D8FE → #D1AEFF), plum-tinted ink for
 * text, and a deeper violet for actions so buttons stay readable on the pale tones.
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
  val isDark: Boolean,
)

/** Tali's palette, lightest to deepest. */
object Lilac {
  val L50 = Color(0xFFE6D8FE)
  val L100 = Color(0xFFE7D1FE)
  val L200 = Color(0xFFDFC4FF)
  val L300 = Color(0xFFD7BAFF)
  val L400 = Color(0xFFD1AEFF)
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
    paper = Color(0xFFFDFCFF),
    isDark = false,
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
    paper = Color(0xFFFDFCFF),
    isDark = true,
  )

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

object Tali {
  val colors: TaliColors
    @Composable get() = LocalTaliColors.current
}

@Composable
fun TaliTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val c = if (darkTheme) Dark else Light
  val scheme =
    if (darkTheme) {
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
  CompositionLocalProvider(LocalTaliColors provides c) { MaterialTheme(colorScheme = scheme, typography = TaliTypography, content = content) }
}
