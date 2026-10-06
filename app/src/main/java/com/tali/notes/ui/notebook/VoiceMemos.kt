package com.tali.notes.ui.notebook

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tali.notes.audio.VoiceRecorder
import com.tali.notes.canvas.NoteCanvasView
import com.tali.notes.geometry.Pt
import com.tali.notes.model.AudioItem
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.CloudCrown
import com.tali.notes.ui.components.EaseOutStrong
import com.tali.notes.ui.components.PUFF_LEAVE_MS
import com.tali.notes.ui.components.PuffBox
import com.tali.notes.ui.components.cloudSurface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** The memo button, the bar it opens into, and the recording bar. All in dp, so the same on every tablet. */
private val MEMO_SIZE = 48.dp
private val MEMO_BAR = 312.dp
private val MEMO_GAP = 12.dp
private val RECORDER_BAR = 404.dp
private val RECORDER_HEIGHT = 56.dp

/** Opening is an entry (strong ease-out, 260 ms); closing is quicker (180 ms), as exits should be. */
private const val OPEN_MS = 260
private const val CLOSE_MS = 180

/** The distance between two memos beside a page, in pixels (for placing a new one). */
@Composable
fun memoSpacingPx(): Float = with(LocalDensity.current) { (MEMO_SIZE + 22.dp).toPx() }

/**
 * The notebook's voice memos, laid over the canvas: a round button for each beside its page, over
 * the desk, following the page as it scrolls and zooms. Tapping one opens it into a bar to play it
 * (it waits for play); holding one picks it up to move it anywhere off the paper.
 */
@Composable
fun VoiceMemoLayer(vm: NotebookViewModel, canvas: NoteCanvasView) {
  // Folding away when the page is touched (writing carries on as normal).
  LaunchedEffect(canvas) { canvas.onTouchDown = { if (vm.openMemo != null) vm.openMemo = null } }
  if (vm.openMemo != null) BackHandler { vm.openMemo = null }
  val density = LocalDensity.current
  val size = with(density) { MEMO_SIZE.toPx() }
  val gap = with(density) { MEMO_GAP.toPx() }
  val spacing = memoSpacingPx()
  // Where every button sits this frame, worked out together so none overlap however far she zooms out.
  val centres: () -> Map<String, Pt> = { spread(vm.memos.map { it.id to canvas.memoCenter(it, size / 2, gap) }, spacing, size) }
  Box(Modifier.fillMaxSize()) {
    vm.memos.forEach { m -> key(m.id) { MemoBubble(m, centres, vm, canvas, open = vm.openMemo == m.id) } }
  }
}

/**
 * Moves buttons that would overlap on screen (one column beside the page, zoomed out far) down
 * so each keeps at least [spacing] from the one above it. [size] tells which share a column.
 */
internal fun spread(centres: List<Pair<String, Pt>>, spacing: Float, size: Float): Map<String, Pt> {
  val out = HashMap<String, Pt>()
  val placed = ArrayList<Pt>()
  for ((id, p) in centres.sortedBy { it.second.y }) {
    var y = p.y
    placed.filter { abs(it.x - p.x) < size }.maxByOrNull { it.y }?.let { above -> if (y < above.y + spacing) y = above.y + spacing }
    val at = Pt(p.x, y)
    placed += at
    out[id] = at
  }
  return out
}

