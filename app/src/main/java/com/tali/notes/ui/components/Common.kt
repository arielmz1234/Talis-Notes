package com.tali.notes.ui.components

import com.tali.notes.tr
import androidx.compose.ui.res.stringResource
import com.tali.notes.R
import androidx.compose.ui.graphics.graphicsLayer
import android.graphics.BitmapFactory
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tali.notes.theme.Figtree
import com.tali.notes.theme.Tali
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.SolidColor

fun relativeTime(millis: Long): String {
  val now = System.currentTimeMillis()
  val diff = now - millis
  return when {
    diff < DateUtils.MINUTE_IN_MILLIS -> tr(R.string.just_now)
    diff < DateUtils.HOUR_IN_MILLIS -> tr(R.string.n_min_ago, diff / DateUtils.MINUTE_IN_MILLIS)
    diff < DateUtils.DAY_IN_MILLIS -> tr(R.string.n_h_ago, diff / DateUtils.HOUR_IN_MILLIS)
    diff < 2 * DateUtils.DAY_IN_MILLIS -> tr(R.string.yesterday)
    else -> DateUtils.formatDateTime(null, millis, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_NO_YEAR)
  }
}

@Composable
fun rememberThumbnail(file: File, version: Long): ImageBitmap? {
  val bitmap by
    produceState<ImageBitmap?>(null, file, version) {
      value = withContext(Dispatchers.IO) { if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }
    }
  return bitmap
}

/** Floating toolbar surface: a soft white cloud with a pale belly, hairline edge and lilac shadow. */
@Composable
fun FloatingBar(modifier: Modifier = Modifier, crown: CloudCrown = CloudCrown.NONE, content: @Composable RowScope.() -> Unit) {
  Row(
    modifier.cloudSurface(22.dp, crown).padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

/** A popover panel that drops below the toolbar. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  Box(modifier.cloudSurface(22.dp, shadowDepth = 10.dp).padding(18.dp)) {
    content()
  }
}

/**
 * Round icon button with a pressed scale and optional selected tint. Inside a group of choices, pass
 * the group's [selection] and the highlight glides to it rather than appearing under it.
 */
@Composable
fun ToolButton(icon: ImageVector, label: String, selected: Boolean = false, enabled: Boolean = true, size: Dp = 44.dp, blue: Boolean = false, selection: SlidingSelection? = null, onClick: () -> Unit) {
  val c = Tali.colors
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val wash by animateColorAsState(if (selected && selection == null) (if (blue) c.blueSoft else c.accentSoft) else Color.Transparent, tween(200), label = "wash")
  val tint by animateColorAsState(
    when {
      !enabled -> c.inkFaint.copy(alpha = 0.5f)
      selected -> if (blue) c.blue else c.accent
      else -> c.ink
    },
    tween(200),
    label = "tint",
  )
  Box(
    Modifier.then(if (selection != null) Modifier.slidingOption(selection, label) else Modifier)
      .size(size)
      .scale(if (pressed) 0.9f else 1f)
      .clip(RoundedCornerShape(14.dp))
      .background(wash)
      .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
  }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Tali.colors.inkFaint, modifier = modifier)
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
  val c = Tali.colors
  Row(
    modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Switch) { onChange(!checked) }.padding(vertical = 6.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(label, style = MaterialTheme.typography.bodyMedium, color = c.ink)
      if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
    Switch(
      checked = checked,
      onCheckedChange = onChange,
      colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent, uncheckedTrackColor = c.sunken, uncheckedBorderColor = c.line),
    )
  }
}

/** Segmented control for two to four options; the white pill glides to the chosen one. */
@Composable
fun <T : Any> Segmented(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
  val c = Tali.colors
  val glide = rememberSlidingSelection()
  Row(
    modifier.clip(RoundedCornerShape(12.dp)).background(c.sunken).slidingHighlight(glide, selected, SolidColor(c.surface), 10.dp, lift = true).padding(3.dp),
    horizontalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    options.forEach { o ->
      val ink by animateColorAsState(if (o == selected) c.ink else c.inkMuted, tween(220), label = "segment")
      Box(
        Modifier.weight(1f)
          .slidingOption(glide, o)
          .clip(RoundedCornerShape(10.dp))
          // No ripple: the gliding pill is the feedback, and a grey flash under it looks muddy.
          .clickable(interactionSource = null, indication = null, role = Role.RadioButton) { onSelect(o) }
          .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
      ) {
        Text(label(o), style = MaterialTheme.typography.labelLarge, color = ink)
      }
    }
  }
}

