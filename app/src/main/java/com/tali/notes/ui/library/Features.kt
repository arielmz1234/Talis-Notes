package com.tali.notes.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tali.notes.R
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The features walkthrough (Settings › Tips and tricks): the smaller things the app can do, each
 * with how to use it or turn it on, in the same card as the first-run tour.
 */
private val features =
  listOf(
    TourStep(R.string.feat_paste_title, R.string.feat_paste_body) { PastePicture(it) },
    TourStep(R.string.feat_circle_title, R.string.feat_circle_body) { CircleSelectPicture(it) },
    TourStep(R.string.feat_box_title, R.string.feat_box_body) { BoxPicture(it) },
    TourStep(R.string.feat_starpens_title, R.string.feat_starpens_body) { StarPensPicture(it) },
    TourStep(R.string.feat_fingers_title, R.string.feat_fingers_body) { FingersPicture(it) },
    TourStep(R.string.feat_tidy_title, R.string.feat_tidy_body) { AnglePicture(it) },
    TourStep(R.string.feat_dots_title, R.string.feat_dots_body) { RadiusPicture(it) },
    TourStep(R.string.feat_fill_title, R.string.feat_fill_body) { FillPicture(it) },
    TourStep(R.string.feat_shot_title, R.string.feat_shot_body) { ShotPicture(it) },
    TourStep(R.string.feat_lock_title, R.string.feat_lock_body) { LockPicture(it) },
    TourStep(R.string.feat_laser_title, R.string.feat_laser_body) { LaserPicture(it) },
    TourStep(R.string.feat_pages_title, R.string.feat_pages_body) { PagesPicture(it) },
    TourStep(R.string.feat_quick_title, R.string.feat_quick_body) { QuickOpenPicture(it) },
    TourStep(R.string.feat_room_title, R.string.feat_room_body) { RoomPicture(it) },
    TourStep(R.string.feat_star_title, R.string.feat_star_body) { StarNotebookPicture(it) },
    TourStep(R.string.feat_colours_title, R.string.feat_colours_body) { ColoursPicture(it) },
  )

@Composable
fun FeaturesCard(onDone: () -> Unit) = StepsCard(features, onDone, finish = R.string.done, heading = stringResource(R.string.tips_and_tricks))

// ---- Small drawing helpers ----

private val Blue = Color(0xFF2F6FE0)
private val Red = Color(0xFFE5484D)
private val Gold = Color(0xFFF5B83D)

/** A short word in the accent's pill, as the app's badges and bubbles look. */
private fun DrawScope.pill(text: String, at: Offset, bg: Color, alpha: Float = 1f) {
  val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textSize = 12.dp.toPx()
    color = Color.White.copy(alpha = alpha).toArgb()
    textAlign = android.graphics.Paint.Align.CENTER
    isFakeBoldText = true
  }
  val w = paint.measureText(text) + 18.dp.toPx()
  val h = 24.dp.toPx()
  drawRoundRect(bg.copy(alpha = alpha), Offset(at.x - w / 2, at.y - h / 2), Size(w, h), CornerRadius(h / 2))
  drawContext.canvas.nativeCanvas.drawText(text, at.x, at.y + 4.5.dp.toPx(), paint)
}

/** A dot like a shape's handle. */
private fun DrawScope.handle(at: Offset, c: TaliColors) {
  drawCircle(Color.White, 5.5.dp.toPx(), at)
  drawCircle(c.accent, 5.5.dp.toPx(), at, style = Stroke(1.8.dp.toPx()))
}

/** A fingertip: a soft round touch. */
private fun DrawScope.finger(at: Offset, c: TaliColors, alpha: Float = 1f) {
  drawCircle(c.accent.copy(alpha = 0.18f * alpha), 17.dp.toPx(), at)
  drawCircle(c.accent.copy(alpha = 0.55f * alpha), 17.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
}

private fun DrawScope.dashedRect(l: Float, t: Float, r: Float, b: Float, c: TaliColors, alpha: Float = 1f) =
  drawRect(c.accent.copy(alpha = alpha), Offset(l, t), Size(r - l, b - t), style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))

