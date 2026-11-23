package com.tali.notes.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Test

/**
 * Scribble to delete over many kinds of scribble and of ordinary writing, each drawn 300 ways
 * (size, angle, slant, wobble, how even the legs are): how often each counts as a scribble. Writing
 * that counts still deletes nothing unless its path runs over something ([ScratchOut.takes]).
 */
class ScribbleKindsTest {
  private val tau = 2 * PI.toFloat()

  /** Through [corners] with rounded turns, a point every ~2 units, turned by [angle] and wobbled. */
  private fun poly(corners: List<Pt>, rnd: Random, angle: Float, jitter: Float = 0.6f): List<Pt> {
    val out = ArrayList<Pt>()
    for (i in 1 until corners.size) {
      val a = corners[i - 1]
      val b = corners[i]
      val n = maxOf((a.dist(b) / 2f).toInt(), 2)
      for (k in 0 until n) out += a + (b - a) * (k / n.toFloat())
    }
    out += corners.last()
    // Round the corners a little like a hand does (moving average).
    val smooth = out.indices.map { i ->
      val lo = maxOf(0, i - 2)
      val hi = minOf(out.size - 1, i + 2)
      var x = 0f
      var y = 0f
      for (j in lo..hi) {
        x += out[j].x
        y += out[j].y
      }
      Pt(x / (hi - lo + 1), y / (hi - lo + 1))
    }
    return turned(smooth, rnd, angle, jitter)
  }

  private fun turned(pts: List<Pt>, rnd: Random, angle: Float, jitter: Float = 0.6f): List<Pt> {
    val c = cos(angle)
    val s = sin(angle)
    return pts.map { p -> Pt(p.x * c - p.y * s + (rnd.nextFloat() - 0.5f) * jitter, p.x * s + p.y * c + (rnd.nextFloat() - 0.5f) * jitter) }
  }

  private fun Random.f(a: Float, b: Float) = a + nextFloat() * (b - a)

  // ---- Scribbles ----

  /** Back and forth along a line, moving on a little each leg, uneven lengths. */
  private fun scrub(r: Random): List<Pt> {
    val legs = r.nextInt(5, 14)
    val len = r.f(25f, 140f)
    val drift = len * r.f(0f, 0.22f)
    val corners = (0..legs).map { i -> Pt((if (i % 2 == 0) 0f else len) + r.f(-0.15f, 0.15f) * len, i * drift + r.f(-0.05f, 0.05f) * len) }
    return poly(corners, r, r.f(0f, tau))
  }

  /** Up and down across a word, travelling sideways (a slanted zigzag). */
  private fun zigzagAcross(r: Random): List<Pt> {
    val legs = r.nextInt(6, 18)
    val h = r.f(20f, 70f)
    val step = h * r.f(0.05f, 0.3f)
    val slant = r.f(-0.3f, 1.1f)
    val corners = (0..legs).map { i -> val y = if (i % 2 == 0) 0f else h * r.f(0.75f, 1.1f); Pt(i * step + y * slant, y) }
    return poly(corners, r, r.f(-0.4f, 0.4f))
  }

  /** Loops travelling sideways ("eeee" / "llll" scrubbing), slanted. */
  private fun loops(r: Random): List<Pt> {
    val n = r.nextInt(3, 10)
    val h = r.f(20f, 70f)
    val w = n * h * r.f(0.15f, 0.5f)
    val slant = r.f(-0.3f, 1.1f)
    val m = n * 40
    val pts = (0..m).map { i ->
      val t = i / m.toFloat() * n * tau
      val x = w * i / m + w / n * r.f(0.3f, 0.45f) * sin(t)
      val y = h / 2 * cos(t) * (1f + 0.2f * sin(t * 0.37f))
      Pt(x + y * slant, y)
    }
    return turned(pts, r, r.f(-0.4f, 0.4f))
  }

  /** Round and round over the same spot, drifting (a tangle). */
  private fun tangle(r: Random): List<Pt> {
    val n = r.nextInt(3, 8)
    val rad = r.f(12f, 40f)
    val travel = rad * r.f(0f, 2.5f)
    val m = n * 40
    val pts = (0..m).map { i ->
      val t = i / m.toFloat() * n * tau
      val rr = rad * (1f + 0.25f * sin(t * 0.53f))
      Pt(travel * i / m + rr * cos(t), rr * 0.8f * sin(t))
    }
    return turned(pts, r, r.f(0f, tau))
  }

  /** Scrubbed in place: very short drift, many legs. */
  private fun inPlace(r: Random): List<Pt> {
    val legs = r.nextInt(6, 16)
    val len = r.f(20f, 90f)
    val corners = (0..legs).map { i -> Pt((if (i % 2 == 0) 0f else len) + r.f(-0.2f, 0.2f) * len, r.f(-0.08f, 0.08f) * len) }
    return poly(corners, r, r.f(0f, tau), jitter = 1.2f)
  }

  // ---- Not scribbles ----

  /** A word of joined letters: humps, tall loops, small loops, vees and o's, mixed heights. */
  private fun word(r: Random): List<Pt> {
    val xh = r.f(10f, 30f)
    val letters = r.nextInt(3, 9)
    val pts = ArrayList<Pt>()
    var x = 0f
    repeat(letters) {
      val kind = r.nextInt(5)
      val w = xh * r.f(0.5f, 1f)
      val h = if (kind == 1) xh * r.f(1.8f, 2.4f) else xh
      val m = 30
      for (i in 0..m) {
        val t = i / m.toFloat()
        val p =
          when (kind) {
            0 -> Pt(x + w * t, -h * abs(sin(t * PI.toFloat()))) // hump (n, m)
            1 -> Pt(x + w * t + w * 0.3f * sin(t * tau), -h * sin(t * PI.toFloat())) // tall loop (l, h)
            2 -> Pt(x + w * t + w * 0.35f * sin(t * tau), -h * 0.5f * (1 - cos(t * tau))) // small loop (e)
            3 -> Pt(x + w * t, -h * abs(1 - 2 * t)) // vee (v, w)
            else -> Pt(x + w * 0.5f - w * 0.5f * cos(t * tau), -h * 0.5f + h * 0.5f * sin(t * tau)) // o
          }
        pts += p
      }
      x += w * r.f(1f, 1.4f)
    }
    return turned(pts, r, r.f(-0.3f, 0.3f))
  }