@Composable
fun PrimaryButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = Tali.colors
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  Row(
    modifier
      .scale(if (pressed) 0.97f else 1f)
      .height(48.dp)
      .clip(RoundedCornerShape(16.dp))
      .background(c.accentBrush)
      .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
      .padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (icon != null) {
      Icon(icon, null, tint = c.onAccent, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(8.dp))
    }
    Text(text, style = MaterialTheme.typography.labelLarge, color = c.onAccent)
  }
}

@Composable
fun QuietButton(text: String, modifier: Modifier = Modifier, color: Color = Tali.colors.ink, onClick: () -> Unit) {
  val c = Tali.colors
  // A soft lilac pill (a faint wash of its own colour for a warning like Delete), no outline: a thin
  // grey border all but vanished on the sky and on white cards. It presses in like the main button.
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.96f else 1f, androidx.compose.animation.core.tween(if (pressed) 90 else 160), label = "quietPress")
  // In light mode a touch deeper than the plain soft accent, which read too faint on the pale sky.
  val wash = if (color == c.ink) (if (c.isDark) c.accentSoft else androidx.compose.ui.graphics.lerp(c.accentSoft, c.accent, 0.12f)) else color.copy(alpha = if (c.isDark) 0.12f else 0.15f)
  Box(
    modifier
      .graphicsLayer { scaleX = scale; scaleY = scale }
      .height(44.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(wash)
      .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
      .padding(horizontal = 18.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = if (color == c.ink) c.accent else color)
  }
}

/**
 * A colour to pick. In a row of colours, pass the row's [selection] (hosted with [colorRing]) and the
 * ring glides between them; on its own it draws its own ring.
 */
@Composable
fun ColorDot(color: Color, selected: Boolean, size: Dp = 30.dp, selection: SlidingSelection? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
  val c = Tali.colors
  Box(
    Modifier.then(if (selection != null) Modifier.slidingOption(selection, color) else Modifier)
      .size(size + 8.dp)
      .clip(CircleShape)
      .then(if (selected && selection == null) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
      .then(
        if (selection != null) Modifier.combinedClickable(interactionSource = null, indication = null, role = Role.RadioButton, onClick = onClick, onLongClick = onLongClick)
        else Modifier.combinedClickableCompat(onClick, onLongClick)
      )
      .padding(4.dp)
      .clip(CircleShape)
      .background(color)
      .border(1.dp, Color(0x22000000), CircleShape)
  )
}

/** Hosts the gliding ring for a row of [ColorDot]s; [selected] is the chosen colour. */
@Composable
fun Modifier.colorRing(selection: SlidingSelection, selected: Color?, color: Color = Tali.colors.accent, width: Dp = 2.dp): Modifier =
  slidingHighlight(selection, selected, SolidColor(color), corner = 100.dp, ring = width)

/**
 * The divider under the library title: an architect's scale ruler, with minor ticks and numbered
 * centimetres, as a quiet nod to civil engineering drawings.
 */
@Composable
fun RulerDivider(modifier: Modifier = Modifier) {
  val c = Tali.colors
  val measurer = rememberTextMeasurer()
  val labelStyle = MaterialTheme.typography.labelSmall.copy(fontFamily = Figtree, fontSize = 9.sp, color = c.inkFaint)
  Canvas(modifier.fillMaxWidth().height(22.dp)) {
    val mm = 6.3f * density / 1.6f
    val baseY = size.height - 1f
    drawLine(c.line, Offset(0f, baseY), Offset(size.width, baseY), 1f * density)
    var i = 0
    var x = 0f
    while (x <= size.width) {
      val h = when {
        i % 10 == 0 -> 9f
        i % 5 == 0 -> 6f
        else -> 3.5f
      } * density
      drawLine(if (i % 10 == 0) c.blue.copy(alpha = 0.55f) else c.line, Offset(x, baseY), Offset(x, baseY - h), 1f * density)
      if (i % 10 == 0 && i > 0) drawText(measurer, (i / 10).toString(), Offset(x + 3f * density, 0f), labelStyle)
      i++
      x += mm
    }
  }
}

fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier =
  combinedClickable(onClick = onClick, onLongClick = onLongClick)
