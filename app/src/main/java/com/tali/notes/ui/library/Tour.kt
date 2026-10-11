package com.tali.notes.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tali.notes.R
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.EaseOutStrong
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The tour's steps: a title, a few lines, and the little moving picture that shows it. */
private class TourStep(val title: Int, val body: Int, val picture: @Composable (Modifier) -> Unit)

private val steps =
  listOf(
    TourStep(R.string.tour_write_title, R.string.tour_write_body) { WritingPicture(it) },
    TourStep(R.string.tour_pens_title, R.string.tour_pens_body) { PensPicture(it) },
    TourStep(R.string.tour_hold_title, R.string.tour_hold_body) { HoldPicture(it) },
    TourStep(R.string.tour_subjects_title, R.string.tour_subjects_body) { StackPicture(it) },
    TourStep(R.string.tour_lasso_title, R.string.tour_lasso_body) { LassoPicture(it) },
    TourStep(R.string.tour_voice_title, R.string.tour_voice_body) { VoicePicture(it) },
    TourStep(R.string.tour_spen_title, R.string.tour_spen_body) { EraserPicture(it) },
    TourStep(R.string.tour_backup_title, R.string.tour_backup_body) { BackupPicture(it) },
  )

/**
 * A short walk through the app for someone new: what it does and a few things worth knowing
 * (holding the pen to perfect a shape, subject shapes, the lasso, voice memos, the S Pen button,
 * backups). Skip is there at every step; Back and Next move along, and the dots show how far.
 */
@Composable
fun TourCard(onDone: () -> Unit) {
  val c = Tali.colors
  var at by rememberSaveable { mutableIntStateOf(0) }
  val last = at == steps.lastIndex
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  DialogCard(onDismiss = onDone, width = 760) {
    // The step counter and Skip, along the top.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Text(stringResource(R.string.tour_step_n_of_n, at + 1, steps.size), style = MaterialTheme.typography.labelMedium, color = c.inkFaint, modifier = Modifier.weight(1f))
      if (!last) Text(stringResource(R.string.tour_skip), style = MaterialTheme.typography.labelLarge, color = c.accent, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDone).padding(horizontal = 10.dp, vertical = 6.dp))
    }
    Spacer(Modifier.height(6.dp))
    // Forward slides in from the reading side's end: from the right in English, the left in Hebrew.
    AnimatedContent(
      targetState = at,
      transitionSpec = {
        val forward = targetState > initialState
        val sign = (if (forward) 1 else -1) * (if (rtl) -1 else 1)
        (slideInHorizontally(tween(260, easing = EaseOutStrong)) { it / 6 * sign } + fadeIn(tween(200, delayMillis = 40)))
          .togetherWith(slideOutHorizontally(tween(180)) { -it / 8 * sign } + fadeOut(tween(140)))
      },
      label = "tour",
    ) { i ->
      val step = steps[i]
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth > 560.dp) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            PictureFrame { step.picture(it) }
            Spacer(Modifier.width(26.dp))
            Words(step, Modifier.weight(1f))
          }
        } else {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PictureFrame { step.picture(it) }
            Spacer(Modifier.height(16.dp))
            Words(step, Modifier.fillMaxWidth())
          }
        }
      }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Dots(at, Modifier.weight(1f)) { at = it }
      if (at > 0) {
        QuietButton(stringResource(R.string.back)) { at-- }
        Spacer(Modifier.width(8.dp))
      }
      PrimaryButton(stringResource(if (last) R.string.tour_start_writing else R.string.tour_next)) { if (last) onDone() else at++ }
    }
  }
}