@Composable
private fun MemoBubble(memo: AudioItem, centres: () -> Map<String, Pt>, vm: NotebookViewModel, canvas: NoteCanvasView, open: Boolean) {
  val c = Tali.colors
  val voice = vm.voice
  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current
  val size = with(density) { MEMO_SIZE.toPx() }
  val gap = with(density) { MEMO_GAP.toPx() }
  // 0 = the round button, 1 = the open bar.
  val openness = remember { Animatable(0f) }
  // It settles in from slightly smaller when it first shows: never from nothing. Just added from a
  // recording, it pops instead: springs up past its size and back, with a ring and a few sparks
  // spreading out from it.
  val popping = remember { vm.justAdded == memo.id }
  val shown = remember { Animatable(0f) }
  val burst = remember { Animatable(if (popping) 0f else 1f) }
  LaunchedEffect(Unit) {
    if (popping) {
      vm.justAdded = null
      launch { burst.animateTo(1f, tween(560, easing = EaseOutStrong)) }
      shown.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.48f, stiffness = 520f))
    } else {
      shown.animateTo(1f, tween(OPEN_MS, easing = EaseOutStrong))
    }
  }
  LaunchedEffect(open) {
    if (open) openness.animateTo(1f, tween(OPEN_MS, easing = EaseOutStrong))
    else openness.animateTo(0f, tween(CLOSE_MS, easing = EaseOutStrong))
  }
  var editing by remember { mutableStateOf(false) }
  LaunchedEffect(open) { if (!open) editing = false }
  var leaving by remember { mutableStateOf(false) }
  LaunchedEffect(leaving) {
    if (!leaving) return@LaunchedEffect
    delay(PUFF_LEAVE_MS.toLong())
    vm.deleteMemo(memo.id)
  }
  // Held and moved: where the finger is, and the lift while it's carried. It keeps to its margin
  // beside the paper ([dragX], gliding across when the finger crosses the page's middle) and only
  // follows the finger up and down.
  var drag by remember { mutableStateOf<Offset?>(null) }
  val dragX = remember { Animatable(0f) }
  var dragSide by remember { mutableStateOf<Int?>(null) }
  val scope = androidx.compose.runtime.rememberCoroutineScope()
  val lift by animateFloatAsState(if (drag != null) 1f else 0f, tween(160, easing = EaseOutStrong), label = "lift")
  // After a drop it glides from where it was let go to where it lands (just off the paper).
  val settle = remember { Animatable(1f) }
  var settleFrom by remember { mutableStateOf<Offset?>(null) }
  LaunchedEffect(settleFrom) {
    if (settleFrom == null) return@LaunchedEffect
    settle.snapTo(0f)
    settle.animateTo(1f, tween(220, easing = EaseOutStrong))
    settleFrom = null
  }
  // Where it was last put, and which way the bar opens (read by the contents, which stay still).
  val placed = remember { FloatArray(2) }
  val grows = remember { BooleanArray(1) }
  val playingThis = voice.current == memo.id
  // The gesture outlives recompositions: it must move the memo as it is now (named, say), not as it was.
  val current by androidx.compose.runtime.rememberUpdatedState(memo)
  Layout(
    content = {
      // 0: the button and the bar it opens into.
      PuffBox(leaving = leaving, appearing = false) {
        Box(
          Modifier
            .graphicsLayer {
              alpha = shown.value
              alpha = shown.value.coerceAtMost(1f)
              val k = (if (popping) 0.5f + 0.5f * shown.value else 0.9f + 0.1f * shown.value) * (1f + 0.1f * lift)
              scaleX = k
              scaleY = k
            }
            .cloudSurface(MEMO_SIZE / 2, CloudCrown.NONE, shadowDepth = 4.dp + 6.dp * lift)
            .clip(RoundedCornerShape(MEMO_SIZE / 2))
        ) {
          // The button's face: a sound-wave glyph, with a ring showing how far it has played.
          Box(
            Modifier.size(MEMO_SIZE)
              .graphicsLayer { alpha = (1f - openness.value * 3f).coerceIn(0f, 1f) }
              .pointerInput(memo.id) {
                detectDragGesturesAfterLongPress(
                  onDragStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.openMemo = null
                    drag = Offset(placed[0], placed[1])
                    dragSide = current.side
                    scope.launch { dragX.snapTo(placed[0]) }
                  },
                  onDrag = { change, amount ->
                    change.consume()
                    val d = (drag ?: return@detectDragGesturesAfterLongPress) + amount
                    drag = d
                    val spot = canvas.memoDrop(current, d.x, d.y)
                    val x = canvas.memoCenter(spot, size / 2, gap).x
                    if (spot.side != dragSide) {
                      dragSide = spot.side
                      scope.launch { dragX.animateTo(x, tween(220, easing = EaseOutStrong)) }
                    } else if (!dragX.isRunning) {
                      scope.launch { dragX.snapTo(x) }
                    }
                  },
                  onDragEnd = {
                    drag?.let { d ->
                      settleFrom = Offset(dragX.value, d.y)
                      vm.moveMemo(canvas.memoDrop(current, d.x, d.y))
                    }
                    drag = null
                  },
                  onDragCancel = { drag = null },
                )
              }
              .clickable(enabled = !open, onClickLabel = "Open voice memo", role = Role.Button) { vm.openMemo = memo.id },
            contentAlignment = Alignment.Center,
          ) {
            if (playingThis) {
              Canvas(Modifier.size(MEMO_SIZE - 8.dp)) {
                val sw = 2.5.dp.toPx()
                drawArc(c.accent.copy(alpha = 0.18f), 0f, 360f, false, style = Stroke(sw))
                drawArc(c.accent, -90f, 360f * voice.progress, false, style = Stroke(sw, cap = StrokeCap.Round))
              }
            }
            Icon(Icons.Outlined.GraphicEq, null, tint = c.accent, modifier = Modifier.size(22.dp))
          }
          // The bar's contents, laid out at full width from the start so nothing reflows while it opens.
          Box(Modifier.revealInPlace { grows[0] }.graphicsLayer { alpha = ((openness.value - 0.45f) / 0.55f).coerceIn(0f, 1f) }) {
            if (openness.value > 0.01f) {
              PlayRow(memo, voice, openness::value, MEMO_BAR, MEMO_SIZE) {
                Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Delete voice memo", role = Role.Button) { leaving = true }, contentAlignment = Alignment.Center) {
                  Icon(Icons.Outlined.DeleteOutline, "Delete voice memo", tint = c.inkFaint, modifier = Modifier.size(20.dp))
                }
              }
            }
          }
        }
      }
      // 1: its title above the open bar (or a small pencil to give it one).
      Box(Modifier.graphicsLayer { alpha = ((openness.value - 0.5f) / 0.5f).coerceIn(0f, 1f) }) {
        if (openness.value > 0.01f && !leaving) MemoTitle(memo.title, editing, onEdit = { editing = true }) { t ->
          vm.renameMemo(memo.id, t)
          editing = false
        }
      }
      // 2: the pop's ring and sparks (placed under the button).
      Canvas(Modifier.size(MEMO_SIZE * 2.4f)) {
        val p = burst.value
        if (p >= 1f) return@Canvas
        val r0 = MEMO_SIZE.toPx() / 2
        val o = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(c.accent.copy(alpha = 0.45f * (1f - p)), radius = r0 * (1f + 0.75f * p), center = o, style = Stroke(2.dp.toPx() * (1f - p) + 0.5f))
        for (i in 0 until 6) {
          val a = i * (PI.toFloat() / 3f) + 0.35f
          val r = r0 * (1.05f + 0.95f * p)
          drawCircle(c.accent.copy(alpha = 0.8f * (1f - p)), radius = 2.6.dp.toPx() * (1f - p * 0.7f), center = Offset(o.x + r * kotlin.math.cos(a), o.y + r * sin(a)))
        }
      }
      // 3: folded, a titled memo shows its name faintly under the button.
      Box(Modifier.graphicsLayer { alpha = (1f - openness.value * 3f).coerceIn(0f, 1f) * shown.value * (1f - lift) }) {
        if (memo.title.isNotBlank()) {
          Text(
            memo.title,
            style = MaterialTheme.typography.labelSmall,
            color = c.inkMuted.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp).clip(RoundedCornerShape(8.dp)).background(c.surface.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 1.dp),
          )
        }
      }
    },
  ) { parts, constraints ->
    // Read here (placement), so following the page never recomposes anything.
    vm.cameraTick
    val full = minOf(with(density) { MEMO_BAR.toPx() }, constraints.maxWidth - 2 * gap)
    val w = size + (full - size) * openness.value
    val bar = parts[0].measure(Constraints.fixed(w.roundToInt(), size.roundToInt()))
    val title = parts[1].measure(Constraints(maxWidth = full.roundToInt()))
    val sparks = parts[2].measure(Constraints())
    val caption = parts[3].measure(Constraints())
    val home = centres()[memo.id] ?: Pt(-10_000f, -10_000f)
    val from = settleFrom
    val centre =
      drag?.let { Pt(dragX.value, it.y) }
        ?: from?.let { f -> Pt(f.x + (home.x - f.x) * settle.value, f.y + (home.y - f.y) * settle.value) }
        ?: home
    if (drag == null) {
      placed[0] = centre.x
      placed[1] = centre.y
    }
    // It opens towards the page: from the right of the screen leftwards, or the other way on the left.
    val toLeft = centre.x > constraints.maxWidth / 2f
    grows[0] = toLeft
    val left = if (toLeft) centre.x + size / 2 - w else centre.x - size / 2
    val top = centre.y - size / 2
    layout(constraints.maxWidth, constraints.maxHeight) {
      sparks.place((centre.x - sparks.width / 2).roundToInt(), (centre.y - sparks.height / 2).roundToInt())
      bar.place(left.roundToInt(), top.roundToInt())
      val edge = 4.dp.toPx()
      val tx = if (toLeft) centre.x + size / 2 - full + 10.dp.toPx() else centre.x - size / 2 + 10.dp.toPx()
      // Above the bar, unless that would put it under the toolbars: then just below it.
      val above = top - title.height - 6.dp.toPx()
      val ty = if (above >= canvas.topClearance * density.density - 40.dp.toPx()) above else top + size + 6.dp.toPx()
      title.place(tx.roundToInt(), ty.roundToInt())
      // Under the button, kept on screen.
      val cx = (centre.x - caption.width / 2).coerceIn(edge, maxOf(edge, constraints.maxWidth - caption.width - edge))
      caption.place(cx.roundToInt(), (top + size + 4.dp.toPx()).roundToInt())
    }
  }
}