private fun DrawScope.triangle(cx: Float, cy: Float, s: Float, color: Color, width: Float = 2.6.dp.toPx()) {
  val p = Path().apply { moveTo(cx - s, cy + s * 0.75f); lineTo(cx, cy - s * 0.85f); lineTo(cx + s, cy + s * 0.75f); close() }
  ink(p, color, width)
}

/** A padlock, closed or with its shackle lifted by [open] (0..1). */
private fun DrawScope.padlock(at: Offset, c: TaliColors, open: Float) {
  val s = 1.dp.toPx()
  drawRoundRect(Color.White, Offset(at.x - 16 * s, at.y - 16 * s), Size(32 * s, 32 * s), CornerRadius(10 * s))
  drawRoundRect(c.accent, Offset(at.x - 8 * s, at.y - 2 * s), Size(16 * s, 12 * s), CornerRadius(2.5f * s))
  val lift = 4 * s * open
  val shackle = Path().apply {
    moveTo(at.x - 5 * s, at.y - 2 * s - lift)
    lineTo(at.x - 5 * s, at.y - 6 * s - lift)
    cubicTo(at.x - 5 * s, at.y - 12 * s - lift, at.x + 5 * s, at.y - 12 * s - lift, at.x + 5 * s, at.y - 6 * s - lift)
    lineTo(at.x + 5 * s, at.y - 2 * s - (if (open > 0.5f) 4 * s + lift else 0f))
  }
  drawPath(shackle, c.accent, style = Stroke(2.4f * s, cap = StrokeCap.Round))
}

// ---- The pictures ----

@Composable
private fun PastePicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3800)
  val pasteWord = stringResource(R.string.paste)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    triangle(w * 0.25f, h * 0.38f, 22.dp.toPx(), c.ink)
    // A finger holds still on an empty spot; a ring fills, Paste pops up, and the copy lands.
    val spot = Offset(w * 0.68f, h * 0.62f)
    val hold = phase(t, 0.1f, 0.4f)
    val bubble = ease(phase(t, 0.4f, 0.48f))
    val landed = ease(phase(t, 0.58f, 0.7f))
    if (t < 0.58f && t > 0.08f) {
      finger(spot, c)
      drawArc(c.accent, -90f, 360f * hold, false, Offset(spot.x - 21.dp.toPx(), spot.y - 21.dp.toPx()), Size(42.dp.toPx(), 42.dp.toPx()), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
    if (bubble > 0f && t < 0.6f) pill(pasteWord, Offset(spot.x, spot.y - 40.dp.toPx() - 6.dp.toPx() * (1 - bubble)), c.accent, bubble)
    if (landed > 0f) triangle(spot.x, spot.y, 22.dp.toPx() * (0.9f + 0.1f * landed), c.ink.copy(alpha = landed))
  }
}

@Composable
private fun CircleSelectPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4200)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    ruledPage(c)
    val pts = cursive(w * 0.28f, w * 0.72f, h * 0.55f, 12.dp.toPx(), seed = 2)
    ink(partial(pts, 1f), c.ink, 2.4.dp.toPx())
    val ring = List(64) { k ->
      val a = -PI.toFloat() * 0.6f + k / 63f * PI.toFloat() * 2.08f
      Offset(w * 0.5f + cos(a) * w * 0.32f, h * 0.5f + sin(a) * h * 0.24f)
    }
    val drawn = ease(phase(t, 0.02f, 0.35f))
    val press = phase(t, 0.42f, 0.6f)
    val caught = ease(phase(t, 0.6f, 0.75f))
    if (caught <= 0f) {
      // Drawn in ink, then glowing while the pen presses on it.
      if (press > 0f) drawPath(partial(ring, 1f), c.accent.copy(alpha = 0.25f * press), style = Stroke(10.dp.toPx(), cap = StrokeCap.Round))
      ink(partial(ring, drawn), Blue, 2.4.dp.toPx())
      if (press > 0f) finger(ring[10], c)
    } else {
      // Caught: it pulls in tight as the selection's dashed frame.
      val l = w * 0.26f; val r = w * 0.74f; val top = h * 0.38f; val b = h * 0.62f
      val box = List(64) { k ->
        val p = ring[k]
        val q = Offset(p.x.coerceIn(l, r), p.y.coerceIn(top, b))
        Offset(p.x + (q.x - p.x) * caught, p.y + (q.y - p.y) * caught)
      }
      drawPath(partial(box, 1f), c.accent, style = Stroke(1.8.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))))
    }
  }
}