@Composable
private fun Words(step: TourStep, modifier: Modifier) {
  val c = Tali.colors
  Column(modifier) {
    Text(stringResource(step.title), style = MaterialTheme.typography.headlineSmall, color = c.ink)
    Spacer(Modifier.height(10.dp))
    Text(stringResource(step.body), style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
  }
}

/** A soft page the picture sits on. */
@Composable
private fun PictureFrame(content: @Composable (Modifier) -> Unit) {
  val c = Tali.colors
  Box(
    Modifier.size(280.dp, 230.dp).clip(RoundedCornerShape(22.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(22.dp)),
    contentAlignment = Alignment.Center,
  ) { content(Modifier.size(240.dp, 200.dp)) }
}

/** One dot per step; the current one is a wider pill. Tapping a dot goes there. */
@Composable
private fun Dots(at: Int, modifier: Modifier, onPick: (Int) -> Unit) {
  val c = Tali.colors
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
    steps.indices.forEach { i ->
      val w by animateDpAsState(if (i == at) 22.dp else 8.dp, tween(240, easing = EaseOutStrong), label = "dot")
      val tint by animateColorAsState(if (i == at) c.accent else c.line, tween(200), label = "dotTint")
      Box(Modifier.size(w, 8.dp).clip(CircleShape).background(tint).clickable { onPick(i) })
    }
  }
}

// ---- The pictures: small drawings that loop, in the app's colours ----

/** 0..1 over [ms], round and round. */
@Composable
private fun loop(ms: Int): Float {
  val t = rememberInfiniteTransition(label = "tourLoop")
  val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(ms, easing = LinearEasing), RepeatMode.Restart), label = "t")
  return v
}

private fun phase(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

private fun ease(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)

/**
 * Joined-up "handwriting" from [x0] to [x1] on [baseline]: loops like cursive letters (a prolate
 * trochoid), smaller and taller by turns, with gaps between words.
 */
private fun cursive(x0: Float, x1: Float, baseline: Float, height: Float, seed: Int = 0): List<Offset> {
  val step = height * 0.42f
  val loops = ((x1 - x0) / (step * 2f * PI.toFloat())).toInt().coerceAtLeast(2)
  val n = loops * 24
  val out = ArrayList<Offset>(n + 1)
  for (i in 0..n) {
    val t = i / 24f * 2f * PI.toFloat()
    val letter = i / 24 + seed
    // Every fourth or fifth letter ends a word: that loop is just a low joining stroke.
    val word = letter % 5 == 4
    val tall = if (letter % 3 == 1) 1.35f else 1f
    val amp = (if (word) 0.12f else 0.5f) * height * tall
    // Swinging back near the top of each letter makes the loop (as in a joined-up l or e).
    val x = x0 + step * t + amp * 1.3f * sin(t)
    val y = baseline - amp * (1f - cos(t))
    if (x > x1) break
    out += Offset(x, y)
  }
  return out
}

/** The first [part] of a polyline, as a path. */
private fun partial(points: List<Offset>, part: Float): Path {
  val path = Path()
  if (points.isEmpty() || part <= 0f) return path
  var total = 0f
  for (i in 1 until points.size) total += (points[i] - points[i - 1]).getDistance()
  var left = total * part
  path.moveTo(points[0].x, points[0].y)
  for (i in 1 until points.size) {
    val seg = (points[i] - points[i - 1]).getDistance()
    if (left >= seg) {
      path.lineTo(points[i].x, points[i].y)
      left -= seg
    } else {
      val f = if (seg > 0f) left / seg else 0f
      path.lineTo(points[i - 1].x + (points[i].x - points[i - 1].x) * f, points[i - 1].y + (points[i].y - points[i - 1].y) * f)
      break
    }
  }
  return path
}

private fun DrawScope.ink(path: Path, color: Color, width: Float = 3.dp.toPx()) =
  drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.ruledPage(c: com.tali.notes.theme.TaliColors) {
  val gap = 26.dp.toPx()
  var y = gap
  while (y < size.height) {
    drawLine(c.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
    y += gap
  }
}

@Composable
private fun WritingPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    ruledPage(c)
    // Two lines of "handwriting": loops along a baseline.
    val w = size.width
    for (line in 0..1) {
      val base = 26.dp.toPx() * (2 + line) - 3.dp.toPx()
      val pts = cursive(w * 0.08f, w * (if (line == 0) 0.92f else 0.66f), base, 13.dp.toPx(), seed = line * 2)
      val part = phase(t, line * 0.35f, line * 0.35f + 0.4f)
      ink(partial(pts, ease(part)), c.ink, 2.4.dp.toPx())
    }
    // An underline in the accent, last.
    val u = ease(phase(t, 0.8f, 0.92f))
    if (u > 0f) drawLine(c.accent, Offset(w * 0.08f, 26.dp.toPx() * 4), Offset(w * 0.08f + w * 0.5f * u, 26.dp.toPx() * 4), 3.dp.toPx(), cap = StrokeCap.Round)
  }
}