/**
 * A memo's title, small and a little see-through on a frosted chip so it reads over any paper.
 * Without one there's just a small pencil. Tapping either edits it right there.
 */
@Composable
private fun MemoTitle(title: String, editing: Boolean, onEdit: () -> Unit, onDone: (String) -> Unit) {
  val c = Tali.colors
  val chip = Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface.copy(alpha = 0.72f))
  if (editing) {
    var value by remember { mutableStateOf(TextFieldValue(title, TextRange(title.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
      focus.requestFocus()
      keyboard?.show()
    }
    val done = {
      keyboard?.hide()
      onDone(value.text)
    }
    BackHandler(onBack = done)
    Row(chip.padding(start = 10.dp, end = 4.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.widthIn(min = 120.dp, max = 220.dp)) {
        if (value.text.isEmpty()) Text("Name this memo", style = MaterialTheme.typography.labelLarge, color = c.inkFaint)
        BasicTextField(
          value,
          { value = it },
          singleLine = true,
          textStyle = MaterialTheme.typography.labelLarge.merge(TextStyle(color = c.ink)),
          cursorBrush = SolidColor(c.accent),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { done() }),
          modifier = Modifier.focusRequester(focus),
        )
      }
      Box(Modifier.size(28.dp).clip(CircleShape).clickable(onClickLabel = "Save the name", role = Role.Button) { done() }, contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Check, "Save the name", tint = c.accent, modifier = Modifier.size(18.dp))
      }
    }
  } else if (title.isNotBlank()) {
    Row(chip.clickable(onClickLabel = "Rename voice memo", role = Role.Button, onClick = onEdit).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp))
      Spacer(Modifier.width(6.dp))
      Icon(Icons.Outlined.Edit, null, tint = c.inkFaint.copy(alpha = 0.7f), modifier = Modifier.size(13.dp))
    }
  } else {
    Box(chip.size(28.dp).clickable(onClickLabel = "Name this memo", role = Role.Button, onClick = onEdit), contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.Edit, "Name this memo", tint = c.inkFaint.copy(alpha = 0.75f), modifier = Modifier.size(14.dp))
    }
  }
}