@Composable
private fun BoxPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val pts = cursive(w * 0.3f, w * 0.7f, h * 0.62f, 12.dp.toPx(), seed = 1)
    ink(partial(pts, 1f), c.ink, 2.4.dp.toPx())
    val l = w * 0.24f; val r = w * 0.76f; val top = h * 0.42f; val b = h * 0.74f
    dashedRect(l, top, r, b, c)
    // The menu above, its square button pressed, then the box drawn in the pen's colour.
    val menuY = h * 0.22f
    drawRoundRect(c.surface, Offset(w * 0.3f, menuY - 16.dp.toPx()), Size(w * 0.4f, 32.dp.toPx()), CornerRadius(14.dp.toPx()))
    drawRoundRect(c.line, Offset(w * 0.3f, menuY - 16.dp.toPx()), Size(w * 0.4f, 32.dp.toPx()), CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx()))
    val pressed = t in 0.25f..0.38f
    val btn = Offset(w * 0.56f, menuY)
    if (pressed) drawCircle(c.accentSoft, 14.dp.toPx(), btn)
    drawRect(c.ink, Offset(btn.x - 6.dp.toPx(), btn.y - 6.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()), style = Stroke(1.8.dp.toPx()))
    listOf(w * 0.38f, w * 0.47f, w * 0.65f).forEach { x -> drawCircle(c.inkFaint, 2.5.dp.toPx(), Offset(x, menuY)) }
    val box = ease(phase(t, 0.4f, 0.65f))
    val g = 5.dp.toPx()
    val outline = listOf(Offset(l + g, top + g), Offset(r - g, top + g), Offset(r - g, b - g), Offset(l + g, b - g), Offset(l + g, top + g))
    if (box > 0f) ink(partial(outline, box), Blue, 2.6.dp.toPx())
  }
}

@Composable
private fun StarPensPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val pens = listOf(c.ink, Blue, Red, Gold)
    val starred = listOf(true, false, true, true)
    // Two presses of the button hop to the next starred pen.
    val order = listOf(0, 2, 3)
    val at = order[(t * 3).toInt().coerceIn(0, 2)]
    pens.forEachIndexed { i, col ->
      val x = w * (0.2f + i * 0.2f)
      val top = h * 0.2f
      if (i == at) drawRoundRect(c.accentSoft, Offset(x - 18.dp.toPx(), top - 8.dp.toPx()), Size(36.dp.toPx(), 92.dp.toPx()), CornerRadius(12.dp.toPx()))
      drawRoundRect(Color.White, Offset(x - 8.dp.toPx(), top + 12.dp.toPx()), Size(16.dp.toPx(), 64.dp.toPx()), CornerRadius(4.dp.toPx()))
      drawRoundRect(c.line, Offset(x - 8.dp.toPx(), top + 12.dp.toPx()), Size(16.dp.toPx(), 64.dp.toPx()), CornerRadius(4.dp.toPx()), style = Stroke(1.dp.toPx()))
      drawRect(col, Offset(x - 8.dp.toPx(), top + 40.dp.toPx()), Size(16.dp.toPx(), 6.dp.toPx()))
      val tip = Path().apply { moveTo(x - 6.dp.toPx(), top + 12.dp.toPx()); lineTo(x, top); lineTo(x + 6.dp.toPx(), top + 12.dp.toPx()); close() }
      drawPath(tip, col)
      if (starred[i]) star(Offset(x + 10.dp.toPx(), top + 76.dp.toPx()), 6.dp.toPx(), Gold)
    }
    // The S Pen's button, flashing twice each hop.
    val local = (t * 3) % 1f
    val flash = (local in 0.05f..0.15f) || (local in 0.22f..0.32f)
    val y = h * 0.86f
    drawRoundRect(c.inkMuted, Offset(w * 0.18f, y - 6.dp.toPx()), Size(w * 0.6f, 12.dp.toPx()), CornerRadius(6.dp.toPx()))
    drawRoundRect(if (flash) c.accent else c.surface, Offset(w * 0.56f, y - 3.dp.toPx()), Size(18.dp.toPx(), 6.dp.toPx()), CornerRadius(3.dp.toPx()))
  }
}

