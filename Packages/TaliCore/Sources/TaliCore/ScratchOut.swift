import Foundation

/// Scribble to delete: a pen stroke scrubbed back and forth over writing takes what it covers off
/// the page. Off unless she turns it on in the eraser's settings.
///
/// A scribble is told from writing by its legs: it turns back on itself at least [minTurns] times,
/// each leg runs across most of the scribble, and the legs lie close together (a zigzag line drawn
/// across the page moves on too far between legs, and handwriting's legs are short and uneven).
public enum ScratchOut {
  /// Five legs or more: over, back, over, back, over.
  public static let minTurns = 4
  /// Smallest size along its legs, in screen points.
  public static let minLegScreen: Float = 16
  /// A turn counts once the pen has come back this much of the scribble's length along its legs.
  static let turnBack: Float = 0.3
  /// Each leg on average runs across at least this much of the scribble.
  static let legReach: Float = 0.6
  /// How far it may move on between legs, against a leg's length.
  static let maxDrift: Float = 0.25
  /// How much of a thing has to lie under the scribble for it to go.
  public static let coverNeeded: Float = 0.6

  /// The area [pts] scrubbed over (a convex outline, in the same units), or nil when it isn't a scribble.
  public static func area(_ pts: [Pt], zoom: Float) -> [Pt]? {
    guard pts.count >= 12 else { return nil }
    let n = Float(pts.count)
    let mean = pts.reduce(Pt.zero, +) / n
    var sxx: Float = 0, syy: Float = 0, sxy: Float = 0
    for p in pts {
      let d = p - mean
      sxx += d.x * d.x
      syy += d.y * d.y
      sxy += d.x * d.y
    }
    // Its main direction and the one across it: the legs run along one of them. Both are tried, as
    // the hand's wobble across a scribble scrubbed in place can look like many small legs.
    let angle = 0.5 * atan2f(2 * sxy, sxx - syy)
    let u = Pt(cosf(angle), sinf(angle))
    let v = Pt(-u.y, u.x)
    let scrubbed = [legs(pts, along: u, across: v), legs(pts, along: v, across: u)].contains { found in
      guard let legs = found, legs.spans.count - 1 >= minTurns, legs.length * zoom >= minLegScreen else { return false }
      let meanSpan = legs.spans.reduce(0, +) / Float(legs.spans.count)
      return meanSpan >= legReach * legs.length && legs.width / Float(legs.spans.count) <= maxDrift * meanSpan
    }
    guard scrubbed else { return nil }
    let hull = convexHull(pts)
    return hull.count >= 3 ? hull : nil
  }

  struct Legs {
    var spans: [Float]
    /// Its size along the legs, and across them.
    var length: Float
    var width: Float
  }

  /// The legs of [pts] going back and forth along [u].
  static func legs(_ pts: [Pt], along u: Pt, across v: Pt) -> Legs? {
    let proj = pts.map { $0.x * u.x + $0.y * u.y }
    let side = pts.map { $0.x * v.x + $0.y * v.y }
    guard let lo = proj.min(), let hi = proj.max(), let sLo = side.min(), let sHi = side.max(), hi > lo else { return nil }
    let length = hi - lo
    let back = turnBack * length
    var spans: [Float] = []
    var dir: Float = 0
    var start = proj[0], peak = proj[0]
    for x in proj.dropFirst() {
      if dir == 0 {
        // Not yet under way: it sets off whichever way it first goes far enough.
        if x - start >= back {
          dir = 1
          peak = x
        } else if start - x >= back {
          dir = -1
          peak = x
        }
        continue
      }
      if (x - peak) * dir > 0 {
        peak = x
      } else if (peak - x) * dir >= back {
        spans.append(abs(peak - start))
        start = peak
        peak = x
        dir = -dir
      }
    }
    if dir != 0 { spans.append(abs(peak - start)) }
    return spans.isEmpty ? nil : Legs(spans: spans, length: length, width: sHi - sLo)
  }

  /// Whether the scribble's [area] covers enough of a thing drawn as [lines] for it to go.
  public static func covers(_ lines: [[Pt]], _ area: [Pt]) -> Bool {
    guard let box = Rect.around(area) else { return false }
    let step = max(max(box.width, box.height) / 40, 0.5)
    var inside = 0, all = 0
    func count(_ p: Pt) {
      all += 1
      if box.contains(p) && Geo.pointInPolygon(p, area) { inside += 1 }
    }
    for line in lines {
      guard let first = line.first else { continue }
      count(first)
      for (a, b) in zip(line, line.dropFirst()) {
        let parts = max(Int((a.dist(b) / step).rounded(.up)), 1)
        for k in 1...parts { count(a + (b - a) * (Float(k) / Float(parts))) }
      }
    }
    return all > 0 && Float(inside) >= coverNeeded * Float(all)
  }

  /// The convex hull of [pts] (Andrew's monotone chain), counter-clockwise.
  static func convexHull(_ pts: [Pt]) -> [Pt] {
    let sorted = Array(Set(pts)).sorted { $0.x != $1.x ? $0.x < $1.x : $0.y < $1.y }
    if sorted.count < 3 { return sorted }
    func cross(_ o: Pt, _ a: Pt, _ b: Pt) -> Float { (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x) }
    var lower: [Pt] = []
    for p in sorted {
      while lower.count >= 2 && cross(lower[lower.count - 2], lower[lower.count - 1], p) <= 0 { lower.removeLast() }
      lower.append(p)
    }
    var upper: [Pt] = []
    for p in sorted.reversed() {
      while upper.count >= 2 && cross(upper[upper.count - 2], upper[upper.count - 1], p) <= 0 { upper.removeLast() }
      upper.append(p)
    }
    return Array(lower.dropLast() + upper.dropLast())
  }
}
