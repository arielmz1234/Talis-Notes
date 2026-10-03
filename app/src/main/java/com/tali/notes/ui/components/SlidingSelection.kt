package com.tali.notes.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * One highlight that glides from option to option, instead of each option switching its own
 * background on and off.
 *
 * Options say where they are with [slidingOption]; the container draws the highlight behind (or over)
 * them with [slidingHighlight]. The highlight's edges move on springs, the leading edge a little
 * stiffer than the trailing one, so it stretches as it travels and settles into the new option.
 * It is animated in the draw phase only: no recomposition or layout per frame.
 */
@Stable
class SlidingSelection internal constructor() {
  private var host: LayoutCoordinates? = null
  private val options = HashMap<Any, LayoutCoordinates>()
  internal val brushes = HashMap<Any, Brush>()

  /** Where each option sits in the container, clipped to what's visible (a scrolled list clips it). */
  internal val rects = mutableStateMapOf<Any, Rect>()

  internal fun placeHost(coordinates: LayoutCoordinates) {
    host = coordinates
    options.keys.forEach(::measure)
  }

  internal fun place(key: Any, coordinates: LayoutCoordinates, brush: Brush?) {
    options[key] = coordinates
    if (brush != null) brushes[key] = brush else brushes.remove(key)
    measure(key)
  }

  internal fun forget(key: Any, coordinates: LayoutCoordinates?) {
    if (coordinates != null && options[key] !== coordinates) return
    options.remove(key)
    brushes.remove(key)
    rects.remove(key)
  }

  private fun measure(key: Any) {
    val h = host?.takeIf { it.isAttached } ?: return
    val c = options[key]?.takeIf { it.isAttached } ?: return
    val r = h.localBoundingBoxOf(c, clipBounds = true)
    if (rects[key] != r) rects[key] = r
  }
}

@Composable
fun rememberSlidingSelection(): SlidingSelection = remember { SlidingSelection() }

/**
 * Marks this as the option [key] of [selection]. Put it first in the chain so the highlight covers
 * the option's whole area. [brush] overrides the container's highlight for this option (a folder's
 * own colour, say); the highlight blends between brushes as it moves.
 */
fun Modifier.slidingOption(selection: SlidingSelection, key: Any, brush: Brush? = null): Modifier =
  this then OptionElement(selection, key, brush)

/**
 * Draws [selection]'s highlight under the option [selected], or nothing when it's null. A [ring]
 * width draws an outline instead of a fill; [lift] adds a soft shadow under it; [above] draws it over
 * the options (for swatches that would hide it).
 */
@Composable
fun Modifier.slidingHighlight(
  selection: SlidingSelection,
  selected: Any?,
  brush: Brush,
  corner: Dp,
  ring: Dp = 0.dp,
  lift: Boolean = false,
  above: Boolean = false,
): Modifier {
  val glide = remember(selection) { Glide() }
  val target = selected?.let { selection.rects[it] }?.takeIf { it.width > 0f && it.height > 0f }
  LaunchedEffect(glide, selected, target, brush) { glide.go(selected, target, selected?.let { selection.brushes[it] } ?: brush) }
  return this
    .onGloballyPositioned { selection.placeHost(it) }
    .drawWithContent {
      if (above) {
        drawContent()
        glide.paint(this, corner.toPx(), ring.toPx(), lift)
      } else {
        glide.paint(this, corner.toPx(), ring.toPx(), lift)
        drawContent()
      }
    }
}

private class Glide {
  val left = Animatable(0f)
  val top = Animatable(0f)
  val right = Animatable(0f)
  val bottom = Animatable(0f)

  /** 0 hidden, 1 shown; it also pops the highlight in from slightly smaller. */
  val shown = Animatable(0f)

  /** Blend from [from] to [to] when options have their own brushes. */
  val mix = Animatable(1f)
  // State, so the highlight redraws when its colour changes on its own (the theme or dark paper
  // switching): as plain fields it kept drawing the old theme's colour, white or black.
  var from: Brush? by mutableStateOf(null)
  var to: Brush? by mutableStateOf(null)
  var key: Any? = null