private fun DrawScope.star(at: Offset, r: Float, color: Color) {
  val p = Path().apply {
    for (k in 0 until 10) {
      val rr = if (k % 2 == 0) r else r * 0.42f
      val a = -PI / 2 + k * PI / 5
      val q = Offset(at.x + rr * cos(a).toFloat(), at.y + rr * sin(a).toFloat())
      if (k == 0) moveTo(q.x, q.y) else lineTo(q.x, q.y)
    }
    close()
  }
  drawPath(p, color)
}

@Composable
private fun FingersPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4400)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    ruledPage(c)
    val pts = cursive(w * 0.12f, w * 0.88f, h * 0.4f, 13.dp.toPx(), seed = 3)
    // Written; two fingers tap and it's gone; three fingers tap and it's back.
    val gone = t in 0.35f..0.72f
    if (!gone) ink(partial(pts, ease(phase(t, 0f, 0.25f)).coerceAtLeast(if (t > 0.72f) 1f else 0f)), c.ink, 2.4.dp.toPx())
    val two = t in 0.28f..0.4f
    val three = t in 0.66f..0.78f
    val y = h * 0.74f
    if (two) listOf(w * 0.42f, w * 0.58f).forEach { finger(Offset(it, y), c) }
    if (three) listOf(w * 0.34f, w * 0.5f, w * 0.66f).forEach { finger(Offset(it, y), c) }
  }
}

@Composable
private fun AnglePicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4000)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val o = Offset(w * 0.3f, h * 0.7f)
    val len = w * 0.5f
    // The pen swings the line up; it clicks onto 45° and stays there for a moment.
    val raw = ease(phase(t, 0.05f, 0.5f)) * 60f
    val deg = if (raw in 38f..52f || t > 0.5f) 45f else raw
    val a = Math.toRadians(-deg.toDouble()).toFloat()
    val end = Offset(o.x + cos(a) * len, o.y + sin(a) * len)
    drawLine(c.line, o, Offset(o.x + len, o.y), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())))
    drawLine(c.ink, o, end, 3.dp.toPx(), cap = StrokeCap.Round)
    handle(o, c); handle(end, c)
    pill("${Math.round(deg)}°", Offset(w * 0.5f, h * 0.2f), if (deg == 45f) c.accent else c.inkMuted)
  }
}

@Composable
private fun RadiusPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val o = Offset(w * 0.42f, h * 0.55f)
    val grow = if (t < 0.5f) ease(phase(t, 0.1f, 0.45f)) else 1f - ease(phase(t, 0.6f, 0.95f))
    val r = h * (0.2f + 0.14f * grow)
    drawCircle(c.ink, r, o, style = Stroke(2.6.dp.toPx()))
    val end = Offset(o.x + r, o.y)
    drawLine(c.accent, o, end, 2.dp.toPx(), cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 5.dp.toPx())))
    drawCircle(c.accent, 3.dp.toPx(), o)
    handle(end, c)
    pill("r ${String.format(java.util.Locale.US, "%.1f", 2f + 1.4f * grow)} cm", Offset(o.x + r / 2, o.y - 20.dp.toPx()), c.accent)
  }
}

@Composable
private fun FillPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3400)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    // A hand-drawn closed outline; a tap inside spreads the colour to its edges.
    val blob = Path().apply {
      val n = 48
      for (k in 0..n) {
        val a = k / n.toFloat() * 2 * PI.toFloat()
        val rr = 1f + 0.12f * sin(a * 3f) + 0.06f * cos(a * 5f)
        val p = Offset(w * 0.5f + cos(a) * w * 0.3f * rr, h * 0.5f + sin(a) * h * 0.3f * rr)
        if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
      }
      close()
    }
    val tap = Offset(w * 0.45f, h * 0.52f)
    val spread = ease(phase(t, 0.25f, 0.6f))
    if (spread > 0f) {
      drawContext.canvas.save()
      drawContext.canvas.clipPath(blob)
      drawCircle(Color(0xFF8BC4F0), w * 0.6f * spread, tap)
      drawContext.canvas.restore()
    }
    ink(blob, c.ink, 2.6.dp.toPx())
    if (t in 0.15f..0.3f) finger(tap, c)
  }
}

