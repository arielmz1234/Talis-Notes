import Foundation

/// Auto arrow (Android 1.0.33, `geometry/AutoArrow.kt`): an arrow drawn by hand becomes a clean
/// arrow shape as the pen lifts, with no hold. One stroke (the line, then its head without lifting)
/// or two (a straight line, then its head as the very next stroke). Everything is in page units (an
/// A4 page is 1322 wide). A port of the Kotlin, same thresholds and order of tests; keep the two in step.
public enum AutoArrow {
  /// A point closer than this to the one before it is dropped first.
  static let minStep: Float = 0.5
  /// The line must be at least this long.
  public static let minShaft: Float = 40
  /// The head counts when the pen lifts from it less than this long (seconds) after it lifted from
  /// the line (Android's `AUTO_ARROW_GAP_MS`, both times taken as the pen lifts).
  public static let wait: Double = 3

  private static let deg = Float.pi / 180

  /// An arrow found in what was drawn: from [start] to [tip] (on the line's own direction), and
  /// how long its barbs were drawn on average ([drawn]).
  public struct Found: Equatable, Sendable {
    public var start: Pt
    public var tip: Pt
    public var drawn: Float

    public init(start: Pt, tip: Pt, drawn: Float) {
      self.start = start
      self.tip = tip
      self.drawn = drawn
    }
  }

  /// Without points closer than `minStep` to the one before (Android's `dedupe`).
  static func thinned(_ raw: [Pt]) -> [Pt] {
    var out: [Pt] = []
    out.reserveCapacity(raw.count)
    for p in raw where out.last.map({ $0.dist(p) > minStep }) ?? true { out.append(p) }
    return out
  }

  /// One stroke: the line, then its head without lifting.
  public static func oneStroke(_ raw: [Pt]) -> Found? {
    let pts = thinned(raw)
    guard pts.count >= 4, let box = Rect.around(pts) else { return nil }
    let diag = Pt(box.width, box.height).length()
    let v = Geo.simplify(pts, max(0.04 * diag, 3))
    guard v.count >= 4 else { return nil }
    let v0 = v[0], v1 = v[1]
    let shaft = v0.dist(v1)
    guard shaft >= minShaft, let at = pts.firstIndex(of: v1), at >= 1 else { return nil }
    let slack = max(0.06 * shaft, 3)
    guard !pts[...at].contains(where: { Geo.distToSegment($0, v0, v1) > slack }) else { return nil }
    return headAt(v0, v1, Array(pts[at...]))
  }

  /// The ends of a stroke that's a straight line: at least `minShaft` between them, every point
  /// close to the line through them, and no longer on the way than 1.25 times that.
  public static func straight(_ raw: [Pt]) -> (a: Pt, b: Pt)? {
    let pts = thinned(raw)
    guard let a = pts.first, let b = pts.last else { return nil }
    let length = a.dist(b)
    guard length >= minShaft else { return nil }
    let worst = pts.map { Geo.distToLine($0, a, b) }.max() ?? 0
    guard worst < max(0.06 * length, 3), Geo.pathLength(pts) < 1.25 * length else { return nil }
    return (a, b)
  }

  /// Two strokes: [head] drawn on the straight line [a]–[b], at its end or, failing that, at its
  /// start (the arrow then points back that way).
  public static func twoStrokes(a: Pt, b: Pt, head raw: [Pt]) -> Found? {
    let head = thinned(raw)
    return headAt(a, b, head) ?? headAt(b, a, head)
  }

  /// Whether [head] is an arrowhead on the end of the line from [start] to [end].
  static func headAt(_ start: Pt, _ end: Pt, _ head: [Pt]) -> Found? {
    let shaft = end.dist(start)
    guard shaft >= minShaft, head.count >= 3 else { return nil }
    let u = (end - start) / shaft
    let n = Pt(-u.y, u.x)
    func along(_ p: Pt) -> Float { (p - end).dot(u) }
    func across(_ p: Pt) -> Float { (p - end).dot(n) }
    guard let tip = head.max(by: { along($0) < along($1) }), let left = head.max(by: { across($0) < across($1) }),
      let right = head.min(by: { across($0) < across($1) }), let box = Rect.around(head)
    else { return nil }
    let width = across(left) - across(right)
    guard width >= max(10, 0.05 * shaft), max(box.width, box.height) <= 0.75 * shaft else { return nil }
    guard across(left) >= 0.2 * width, across(right) <= -0.2 * width, abs(across(tip)) <= 0.3 * width else { return nil }
    // Each side opens back from the tip, neither along the line nor square to it.
    for side in [left, right] {
      let barb = side - tip
      guard barb.length() > 1e-3 else { return nil }
      let angle = acosf(min(max((u * -1).dot(barb.normalized()), -1), 1))
      guard angle >= 10 * deg, angle <= 80 * deg else { return nil }
    }
    let base = (left + right) / 2
    guard Geo.distToSegment(end, base, tip) <= max(0.3 * width, 6) else { return nil }
    let near = max(0.15 * width, 3)
    let sides = [(tip, left), (tip, right), (left, right), (end, tip)]
    guard head.allSatisfy({ p in sides.contains { Geo.distToSegment(p, $0.0, $0.1) < near } }) else { return nil }
    return Found(start: start, tip: end + u * along(tip), drawn: (left.dist(tip) + right.dist(tip)) / 2)
  }

  /// The head length kept for an arrow whose barbs were drawn [drawn] long: the last auto arrow's
  /// exactly when it's within a fifth of that (so arrows drawn alike come out alike), otherwise
  /// rounded down to a ten, at least 10.
  public static func headSize(drawn: Float, last: Float?) -> Float {
    if let last, last > 0, abs(drawn - last) <= 0.2 * last { return last }
    return max(10, (drawn / 10).rounded(.down) * 10)
  }

  /// The arrow shape for [found]: the usual line snap (45° steps within 3°, keeping its start and
  /// length), a head on the tip end only, and the head length to remember for the next one.
  public static func shape(_ found: Found, last: Float?) -> (geometry: ShapeGeometry, head: Float) {
    let head = headSize(drawn: found.drawn, last: last)
    var end = found.tip
    if case let .line(_, b, _, _, _, _) = ShapeRecognizer.snappedLine(found.start, found.tip) { end = b }
    return (.line(a: found.start, b: end, arrowStart: false, arrowEnd: true, ticks: false, head: head), head)
  }
}