/**
 * The bar's full-width contents, kept still on screen while the bar opens over them: lined up with
 * the far side when it grows leftwards, so the opening edge uncovers them instead of dragging them.
 */
private fun Modifier.revealInPlace(toLeft: () -> Boolean): Modifier =
  layout { measurable, constraints ->
    val p = measurable.measure(Constraints(minWidth = 0, maxWidth = Constraints.Infinity, minHeight = constraints.minHeight, maxHeight = constraints.maxHeight))
    layout(constraints.maxWidth, constraints.maxHeight) { p.place(if (toLeft()) constraints.maxWidth - p.width else 0, 0) }
  }

/** Play / pause, the wave (tap or drag along it to jump), the time, then [trailing] buttons. */
@Composable
private fun PlayRow(memo: AudioItem, voice: VoiceRecorder, openness: () -> Float, width: Dp, height: Dp, trailing: @Composable () -> Unit) {
  val c = Tali.colors
  val here = voice.current == memo.id
  val playing = here && voice.playing
  Row(Modifier.requiredWidth(width).height(height).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    // Play / pause: a filled accent circle, the bar's one strong mark.
    Box(
      Modifier.size(36.dp).clip(CircleShape).background(c.accent).clickable(onClickLabel = if (playing) "Pause" else "Play", role = Role.Button) {
        if (playing) voice.pause() else voice.play(memo)
      },
      contentAlignment = Alignment.Center,
    ) {
      Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp))
    }
    Spacer(Modifier.width(10.dp))
    Wave(
      levels = { memo.wave.map { it / 100f } },
      played = { if (voice.current == memo.id) voice.progress else 0f },
      alive = playing,
      openness = openness,
      modifier =
        Modifier.weight(1f).height(30.dp)
          .pointerInput(memo.id) {
            detectTapGestures { o ->
              if (voice.current != memo.id) voice.play(memo)
              voice.seek(o.x / size.width)
            }
          }
          .pointerInput(memo.id, "drag") {
            detectHorizontalDragGestures(onDragStart = { if (voice.current != memo.id) voice.play(memo) }) { change, _ -> voice.seek(change.position.x / size.width) }
          },
    )
    Spacer(Modifier.width(10.dp))
    Text(
      if (here) "${clock(voice.positionMs)} / ${clock(memo.durationMs)}" else clock(memo.durationMs),
      style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum")),
      color = c.inkMuted,
      maxLines = 1,
    )
    trailing()
  }
}