  /** Cursive runs of one letter: mmm, nnn, www, lll, eee (the closest writing gets to a scribble). */
  private fun run(r: Random): List<Pt> {
    val xh = r.f(10f, 30f)
    val n = r.nextInt(3, 7)
    val kind = r.nextInt(4)
    val m = n * 30
    val w = xh * r.f(0.6f, 1.1f)
    val pts = (0..m).map { i ->
      val t = i / 30f
      when (kind) {
        0 -> Pt(w * t, -xh * abs(sin(t * PI.toFloat()))) // m/n humps
        1 -> Pt(w * t, -xh * abs(1 - 2 * (t - t.toInt()))) // w vees
        2 -> Pt(w * 0.6f * t + w * 0.25f * sin(t * tau), -xh * 2f * sin(t * PI.toFloat()).let { abs(it) }) // l loops
        else -> Pt(w * t + w * 0.35f * sin(t * tau), -xh * 0.5f * (1 - cos(t * tau))) // e loops
      }
    }
    return turned(pts, r, r.f(-0.3f, 0.3f))
  }

  /** A spring drawn in an engineering diagram: loops spaced along a line. */
  private fun spring(r: Random): List<Pt> {
    val n = r.nextInt(4, 9)
    val h = r.f(15f, 40f)
    val pitch = h * r.f(0.4f, 0.9f)
    val m = n * 40
    val pts = (0..m).map { i -> val t = i / m.toFloat() * n * tau; Pt(pitch * n * i / m + pitch * 0.45f * sin(t), h / 2 * cos(t)) }
    return turned(pts, r, r.f(0f, tau))
  }

  /** A resistor zigzag: even, wide-spaced legs. */
  private fun resistor(r: Random): List<Pt> {
    val legs = r.nextInt(5, 9)
    val h = r.f(10f, 30f)
    val step = h * r.f(0.45f, 0.9f)
    val corners = (0..legs).map { i -> Pt(i * step, if (i % 2 == 0) -h / 2 else h / 2) }
    return poly(corners, r, r.f(0f, tau))
  }

  /** Circling something 1 to 3 times. */
  private fun circles(r: Random): List<Pt> {
    val turns = r.f(0.9f, 3f)
    val rx = r.f(20f, 80f)
    val ry = rx * r.f(0.4f, 1f)
    val m = (turns * 60).toInt()
    val pts = (0..m).map { i -> val t = i / 60f * tau; Pt(rx * cos(t) * (1 + 0.05f * i / 60f), ry * sin(t)) }
    return turned(pts, r, r.f(0f, tau))
  }

  /** A star drawn in one stroke. */
  private fun star(r: Random): List<Pt> {
    val rad = r.f(15f, 50f)
    val corners = (0..5).map { i -> val a = i * 4 * PI.toFloat() / 5; Pt(rad * cos(a), rad * sin(a)) }
    return poly(corners, r, r.f(0f, tau))
  }

  /** A wavy underline. */
  private fun wave(r: Random): List<Pt> {
    val len = r.f(60f, 300f)
    val waves = r.nextInt(3, 10)
    val amp = len / waves * r.f(0.1f, 0.5f)
    return turned((0..200).map { i -> val t = i / 200f; Pt(len * t, amp * sin(t * waves * tau)) }, r, r.f(-0.2f, 0.2f))
  }

  /** A spiral. */
  private fun spiral(r: Random): List<Pt> {
    val turns = r.f(1.5f, 4f)
    val m = (turns * 60).toInt()
    return turned((0..m).map { i -> val t = i / 60f * tau; val rr = 3f + i * r.f(0.2f, 0.5f); Pt(rr * cos(t), rr * sin(t)) }, r, 0f)
  }

  private fun rate(gen: (Random) -> List<Pt>): Float {
    var hit = 0
    val n = 300
    for (seed in 0 until n) if (ScratchOut.area(gen(Random(seed * 31 + 7)), 1f) != null) hit++
    return hit * 100f / n
  }

  @Test fun rates() {
    val scribbles = listOf(Triple("scrub", ::scrub, 95f), Triple("zigzag across", ::zigzagAcross, 65f), Triple("loops", ::loops, 95f), Triple("tangle", ::tangle, 95f), Triple("in place", ::inPlace, 95f))
    val writing = listOf(Triple("word", ::word, 10f), Triple("letter run", ::run, 30f), Triple("spring", ::spring, 25f), Triple("resistor", ::resistor, 2f), Triple("star", ::star, 2f), Triple("wave", ::wave, 2f), Triple("spiral", ::spiral, 3f))
    val report = StringBuilder()
    var ok = true
    for ((name, g, least) in scribbles) rate(g).let { report.appendLine("scribble %-14s %5.1f%% (at least %.0f)".format(name, it, least)); if (it < least) ok = false }
    for ((name, g, most) in writing) rate(g).let { report.appendLine("writing  %-14s %5.1f%% (at most %.0f)".format(name, it, most)); if (it > most) ok = false }
    println(report)
    org.junit.Assert.assertTrue(report.toString(), ok)
  }
}
