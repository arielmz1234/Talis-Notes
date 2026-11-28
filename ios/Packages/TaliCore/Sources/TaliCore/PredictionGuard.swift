import Foundation

/// Keeps the pen's predicted samples from showing where the pen never went (Android's
/// `ink/PredictionGuard.kt`, with one more rule for the moment the pen lifts).
///
/// The live stroke is drawn a little ahead of the pen, along the system's guess of where it's about
/// to be, to hide latency. Writing fast with sharp turns, the guess keeps going the old way: a tail
/// shoots past the turn and the next frame takes it back. And lifting the pen at speed, the last
/// frame shows a tail past the stroke's real end, gone a frame later when the stroke is kept (the
/// owner's report, 28 Nov 2026). So a guess is only drawn when it carries on roughly the way the pen
/// has been going, never further than a short distance (or than the pen has just moved), never when
/// the pen is nearly still, and never while the pen is lifting (its pressure falling away).
public enum PredictionGuard {
  /// How many recent samples set the pen's direction (Android's `RECENT`).
  public static let recent = 5
  /// Less movement than this (screen points) over those samples counts as still (Android's 1.5 px).
  public static let stillPoints: Float = 1.5 / PenSmoother.pixelsPerPoint
  /// The guess may turn at most this far from the pen's direction (cos 50°).
  public static let minCos: Float = 0.64
  /// And reach at most this much of the distance just travelled.
  public static let reachOfTravel: Float = 1.2
  /// And at most this far (screen points): Android's 2.5 mm, on an iPad's 132 points an inch.
  public static let maxReachPoints: Float = 13
  /// The pen is lifting when its pressure has fallen below this share of its firmest over the last
  /// few samples and is still falling.
  public static let liftingShare: Float = 0.75
  static let pressureLookBack = 8

  /// What of [ahead] (the predicted samples, smoothed like the rest; world units) may be drawn after
  /// [real] (the smoothed samples so far) on a canvas at [zoom] points per world unit. [raw] is the
  /// pen's own samples, whose pressure says whether it's lifting. Returns [ahead] itself, a single
  /// sample cut short in the same direction, or nothing.
  public static func tame(_ ahead: [InkInput], after real: [InkInput], raw: [InkInput], zoom: Float, maxReach: Float = maxReachPoints) -> [InkInput] {
    guard zoom > 0, real.count >= 2, let last = real.last else { return [] }
    let guess = ahead.filter { $0.t > last.t }
    guard let tip = guess.last else { return [] }
    if lifting(raw) { return [] }
    let from = real[max(real.count - recent, 0)]
    // The way the pen has been heading, over the last few samples (screen points).
    let dx = (last.x - from.x) * zoom, dy = (last.y - from.y) * zoom
    let travel = (dx * dx + dy * dy).squareRoot()
    let vx = (tip.x - last.x) * zoom, vy = (tip.y - last.y) * zoom
    let reach = (vx * vx + vy * vy).squareRoot()
    // Nearly still: a guess there is only noise.
    if travel < stillPoints { return [] }
    if reach < 0.5 { return guess }
    // Turning back or sharply aside: the guess would overshoot the turn.
    if (vx * dx + vy * dy) / (reach * travel) < minCos { return [] }
    let cap = min(maxReach, travel * reachOfTravel)
    if reach <= cap { return guess }
    // Too far ahead: the same direction, cut to the cap.
    let k = cap / reach
    var cut = tip
    cut.x = last.x + (tip.x - last.x) * k
    cut.y = last.y + (tip.y - last.y) * k
    return [cut]
  }

  /// Whether the pen's pressure says it's leaving the screen: well below its firmest of the last few
  /// samples and still going down. A finger (no pressure, or always the same) never is.
  public static func lifting(_ raw: [InkInput]) -> Bool {
    let ps = raw.suffix(pressureLookBack).compactMap(\.pressure)
    guard ps.count >= 3, let now = ps.last, let peak = ps.max(), peak > 0.05 else { return false }
    return now < peak * liftingShare && now < ps[ps.count - 2]
  }
}