@Composable
private fun ShotPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4000)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    ruledPage(c)
    triangle(w * 0.3f, h * 0.42f, 18.dp.toPx(), c.ink)
    ink(partial(cursive(w * 0.48f, w * 0.85f, h * 0.48f, 10.dp.toPx(), seed = 4), 1f), Blue, 2.2.dp.toPx())
    // The lasso shows the area it will take, a flash, then the picture of it slides up.
    val drawn = ease(phase(t, 0.05f, 0.35f))
    val l = w * 0.14f; val r = w * 0.9f; val top = h * 0.24f; val b = h * 0.62f
    if (t < 0.45f) dashedRect(l, top, l + (r - l) * drawn, top + (b - top) * drawn, c)
    val flash = 1f - phase(t, 0.45f, 0.55f)
    if (t in 0.45f..0.55f) drawRect(Color.White.copy(alpha = flash * 0.9f), Offset(l, top), Size(r - l, b - top))
    val card = ease(phase(t, 0.55f, 0.7f))
    if (card > 0f) {
      val y = h - (h * 0.42f) * card
      drawRoundRect(Color.White, Offset(w * 0.22f, y), Size(w * 0.56f, h * 0.36f), CornerRadius(10.dp.toPx()))
      drawRoundRect(c.line, Offset(w * 0.22f, y), Size(w * 0.56f, h * 0.36f), CornerRadius(10.dp.toPx()), style = Stroke(1.dp.toPx()))
      triangle(w * 0.36f, y + h * 0.16f, 10.dp.toPx(), c.ink, 1.8.dp.toPx())
      ink(partial(cursive(w * 0.46f, w * 0.72f, y + h * 0.2f, 6.dp.toPx(), seed = 4), 1f), Blue, 1.6.dp.toPx())
    }
  }
}

@Composable
private fun LockPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4200)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    drawRoundRect(c.ink, Offset(w * 0.25f, h * 0.35f), Size(w * 0.5f, h * 0.4f), CornerRadius(4.dp.toPx()), style = Stroke(2.6.dp.toPx()))
    // Locked; a finger holds on it, a ring fills, and it unlocks.
    val hold = phase(t, 0.3f, 0.65f)
    val open = ease(phase(t, 0.65f, 0.75f))
    val lock = Offset(w * 0.5f, h * 0.2f)
    padlock(lock, c, open)
    if (t in 0.25f..0.68f) {
      val f = Offset(w * 0.5f, h * 0.6f)
      finger(f, c)
      drawArc(c.accent, -90f, 360f * hold, false, Offset(lock.x - 21.dp.toPx(), lock.y - 21.dp.toPx()), Size(42.dp.toPx(), 42.dp.toPx()), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
    }
  }
}

@Composable
private fun LaserPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    ruledPage(c)
    // A red glowing loop, drawn, held a moment, then faded and thinned away.
    val pts = List(60) { k ->
      val a = k / 59f * 2.2f * PI.toFloat()
      Offset(w * 0.5f + cos(a) * w * 0.28f * (1 - k / 200f), h * 0.5f + sin(a) * h * 0.22f)
    }
    val drawn = ease(phase(t, 0f, 0.4f))
    val fade = 1f - phase(t, 0.62f, 0.85f)
    if (fade > 0f) {
      val p = partial(pts, drawn)
      drawPath(p, Red.copy(alpha = 0.25f * fade), style = Stroke(12.dp.toPx() * fade, cap = StrokeCap.Round))
      drawPath(p, Color(0xFFFFB3B5).copy(alpha = fade), style = Stroke(2.5.dp.toPx() * fade + 0.5f, cap = StrokeCap.Round))
    }
  }
}