@Composable
private fun PensPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3000)
  val colours = listOf(c.ink, Color(0xFF2F6FE0), Color(0xFFE0457B))
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    colours.forEachIndexed { i, col ->
      val y = h * (0.25f + i * 0.22f)
      val width = (2f + i * 2.4f).dp.toPx()
      val pts = List(40) { k -> Offset(w * 0.1f + w * 0.62f * k / 39f, y + sin(k / 39f * PI.toFloat() * 2f) * 8.dp.toPx()) }
      ink(partial(pts, ease(phase(t, i * 0.18f, i * 0.18f + 0.45f))), col, width)
      // Its colour dot, ringed while it's the one in use.
      val dot = Offset(w * 0.86f, y)
      drawCircle(col, 9.dp.toPx(), dot)
      val inUse = ((t * 3).toInt() % 3) == i
      if (inUse) drawCircle(c.accent, 13.dp.toPx(), dot, style = Stroke(2.dp.toPx()))
    }
  }
}

@Composable
private fun HoldPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4200)
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val l = w * 0.18f; val r = w * 0.82f; val top = h * 0.22f; val b = h * 0.78f
    // A wobbly box drawn by hand…
    val corners = listOf(Offset(l, top), Offset(r, top), Offset(r, b), Offset(l, b), Offset(l, top))
    val wobbly = buildList {
      for (s in 0 until 4) {
        val a = corners[s]; val z = corners[s + 1]
        for (k in 0 until 24) {
          val f = k / 24f
          val wob = sin(f * PI.toFloat() * 3 + s) * 5.dp.toPx() * sin(f * PI.toFloat())
          val dx = z.x - a.x; val dy = z.y - a.y
          val len = kotlin.math.sqrt(dx * dx + dy * dy)
          add(Offset(a.x + dx * f - dy / len * wob, a.y + dy * f + dx / len * wob))
        }
      }
      add(Offset(l + 4.dp.toPx(), top + 3.dp.toPx()))
    }
    val clean = buildList {
      for (s in 0 until 4) {
        val a = corners[s]; val z = corners[s + 1]
        for (k in 0 until 24) add(Offset(a.x + (z.x - a.x) * k / 24f, a.y + (z.y - a.y) * k / 24f))
      }
      add(corners[0])
    }
    val draw = ease(phase(t, 0f, 0.4f))
    // …the pen holds still (a ring fills), then it snaps clean.
    val hold = phase(t, 0.42f, 0.6f)
    val snap = ease(phase(t, 0.6f, 0.7f))
    val pts = wobbly.indices.map { i -> Offset(wobbly[i].x + (clean[i].x - wobbly[i].x) * snap, wobbly[i].y + (clean[i].y - wobbly[i].y) * snap) }
    ink(partial(pts, draw), c.ink, 3.dp.toPx())
    if (hold > 0f && snap < 1f) {
      val tip = wobbly.last()
      drawArc(c.accent, -90f, 360f * hold, false, Offset(tip.x - 14.dp.toPx(), tip.y - 14.dp.toPx()), Size(28.dp.toPx(), 28.dp.toPx()), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
    }
    // Its dots, once it's a shape.
    if (snap >= 1f) listOf(Offset((l + r) / 2, top), Offset(r, (top + b) / 2), Offset((l + r) / 2, b), Offset(l, (top + b) / 2)).forEach {
      drawCircle(Color.White, 5.dp.toPx(), it)
      drawCircle(c.accent, 5.dp.toPx(), it, style = Stroke(1.6.dp.toPx()))
    }
  }
}

