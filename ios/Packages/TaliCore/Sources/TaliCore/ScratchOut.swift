import Foundation

/// Scribble to delete: a pen stroke scrubbed back and forth over writing takes what it covers off
/// the page. Off unless she turns it on in the eraser's settings. The same rules as the Android
/// app's (`geometry/ScratchOut.kt`, 1.0.29), so both tell a scribble alike.
///
/// A scribble is told from writing by its legs: along some direction it turns back on itself at
/// least [minTurns] times, its legs run across at least half of it on average, and they lie close
/// together (a zigzag line drawn across the page moves on too far between legs). Every direction is
/// tried, every 15 degrees, since a quick scribble is often slanted loops rather than a level scrub.
///
/// What it takes is what its pen actually ran over: a thing must lie mostly under it, or be crossed
/// by it and not much bigger than it, and the scribble's path must pass over most of what lies under
/// it (so circling a word, however many times, leaves the word alone).
public enum ScratchOut {
  /// Five legs or more: over, back, over, back, over.
  public static let minTurns = 4
  /// Smallest size along its legs, in screen points.
  public static let minLegScreen: Float = 16
  /// A turn counts once the pen has come back this much of the scribble's length along its legs.
  static let turnBack: Float = 0.3
  /// Each leg on average runs across at least this much of the scribble.
  static let legReach: Float = 0.5
  /// How far it may move on between legs, against a leg's length.
  static let maxDrift: Float = 0.25
  /// Directions tried for the legs, spread over half a turn (every 15 degrees).
  static let directions = 12
  /// How much of a thing has to lie under the scribble for it to go.
  public static let coverNeeded: Float = 0.6
  /// How much of a thing the scribble has to cross for it to go, when it isn't mostly under it: a
  /// scribble across the middle of a word leaves the tops and tails of tall letters (l, d, ן, ל)
  /// sticking out.
  public static let crossNeeded: Float = 0.15
  /// How far past the scribble's box such a thing may reach, against the scribble's larger side
  /// (at 0.5 a ן whose hook dipped just past it stayed behind).
  public static let reachPast: Float = 1.0
  /// How much of what lies under the scribble its path must have passed over.
  public static let passedNeeded: Float = 0.6
  /// "Passed over" is within this share of the gap between its legs of its path...
  static let passGap: Float = 0.75
  /// ...and never less than this, in screen points.
  static let passMinScreen: Float = 6

  /// A scribble: the area it scrubbed over (a convex outline), its path, and how near its path
  /// counts as passed over.
  public struct Scribble: Sendable {
    public var hull: [Pt]
    public var path: [Pt]
    public var reach: Float
  }

  /// [pts] as a scribble, or nil when it isn't one. [zoom] is screen points per unit.
  public static func scribble(_ pts: [Pt], zoom: Float) -> Scribble? {
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
    // Starting from its main direction, the legs' way is the one where they lie closest together.
    let angle = 0.5 * atan2f(2 * sxy, sxx - syy)
    var gap = Float.greatestFiniteMagnitude
    for k in 0..<directions {
      let a = angle + Float(k) * .pi / Float(directions)
      let u = Pt(cosf(a), sinf(a))
      guard let l = legs(pts, along: u, across: Pt(-u.y, u.x)), l.spans.count - 1 >= minTurns, l.length * zoom >= minLegScreen else { continue }
      let meanSpan = l.spans.reduce(0, +) / Float(l.spans.count)
      let drift = l.width / Float(l.spans.count)
      if meanSpan >= legReach * l.length && drift <= maxDrift * meanSpan { gap = min(gap, drift) }
    }
    guard gap < .greatestFiniteMagnitude else { return nil }
    let hull = convexHull(pts)
    return hull.count >= 3 ? Scribble(hull: hull, path: pts, reach: max(passGap * gap, passMinScreen / zoom)) : nil
  }

  /// The area [pts] scrubbed over (a convex outline, in the same units), or nil when it isn't a scribble.
  public static func area(_ pts: [Pt], zoom: Float) -> [Pt]? { scribble(pts, zoom: zoom)?.hull }

  struct Legs {
    var spans: [Float]
    /// Its size along the legs, and across them.
    var length: Float
    var width: Float
  }

