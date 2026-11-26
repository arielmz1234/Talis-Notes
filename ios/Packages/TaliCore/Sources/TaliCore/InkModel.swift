import Foundation

/// How Android's ink library models handwriting before drawing it (`Brushes.WRITING_MODEL`:
/// `BrushFamily.SlidingWindowModel(20 ms, 360 Hz)`): the pen's path is averaged over a sliding
/// window of time and filled in at 360 points a second. With `PenSmoother` before it, this is why
/// Android's strokes look so even; PencilKit only joins the samples it's given. It's applied when a
/// stroke is drawn, never to what's stored, so a stroke reads back exactly as Android keeps it.
public enum InkModel {
  public static let window: Float = 0.020
  public static let rate: Float = 360
  /// Samples further apart than this (seconds, the median gap) aren't handwriting: a finger is read
  /// 60 to 120 times a second, the pencil 240; shapes are made a sample every 37 ms.
  static let sparse: Float = 0.025

  /// [points] (times in seconds) as Android's ink draws them: at every 1/[rate] s, the average of
  /// the pen's path (straight between samples) over [window] centred there, the window kept inside
  /// the stroke at its ends, so the first and last points stay put. Pressure is averaged the same
  /// way; tilt and orientation come from the nearest sample. Strokes without real times (all at
  /// one moment, or out of order) are drawn as they are, and so are strokes sampled sparsely:
  /// shapes, whose samples are made a few units apart at a slow pen (`StrokeAlong`), keep the
  /// default model on Android so their corners stay sharp.
  public static func slidingWindow(_ points: [InkInput], window: Float = window, rate: Float = rate) -> [InkInput] {
    guard points.count > 2, let first = points.first, let last = points.last else { return points }
    let start = first.t, end = last.t, span = end - start
    guard span > 0.001 * Float(points.count) || span > 0.02, zip(points, points.dropFirst()).allSatisfy({ $1.t >= $0.t }) else { return points }
    let ts = points.map(\.t)
    let gaps = zip(ts, ts.dropFirst()).map { $1 - $0 }.sorted()
    guard gaps[gaps.count / 2] < sparse else { return points }

    /// The path's position (and pressure) at time [t], straight between samples.
    func at(_ t: Float, from i: inout Int) -> (x: Float, y: Float, p: Float?) {
      while i + 1 < points.count - 1, ts[i + 1] <= t { i += 1 }
      let a = points[i], b = points[min(i + 1, points.count - 1)]
      let d = b.t - a.t
      let f = d > 0 ? min(max((t - a.t) / d, 0), 1) : 0
      let p: Float? = if let pa = a.pressure, let pb = b.pressure { pa + (pb - pa) * f } else { a.pressure ?? b.pressure }
      return (a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f, p)
    }

    /// The path averaged over [lo, hi]: the exact mean of the straight pieces between samples.
    func mean(_ lo: Float, _ hi: Float) -> (x: Float, y: Float, p: Float?) {
      var i = 0
      if hi - lo < 1e-6 { return at(lo, from: &i) }
      var sx: Float = 0, sy: Float = 0, sp: Float = 0, hasP = true
      var t0 = lo
      var j = 0
      while j + 1 < points.count, ts[j + 1] <= lo { j += 1 }
      var p0 = at(lo, from: &j)
      while t0 < hi {
        let next = j + 1 < points.count ? min(ts[j + 1], hi) : hi
        let t1 = max(next, t0)
        var k = j
        let p1 = at(t1, from: &k)
        let w = t1 - t0
        sx += (p0.x + p1.x) / 2 * w
        sy += (p0.y + p1.y) / 2 * w
        if let a = p0.p, let b = p1.p { sp += (a + b) / 2 * w } else { hasP = false }
        if t1 >= hi || j + 1 >= points.count { break }
        t0 = t1
        p0 = p1
        j += 1
      }
      let len = hi - lo
      return (sx / len, sy / len, hasP ? sp / len : nil)
    }

    let half = window / 2
    let steps = max(Int((span * rate).rounded(.up)), 1)
    var out: [InkInput] = []
    out.reserveCapacity(steps + 1)
    var near = 0
    for s in 0...steps {
      let t = s == steps ? end : start + Float(s) / rate
      // The window shrinks near the ends so it never reaches past them: the ends stay where they were.
      let reach = min(half, t - start, end - t)
      let m = mean(t - reach, t + reach)
      while near + 1 < points.count, abs(ts[near + 1] - t) <= abs(ts[near] - t) { near += 1 }
      let src = points[near]
      out.append(InkInput(x: m.x, y: m.y, t: t, pressure: m.p ?? src.pressure, tilt: src.tilt, orientation: src.orientation))
    }
    return out
  }
}