@Composable
private fun StackPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    // A stack being pulled longer: more cells appear as it grows, then it settles back.
    val grow = if (t < 0.5f) ease(phase(t, 0.08f, 0.45f)) else 1f - ease(phase(t, 0.6f, 0.95f))
    val cell = 22.dp.toPx()
    val top = h * 0.1f
    val height = cell * (2.2f + 5f * grow)
    val l = w * 0.36f; val r = w * 0.64f
    drawRoundRect(c.ink, Offset(l, top), Size(r - l, height), CornerRadius(3.dp.toPx()), style = Stroke(2.4.dp.toPx()))
    var y = top + cell
    while (y < top + height - 4f) {
      drawLine(c.ink, Offset(l, y), Offset(r, y), 2.dp.toPx())
      y += cell
    }
    // The dot being pulled, and a hint of the finger's way.
    val dot = Offset((l + r) / 2, top + height)
    drawCircle(Color.White, 6.dp.toPx(), dot)
    drawCircle(c.accent, 6.dp.toPx(), dot, style = Stroke(2.dp.toPx()))
    drawLine(c.accent.copy(alpha = 0.35f), Offset(dot.x, top + cell * 2.2f), Offset(dot.x, top + cell * 7.2f), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
    // A star beside it: her subject, kept in the panel.
    val s = Offset(w * 0.82f, h * 0.2f)
    val star = Path().apply {
      for (k in 0 until 10) {
        val rr = if (k % 2 == 0) 13.dp.toPx() else 5.5.dp.toPx()
        val a = -PI / 2 + k * PI / 5
        val p = Offset(s.x + rr * cos(a).toFloat(), s.y + rr * sin(a).toFloat())
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
      }
      close()
    }
    drawPath(star, Color(0xFFF5B83D))
  }
}

@Composable
private fun LassoPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4200)
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    val move = ease(phase(t, 0.55f, 0.8f)) * w * 0.22f
    val cx = w * 0.36f + move
    val cy = h * 0.5f
    // A little drawing: a triangle on a line.
    val tri = Path().apply { moveTo(cx - 22.dp.toPx(), cy + 16.dp.toPx()); lineTo(cx, cy - 20.dp.toPx()); lineTo(cx + 22.dp.toPx(), cy + 16.dp.toPx()); close() }
    ink(tri, c.ink, 2.6.dp.toPx())
    drawLine(c.ink, Offset(cx - 34.dp.toPx(), cy + 16.dp.toPx()), Offset(cx + 34.dp.toPx(), cy + 16.dp.toPx()), 2.6.dp.toPx(), cap = StrokeCap.Round)
    // The lasso loop drawn around it (dashed), which then travels with it.
    val loopPts = List(60) { k ->
      val a = -PI.toFloat() / 2 + k / 59f * PI.toFloat() * 2.1f
      Offset(cx + cos(a) * 58.dp.toPx(), cy + sin(a) * 46.dp.toPx())
    }
    val drawn = ease(phase(t, 0.05f, 0.45f))
    if (t < 0.92f) drawPath(partial(loopPts, drawn), c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))))
    // "Paste" bubble at the end.
    val paste = phase(t, 0.82f, 0.88f)
    if (paste > 0f) {
      val p = Offset(w * 0.2f, h * 0.18f)
      drawRoundRect(c.accent.copy(alpha = paste), Offset(p.x - 28.dp.toPx(), p.y - 12.dp.toPx()), Size(56.dp.toPx(), 24.dp.toPx()), CornerRadius(12.dp.toPx()))
    }
  }
}

@Composable
private fun VoicePicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(2400)
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    // A memo beside a page: a round button and a moving sound wave.
    drawRoundRect(c.line, Offset(w * 0.06f, h * 0.08f), Size(w * 0.5f, h * 0.84f), CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx()))
    for (k in 0 until 6) drawLine(c.line, Offset(w * 0.12f, h * (0.2f + k * 0.12f)), Offset(w * 0.5f, h * (0.2f + k * 0.12f)), 1.dp.toPx())
    val b = Offset(w * 0.74f, h * 0.32f)
    val pulse = (sin(t * 2 * PI.toFloat()) * 0.5f + 0.5f)
    drawCircle(Color(0xFFE5484D).copy(alpha = 0.18f), (20f + 6f * pulse).dp.toPx(), b)
    drawCircle(Color(0xFFE5484D), 14.dp.toPx(), b)
    for (k in 0 until 9) {
      val x = w * 0.62f + k * 9.dp.toPx()
      val amp = (0.3f + 0.7f * (sin(k * 1.3f + t * 2 * PI.toFloat() * 2) * 0.5f + 0.5f)) * 22.dp.toPx()
      drawLine(c.accent, Offset(x, h * 0.66f - amp / 2), Offset(x, h * 0.66f + amp / 2), 4.dp.toPx(), cap = StrokeCap.Round)
    }
  }
}