@Composable
private fun PagesPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4000)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val pw = w * 0.2f; val ph = h * 0.36f
    fun slot(i: Int) = Offset(w * 0.12f + (i % 3) * w * 0.28f, h * 0.08f + (i / 3) * h * 0.48f)
    // Page 1 is held, lifts, and moves to the last place; the others make way.
    val move = ease(phase(t, 0.2f, 0.6f))
    for (p in 1..4) {
      val from = slot(p); val to = slot(p - 1)
      val at = Offset(from.x + (to.x - from.x) * move, from.y + (to.y - from.y) * move)
      drawRoundRect(Color.White, at, Size(pw, ph), CornerRadius(5.dp.toPx()))
      drawRoundRect(c.line, at, Size(pw, ph), CornerRadius(5.dp.toPx()), style = Stroke(1.dp.toPx()))
      for (k in 1..3) drawLine(c.line, Offset(at.x + 6.dp.toPx(), at.y + ph * k / 4f), Offset(at.x + pw - 6.dp.toPx(), at.y + ph * k / 4f), 1.dp.toPx())
    }
    val a = slot(0); val z = slot(4)
    val lift = if (t in 0.1f..0.7f) 1f else 0f
    val held = Offset(a.x + (z.x - a.x) * move, a.y + (z.y - a.y) * move - 6.dp.toPx() * lift)
    drawRoundRect(Color.White, held, Size(pw, ph), CornerRadius(5.dp.toPx()))
    drawRoundRect(c.accent, held, Size(pw, ph), CornerRadius(5.dp.toPx()), style = Stroke(2.dp.toPx()))
    ink(partial(cursive(held.x + 6.dp.toPx(), held.x + pw - 6.dp.toPx(), held.y + ph * 0.4f, 5.dp.toPx()), 1f), c.ink, 1.4.dp.toPx())
    if (lift > 0f) finger(Offset(held.x + pw / 2, held.y + ph / 2), c)
  }
}

@Composable
private fun QuickOpenPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    // The button beside Back, and recent notebooks drifting in under it.
    val b = Offset(w * 0.18f, h * 0.14f)
    drawCircle(c.accentSoft, 16.dp.toPx(), b)
    drawCircle(c.accent, 7.dp.toPx(), b, style = Stroke(1.8.dp.toPx()))
    drawLine(c.accent, b, Offset(b.x, b.y - 4.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
    drawLine(c.accent, b, Offset(b.x + 3.dp.toPx(), b.y + 1.dp.toPx()), 1.8.dp.toPx(), cap = StrokeCap.Round)
    val spines = listOf(Color(0xFF7C5CE0), Color(0xFF2FA37A), Red, Blue)
    spines.forEachIndexed { i, col ->
      val k = ease(phase(t, 0.08f + i * 0.07f, 0.28f + i * 0.07f)) * (1f - phase(t, 0.85f, 0.95f))
      if (k <= 0f) return@forEachIndexed
      val y = h * (0.3f + i * 0.17f) + 8.dp.toPx() * (1 - k)
      drawRoundRect(Color.White.copy(alpha = k), Offset(w * 0.12f, y), Size(w * 0.76f, h * 0.14f), CornerRadius(8.dp.toPx()))
      drawRoundRect(col.copy(alpha = k), Offset(w * 0.14f, y + 4.dp.toPx()), Size(20.dp.toPx(), h * 0.14f - 8.dp.toPx()), CornerRadius(3.dp.toPx()))
      drawLine(c.inkMuted.copy(alpha = k), Offset(w * 0.3f, y + h * 0.07f), Offset(w * (0.6f + 0.06f * (i % 2)), y + h * 0.07f), 3.dp.toPx(), cap = StrokeCap.Round)
    }
    if (t in 0.5f..0.62f) finger(Offset(w * 0.5f, h * 0.47f + h * 0.07f), c)
  }
}