  /// The legs of [pts] going back and forth along [u].
  static func legs(_ pts: [Pt], along u: Pt, across v: Pt) -> Legs? {
    let proj = pts.map { $0.dot(u) }
    let side = pts.map { $0.dot(v) }
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

  /// Whether the scribble [s] takes a thing drawn as [lines]: mostly under it ([coverNeeded]), or
  /// crossed by it ([crossNeeded]) and no bigger than the scribble plus its own size ([reachPast])
  /// all round; and its path passed over most of the part under it ([passedNeeded]). So a word's
  /// tall letters go when the scribble runs across their middles, while a long line, a box it only
  /// crosses one side of, or a word it only circled stays.
  public static func takes(_ lines: [[Pt]], _ s: Scribble) -> Bool {
    let area = s.hull
    guard let box = Rect.around(area) else { return false }
    let near = PathIndex(s.path, reach: s.reach)
    var inside = 0, passed = 0, all = 0
    var l = Float.greatestFiniteMagnitude, t = Float.greatestFiniteMagnitude
    var r = -Float.greatestFiniteMagnitude, b = -Float.greatestFiniteMagnitude
    eachSample(lines, step: max(max(box.width, box.height) / 40, 0.5)) { p in
      all += 1
      let ranOver = near.has(p)
      // Under it: inside its outline, or right by its path (a scrub in place leaves a thin outline).
      if ranOver || box.contains(p) && Geo.pointInPolygon(p, area) {
        inside += 1
        if ranOver { passed += 1 }
      }
      l = min(l, p.x)
      t = min(t, p.y)
      r = max(r, p.x)
      b = max(b, p.y)
    }
    guard all > 0, inside > 0, Float(passed) >= passedNeeded * Float(inside) else { return false }
    if Float(inside) >= coverNeeded * Float(all) { return true }
    let reach = box.outset(reachPast * max(box.width, box.height))
    return Float(inside) >= crossNeeded * Float(all) && l >= reach.left && t >= reach.top && r <= reach.right && b <= reach.bottom
  }

  /// Whether the scribble's [area] covers enough of a thing drawn as [lines] for it to go (the first rule).
  public static func covers(_ lines: [[Pt]], _ area: [Pt]) -> Bool {
    guard let box = Rect.around(area) else { return false }
    var inside = 0, all = 0
    eachSample(lines, step: max(max(box.width, box.height) / 40, 0.5)) { p in
      all += 1
      if box.contains(p) && Geo.pointInPolygon(p, area) { inside += 1 }
    }
    return all > 0 && Float(inside) >= coverNeeded * Float(all)
  }

  /// Finds whether a point lies within [reach] of [path] (filled in so no gap is wider than half of it).
  struct PathIndex {
    struct Cell: Hashable {
      var x: Int
      var y: Int
    }

    let reach: Float
    private var cells: [Cell: [Pt]] = [:]

    init(_ path: [Pt], reach: Float) {
      self.reach = reach
      var cells: [Cell: [Pt]] = [:]
      ScratchOut.eachSample([path], step: reach / 2) { p in cells[Cell(x: Self.cell(p.x, reach), y: Self.cell(p.y, reach)), default: []].append(p) }
      self.cells = cells
    }

    private static func cell(_ v: Float, _ reach: Float) -> Int { Int((v / reach).rounded(.down)) }

    func has(_ p: Pt) -> Bool {
      let cx = Self.cell(p.x, reach), cy = Self.cell(p.y, reach)
      for dx in -1...1 {
        for dy in -1...1 {
          if let pts = cells[Cell(x: cx + dx, y: cy + dy)], pts.contains(where: { $0.dist(p) <= reach }) { return true }
        }
      }
      return false
    }
  }

  /// Points along [lines] at most [step] apart.
  static func eachSample(_ lines: [[Pt]], step: Float, _ f: (Pt) -> Void) {
    for line in lines {
      guard let first = line.first else { continue }
      f(first)
      for (a, b) in zip(line, line.dropFirst()) {
        let parts = max(Int((a.dist(b) / step).rounded(.up)), 1)
        for k in 1...parts { f(a + (b - a) * (Float(k) / Float(parts))) }
      }
    }
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