@Composable
private fun EraserPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3400)
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    ruledPage(c)
    // A word written, then the eraser (with the pen's button held) wipes part of it away.
    val pts = cursive(w * 0.12f, w * 0.88f, 26.dp.toPx() * 3 - 3.dp.toPx(), 14.dp.toPx(), seed = 1)
    val wiped = ease(phase(t, 0.45f, 0.8f))
    val visible = ease(phase(t, 0f, 0.35f))
    val path = partial(pts, visible)
    val cut = w * 0.12f + w * 0.76f * wiped
    drawContext.canvas.save()
    if (wiped > 0f) drawContext.canvas.clipRect(cut, 0f, size.width, size.height)
    ink(path, c.ink, 2.6.dp.toPx())
    drawContext.canvas.restore()
    if (wiped in 0.01f..0.99f) drawCircle(c.accent, 12.dp.toPx(), Offset(cut, 26.dp.toPx() * 3 - 9.dp.toPx()), style = Stroke(2.dp.toPx()))
    // The S Pen lying below, its side button lit while it's held.
    val y = h * 0.84f
    val l = w * 0.2f
    val r = w * 0.74f
    val body = Path().apply {
      moveTo(l, y - 7.dp.toPx()); lineTo(r, y - 7.dp.toPx()); lineTo(r + 22.dp.toPx(), y); lineTo(r, y + 7.dp.toPx()); lineTo(l, y + 7.dp.toPx()); close()
    }
    drawPath(body, c.inkMuted)
    drawCircle(c.ink, 2.dp.toPx(), Offset(r + 20.dp.toPx(), y))
    val pressed = wiped in 0.01f..0.99f
    drawRoundRect(if (pressed) c.accent else c.surface, Offset(r - 30.dp.toPx(), y - 3.dp.toPx()), Size(18.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
  }
}

@Composable
private fun BackupPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3000)
  Canvas(modifier) {
    val w = size.width
    val h = size.height
    // A cloud, a page rising into it, and a tick once it's there.
    val cx = w / 2; val cy = h * 0.36f
    val r = min(w, h) * 0.13f
    // Solid, so the puffs don't show where they overlap.
    val puff = c.accentSoft
    listOf(Offset(cx - r * 1.1f, cy + r * 0.3f) to r * 0.9f, Offset(cx, cy - r * 0.2f) to r * 1.2f, Offset(cx + r * 1.1f, cy + r * 0.3f) to r * 0.85f).forEach { (o, rr) -> drawCircle(puff, rr, o) }
    drawRoundRect(puff, Offset(cx - r * 2f, cy + r * 0.1f), Size(r * 4f, r * 1.1f), CornerRadius(r * 0.55f))
    val rise = ease(phase(t, 0.05f, 0.5f))
    val pageY = h * 0.86f - (h * 0.86f - cy - r * 0.2f) * rise
    val fade = 1f - phase(t, 0.5f, 0.6f)
    drawRoundRect(c.ink.copy(alpha = fade), Offset(cx - 16.dp.toPx(), pageY - 20.dp.toPx()), Size(32.dp.toPx(), 40.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(2.dp.toPx()))
    val tick = ease(phase(t, 0.6f, 0.75f))
    if (tick > 0f) {
      val a = Offset(cx - 12.dp.toPx(), cy + 2.dp.toPx()); val m = Offset(cx - 3.dp.toPx(), cy + 11.dp.toPx()); val z = Offset(cx + 14.dp.toPx(), cy - 8.dp.toPx())
      ink(partial(listOf(a, m, z), tick), c.accent, 4.dp.toPx())
    }
  }
}