/**
 * Recording, just below the mic button: a bar that drops from the button with the live sound wave,
 * the time, Done and delete. Done turns it into a draft to listen to, delete, or add to the page,
 * where it pops in beside the page.
 */
@Composable
fun RecorderPanel(vm: NotebookViewModel, anchor: () -> LayoutCoordinates?, onAdd: () -> Unit) {
  val c = Tali.colors
  val voice = vm.voice
  val density = LocalDensity.current
  val present = vm.recordingAt != null
  var leaving by remember { mutableStateOf(false) }
  LaunchedEffect(present) { if (present) leaving = false }
  LaunchedEffect(leaving) {
    if (!leaving) return@LaunchedEffect
    delay(PUFF_LEAVE_MS.toLong())
    vm.discardDraft()
  }
  val self = remember { arrayOfNulls<LayoutCoordinates>(1) }
  Box(
    Modifier.fillMaxSize().onGloballyPositioned { self[0] = it }.layout { measurable, constraints ->
      val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
      val margin = 16.dp.toPx()
      val me = self[0]
      val a = anchor()?.takeIf { it.isAttached && me?.isAttached == true }?.let { me!!.localBoundingBoxOf(it) }
      val cx = a?.center?.x ?: (constraints.maxWidth / 2f)
      val top = (a?.bottom ?: 80.dp.toPx()) + 18.dp.toPx()
      val x = (cx - p.width / 2f).coerceIn(margin, maxOf(margin, constraints.maxWidth - p.width - margin))
      layout(constraints.maxWidth, constraints.maxHeight) { p.place(x.roundToInt(), top.roundToInt()) }
    }
  ) {
    // It drops from the mic button: in from 92% and a little above, out quicker.
    AnimatedVisibility(
      present,
      enter = fadeIn(tween(OPEN_MS, easing = EaseOutStrong)) + scaleIn(tween(OPEN_MS, easing = EaseOutStrong), initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 0f)) + slideInVertically(tween(OPEN_MS, easing = EaseOutStrong)) { -it / 4 },
      exit = fadeOut(tween(CLOSE_MS, easing = EaseOutStrong)) + scaleOut(tween(CLOSE_MS, easing = EaseOutStrong), targetScale = 0.94f, transformOrigin = TransformOrigin(0.5f, 0f)),
    ) {
      val width = minOf(RECORDER_BAR, androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - 32.dp).coerceAtLeast(200.dp)
      PuffBox(leaving = leaving, appearing = false) {
        Box(Modifier.width(width).height(RECORDER_HEIGHT).cloudSurface(RECORDER_HEIGHT / 2, CloudCrown.NONE, shadowDepth = 6.dp).clip(RoundedCornerShape(RECORDER_HEIGHT / 2))) {
          // The last draft shown, kept while the bar leaves (after Add the draft is gone, and the bar
          // must not flip back to recording on its way out).
          var shownDraft by remember { mutableStateOf<AudioItem?>(null) }
          vm.draftMemo?.let { shownDraft = it }
          LaunchedEffect(present) { if (present && vm.draftMemo == null) shownDraft = null }
          // Recording, then listening back: the contents cross-fade, the bar stays.
          AnimatedContent(
            targetState = !voice.recording && shownDraft != null && !leaving,
            transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
            label = "recorder",
          ) { review ->
            val trash: @Composable () -> Unit = {
              Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "Delete the recording", role = Role.Button) {
                if (voice.recording) voice.cancel()
                voice.stopPlayback()
                leaving = true
              }, contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.DeleteOutline, "Delete the recording", tint = c.inkFaint, modifier = Modifier.size(20.dp))
              }
            }
            val memo = shownDraft
            if (review && memo != null) {
              PlayRow(memo, voice, { 1f }, width, RECORDER_HEIGHT) {
                trash()
                Spacer(Modifier.width(4.dp))
                Row(
                  Modifier.height(38.dp).clip(RoundedCornerShape(19.dp)).background(c.accent).clickable(onClickLabel = "Add to the page", role = Role.Button, onClick = onAdd).padding(horizontal = 14.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                  Spacer(Modifier.width(6.dp))
                  Text("Add", style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
              }
            } else {
              RecordingRow(voice, width, onDone = vm::stopMemo, trash = trash)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun RecordingRow(voice: VoiceRecorder, width: Dp, onDone: () -> Unit, trash: @Composable () -> Unit) {
  val c = Tali.colors
  val pulse by rememberInfiniteTransition(label = "rec").animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "recPulse")
  Row(Modifier.requiredWidth(width).height(RECORDER_HEIGHT).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    // Done: a red circle with a soft halo breathing out from it while it records.
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
      Canvas(Modifier.size(40.dp)) {
        val r = size.minDimension / 2
        drawCircle(c.danger.copy(alpha = 0.28f * (1f - pulse)), radius = r * (0.78f + 0.4f * pulse))
      }
      Box(
        Modifier.size(34.dp).clip(CircleShape).background(c.danger).clickable(onClickLabel = "Done recording", role = Role.Button, onClick = onDone),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Rounded.Stop, "Done recording", tint = Color.White, modifier = Modifier.size(20.dp))
      }
    }
    Spacer(Modifier.width(10.dp))
    Wave(levels = { voice.levels.toList() }, played = { 1f }, alive = false, openness = { 1f }, live = true, modifier = Modifier.weight(1f).height(30.dp))
    Spacer(Modifier.width(10.dp))
    Text(clock(voice.elapsedMs), style = MaterialTheme.typography.labelMedium.merge(TextStyle(fontFeatureSettings = "tnum")), color = c.danger, maxLines = 1)
    Spacer(Modifier.width(4.dp))
    trash()
  }
}

/**
 * The sound wave: round-capped bars. A memo's bars spread across the width, the played part in the
 * accent colour; around the playhead they sway gently while it plays. A recording's bars are its
 * latest readings, the newest at the right. As a memo's bar opens, the bars grow in from the middle
 * line, left to right.
 */
@Composable
private fun Wave(levels: () -> List<Float>, played: () -> Float, alive: Boolean, openness: () -> Float, modifier: Modifier, live: Boolean = false) {
  val c = Tali.colors
  var time by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(alive) {
    if (!alive) return@LaunchedEffect
    val start = withFrameNanos { it }
    while (isActive) withFrameNanos { time = (it - start) / 1e9f }
  }
  Canvas(modifier) {
    val bw = 3.dp.toPx()
    val step = bw + 2.5.dp.toPx()
    val n = ((size.width + 2.5.dp.toPx()) / step).toInt().coerceAtLeast(1)
    val all = levels()
    val values =
      if (live) List(n) { i -> all.getOrNull(all.size - n + i) ?: 0f }
      else List(n) { i -> if (all.isEmpty()) 0f else all[(i * all.size / n).coerceIn(0, all.size - 1)] }
    val head = played() * n
    val open = openness()
    val mid = size.height / 2
    values.forEachIndexed { i, v ->
      val grow = ((open - 0.45f) / 0.55f * 1.6f - i.toFloat() / n * 0.6f).coerceIn(0f, 1f)
      val near = (1f - abs(i - head) / 4f).coerceIn(0f, 1f)
      val sway = if (alive) 1f + 0.35f * near * sin(time * 2f * PI.toFloat() * 1.8f + i * 0.9f) else 1f
      val h = (bw + (size.height - bw) * v * sway).coerceIn(bw, size.height) * grow
      val x = i * step + bw / 2
      val color = if (live || i < head) c.accent else c.accent.copy(alpha = 0.28f)
      if (h > 0.5f) drawLine(color, Offset(x, mid - h / 2 + bw / 2), Offset(x, mid + h / 2 - bw / 2), strokeWidth = bw, cap = StrokeCap.Round)
    }
  }
}

private fun clock(ms: Long): String {
  val s = (ms / 1000).coerceAtLeast(0)
  return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
