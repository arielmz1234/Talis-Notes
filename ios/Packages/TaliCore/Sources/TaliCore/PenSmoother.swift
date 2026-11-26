import Foundation

/// Smooths a stroke's path the way Android's `PenSmoother` does: a One Euro filter (Casiez et al.)
/// that smooths a lot while the pen moves slowly, where the hand's and the screen's jitter shows as
/// little kinks, and hardly at all when it moves fast. It works in screen units, so it feels the
/// same at any zoom. Pressure is eased over `pressureTau` as on Android (a jump in the reading
/// became a step in a pressure pen's edge); tilt and timing are kept.
public enum PenSmoother {
  /// Android's tuning, in its screen pixels (a Galaxy Tab S6 Lite: about 224 per inch).
  public static let minCutoff: Float = 2.5
  public static let betaPerPixel: Float = 0.04
  public static let speedCutoff: Float = 1.2
  /// An iPad point is about 1.7 of those pixels: the same speed reads lower in points.
  public static let pixelsPerPoint: Float = 1.7
  /// How long pressure takes to follow a change (seconds, a time constant; Android's `PRESSURE_TAU_MS`).
  public static let pressureTau: Float = 0.018

  /// [points] (world units, times in seconds) smoothed for a canvas showing [zoom] points per world unit.
  public static func smooth(_ points: [InkInput], zoom: Float) -> [InkInput] {
    guard points.count > 2, zoom > 0 else { return points }
    let k = zoom * pixelsPerPoint  // world units to Android's pixels
    var out = points
    var x = points[0].x * k, y = points[0].y * k
    var vx: Float = 0, vy: Float = 0
    var pressure = points[0].pressure
    var last = points[0].t
    func alpha(_ cutoff: Float, _ dt: Float) -> Float {
      let tau = 1 / (2 * Float.pi * cutoff)
      return 1 / (1 + tau / dt)
    }
    // Samples without times that move on (some strokes carry none) are taken as Apple Pencil's 240 a second.
    let timed = points.last!.t - points[0].t > 0.001 * Float(points.count)
    for i in 1..<points.count {
      let px = points[i].x * k, py = points[i].y * k
      let dt = timed ? points[i].t - last : 1 / 240
      if dt > 0 {
        last = points[i].t
        let av = alpha(speedCutoff, dt)
        vx += av * ((px - x) / dt - vx)
        vy += av * ((py - y) / dt - vy)
        let a = alpha(minCutoff + betaPerPixel * (vx * vx + vy * vy).squareRoot(), dt)
        x += a * (px - x)
        y += a * (py - y)
        if let p = points[i].pressure, let was = pressure { pressure = was + (1 - expf(-dt / pressureTau)) * (p - was) } else { pressure = points[i].pressure }
      }
      out[i].x = x / k
      out[i].y = y / k
      out[i].pressure = pressure
    }
    // As on Android, the last sample is filtered like the rest (it used to be pulled back onto the
    // pen over the last few samples, which brought the hand's jitter back at the stroke's end).
    return out
  }
}
