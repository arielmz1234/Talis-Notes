package com.tali.notes.camera

/** A point in a picture, in parts of its width and height (0..1). */
data class P(val x: Float, val y: Float)

/** A crop's four corners, in parts of the picture: top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: P, val tr: P, val br: P, val bl: P) {
  val points: List<P>
    get() = listOf(tl, tr, br, bl)

  fun with(i: Int, p: P): Quad =
    when (i) {
      0 -> copy(tl = p)
      1 -> copy(tr = p)
      2 -> copy(br = p)
      else -> copy(bl = p)
    }

  /** The same corners in a picture turned a quarter clockwise [quarters] times. */
  fun turned(quarters: Int): Quad {
    var q = this
    repeat(((quarters % 4) + 4) % 4) {
      fun r(p: P) = P(1f - p.y, p.x)
      // Turned clockwise, the old bottom-left becomes the top-left.
      q = Quad(r(q.bl), r(q.tl), r(q.tr), r(q.br))
    }
    return q
  }

  /** No corner crossing over: the four make a shape that bulges outward everywhere. */
  val convex: Boolean
    get() {
      val ps = points
      var sign = 0
      for (i in 0 until 4) {
        val a = ps[i]
        val b = ps[(i + 1) % 4]
        val c = ps[(i + 2) % 4]
        val z = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        val s = if (z > 0) 1 else if (z < 0) -1 else 0
        if (s == 0) return false
        if (sign == 0) sign = s else if (s != sign) return false
      }
      return true
    }

  companion object {
    /** The whole picture, a little in from its edges. */
    fun whole(inset: Float = 0f) = Quad(P(inset, inset), P(1 - inset, inset), P(1 - inset, 1 - inset), P(inset, 1 - inset))
  }
}