  suspend fun go(newKey: Any?, target: Rect?, brush: Brush) = coroutineScope {
    if (target == null) {
      // Fade out where it is, so it can come back from there.
      key = null
      shown.animateTo(0f, tween(150))
      return@coroutineScope
    }
    val sameKey = newKey == key
    val hidden = shown.value == 0f && shown.targetValue == 0f
    key = newKey
    if (brush != to) {
      if (!hidden && !sameKey && to != null) {
        from = to
        mix.snapTo(0f)
        launch { mix.animateTo(1f, tween(280)) }
      } else {
        from = null
        mix.snapTo(1f)
      }
      to = brush
    }
    if (hidden || sameKey) {
      // Appearing, or the same option moved (a scroll, a rotation): be there at once.
      left.snapTo(target.left)
      top.snapTo(target.top)
      right.snapTo(target.right)
      bottom.snapTo(target.bottom)
      if (shown.targetValue != 1f) shown.animateTo(1f, POP)
      return@coroutineScope
    }
    launch { shown.animateTo(1f, POP) }
    val dx = target.center.x - (left.value + right.value) / 2
    val dy = target.center.y - (top.value + bottom.value) / 2
    // The edge facing the way it moves leads; the other follows a little behind, so it stretches.
    val horizontal = abs(dx) >= abs(dy)
    launch { left.animateTo(target.left, if (horizontal) (if (dx < 0) LEAD else TRAIL) else EVEN) }
    launch { right.animateTo(target.right, if (horizontal) (if (dx > 0) LEAD else TRAIL) else EVEN) }
    launch { top.animateTo(target.top, if (!horizontal) (if (dy < 0) LEAD else TRAIL) else EVEN) }
    launch { bottom.animateTo(target.bottom, if (!horizontal) (if (dy > 0) LEAD else TRAIL) else EVEN) }
  }

  fun paint(scope: DrawScope, cornerPx: Float, ringPx: Float, lift: Boolean) = with(scope) {
    val a = shown.value
    if (a <= 0.001f) return@with
    val scale = 0.88f + 0.12f * a
    val cx = (left.value + right.value) / 2
    val cy = (top.value + bottom.value) / 2
    val w = (right.value - left.value) * scale
    val h = (bottom.value - top.value) * scale
    if (w <= 0f || h <= 0f) return@with
    var topLeft = Offset(cx - w / 2, cy - h / 2)
    var size = Size(w, h)
    val radius = min(cornerPx * scale, min(w, h) / 2)
    if (lift) {
      val d = density
      drawRoundRect(SHADOW.copy(alpha = 0.05f * a), topLeft + Offset(0f, 1.5f * d), size, CornerRadius(radius))
      drawRoundRect(SHADOW.copy(alpha = 0.04f * a), topLeft + Offset(0f, 0.5f * d), size, CornerRadius(radius))
    }
    val style = if (ringPx > 0f) Stroke(ringPx) else null
    if (style != null) {
      topLeft += Offset(ringPx / 2, ringPx / 2)
      size = Size(w - ringPx, h - ringPx)
    }
    val r = CornerRadius((radius - ringPx / 2).coerceAtLeast(0f))
    val m = mix.value
    from?.takeIf { m < 1f }?.let { b ->
      if (style != null) drawRoundRect(b, topLeft, size, r, alpha = a * (1 - m), style = style)
      else drawRoundRect(b, topLeft, size, r, alpha = a * (1 - m))
    }
    to?.let { b ->
      if (style != null) drawRoundRect(b, topLeft, size, r, alpha = a * m, style = style)
      else drawRoundRect(b, topLeft, size, r, alpha = a * m)
    }
  }

  companion object {
    private val SHADOW = Color(0xFF3B2A7A)
    private val LEAD = spring(dampingRatio = 0.8f, stiffness = 750f, visibilityThreshold = 0.5f)
    private val TRAIL = spring(dampingRatio = 0.86f, stiffness = 330f, visibilityThreshold = 0.5f)
    private val EVEN = spring(dampingRatio = 0.84f, stiffness = 500f, visibilityThreshold = 0.5f)
    private val POP = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.002f)
  }
}

private data class OptionElement(val selection: SlidingSelection, val key: Any, val brush: Brush?) : ModifierNodeElement<OptionNode>() {
  override fun create() = OptionNode(selection, key, brush)

  override fun update(node: OptionNode) {
    if (node.selection !== selection || node.key != key) node.forget()
    node.selection = selection
    node.key = key
    node.brush = brush
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "slidingOption"
    properties["key"] = key
  }
}

private class OptionNode(var selection: SlidingSelection, var key: Any, var brush: Brush?) : Modifier.Node(), GlobalPositionAwareModifierNode {
  private var coordinates: LayoutCoordinates? = null

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
    selection.place(key, coordinates, brush)
  }

  fun forget() {
    selection.forget(key, coordinates)
    coordinates = null
  }

  override fun onDetach() = forget()
}