@Composable
private fun RoomPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3800)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    // A screen with its bars; they slide away and the page fills it, then come back.
    val hide = if (t < 0.5f) ease(phase(t, 0.15f, 0.35f)) else 1f - ease(phase(t, 0.7f, 0.9f))
    drawRoundRect(c.desk, Offset(0f, 0f), Size(w, h), CornerRadius(12.dp.toPx()))
    val page = Size(w * (0.62f + 0.16f * hide), h * (0.7f + 0.22f * hide))
    val po = Offset((w - page.width) / 2, h * (0.22f - 0.18f * hide))
    drawRoundRect(Color.White, po, page, CornerRadius(4.dp.toPx()))
    var y = po.y + 14.dp.toPx(); while (y < po.y + page.height) { drawLine(c.line, Offset(po.x + 6.dp.toPx(), y), Offset(po.x + page.width - 6.dp.toPx(), y), 1.dp.toPx()); y += 14.dp.toPx() }
    val barY = h * 0.08f - h * 0.2f * hide
    drawRoundRect(c.surface, Offset(w * 0.2f, barY - 10.dp.toPx()), Size(w * 0.6f, 20.dp.toPx()), CornerRadius(10.dp.toPx()))
    drawRoundRect(c.surface, Offset(w * 0.84f, barY - 10.dp.toPx()), Size(w * 0.12f, 20.dp.toPx()), CornerRadius(10.dp.toPx()))
    drawRect(c.inkMuted.copy(alpha = 0.5f * (1 - hide)), Offset(0f, h - 6.dp.toPx()), Size(w, 6.dp.toPx()))
  }
}

@Composable
private fun StarNotebookPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(3600)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    val spines = listOf(Color(0xFF7C5CE0), Color(0xFF2FA37A), Blue)
    spines.forEachIndexed { i, col ->
      val x = w * (0.06f + i * 0.32f)
      val y = h * 0.28f
      drawRoundRect(Color.White, Offset(x, y), Size(w * 0.26f, h * 0.46f), CornerRadius(8.dp.toPx()))
      drawRoundRect(c.line, Offset(x, y), Size(w * 0.26f, h * 0.46f), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
      drawRoundRect(col, Offset(x, y), Size(8.dp.toPx(), h * 0.46f), CornerRadius(3.dp.toPx()))
      for (k in 1..3) drawLine(c.line, Offset(x + 14.dp.toPx(), y + h * 0.46f * k / 4f), Offset(x + w * 0.26f - 6.dp.toPx(), y + h * 0.46f * k / 4f), 1.dp.toPx())
    }
    // Held, then a star pops onto its corner.
    val held = t in 0.1f..0.35f
    val card = Offset(w * 0.38f + w * 0.13f, h * 0.51f)
    if (held) finger(card, c)
    val pop = ease(phase(t, 0.38f, 0.5f))
    if (pop > 0f) {
      val s = if (pop < 1f) pop * 1.25f else 1f
      star(Offset(w * 0.38f + w * 0.24f, h * 0.7f), 9.dp.toPx() * s, Gold)
    }
  }
}

@Composable
private fun ColoursPicture(modifier: Modifier) {
  val c = Tali.colors
  val t = loop(4800)
  Canvas(modifier) {
    val w = size.width; val h = size.height
    // Lilac, Sky, Sage and Peach, the ring gliding from one to the next and the sky following.
    val skies = listOf(Color(0xFFD9CCF7) to Color(0xFF8E6CF0), Color(0xFFCFE2F7) to Color(0xFF3B82D6), Color(0xFFD5EADB) to Color(0xFF2F9E73), Color(0xFFFBDCCB) to Color(0xFFE07A5F))
    val f = t * 4f
    val i = f.toInt().coerceIn(0, 3)
    val sky = skies[i].first
    drawRoundRect(sky, Offset(w * 0.06f, h * 0.06f), Size(w * 0.88f, h * 0.48f), CornerRadius(14.dp.toPx()))
    val r = min(w, h) * 0.07f
    listOf(Offset(w * 0.32f, h * 0.3f) to r, Offset(w * 0.42f, h * 0.24f) to r * 1.3f, Offset(w * 0.53f, h * 0.3f) to r).forEach { (o, rr) -> drawCircle(Color.White, rr, o) }
    skies.forEachIndexed { k, (_, accent) ->
      val o = Offset(w * (0.2f + k * 0.2f), h * 0.76f)
      drawCircle(accent, 13.dp.toPx(), o)
      if (k == i) drawCircle(c.accent, 18.dp.toPx(), o, style = Stroke(2.2.dp.toPx()))
    }
  }
}
