import Foundation
import Testing
@testable import TaliCore

struct PenSmootherTests {
  /// A slow level line with a little jitter up and down, 240 samples a second.
  private func shaky(speed: Float) -> [InkInput] {
    (0..<240).map { i in
      let t = Float(i) / 240
      return InkInput(x: 100 + speed * t, y: 200 + (i % 2 == 0 ? 0.6 : -0.6), t: t, pressure: 0.5)
    }
  }

  private func wobble(_ pts: [InkInput]) -> Float {
    zip(pts, pts.dropFirst()).map { abs($1.y - $0.y) }.reduce(0, +) / Float(pts.count - 1)
  }

  @Test func slowJitterIsSmoothedAway() {
    let raw = shaky(speed: 60)
    let smooth = PenSmoother.smooth(raw, zoom: 1)
    #expect(wobble(Array(smooth.dropLast(8))) < wobble(raw) * 0.5)
  }

  @Test func itStartsAndEndsWhereThePenDid() {
    let raw = shaky(speed: 300)
    let smooth = PenSmoother.smooth(raw, zoom: 1.5)
    #expect(smooth.first == raw.first)
    #expect(smooth.last?.x == raw.last?.x && smooth.last?.y == raw.last?.y)
    #expect(smooth.count == raw.count)
  }

  @Test func fastWritingKeepsUp() {
    // A quick sweep: the smoothed path stays close to the pen.
    let raw = (0..<60).map { i in InkInput(x: Float(i) * 10, y: sinf(Float(i) / 6) * 40, t: Float(i) / 240) }
    let smooth = PenSmoother.smooth(raw, zoom: 1)
    let lags = zip(raw, smooth).map { hypotf($0.x - $1.x, $0.y - $1.y) }
    let average = lags.reduce(0, +) / Float(lags.count)
    // About Android's lag at this speed: a few units behind on average, never far off the line.
    #expect(average < 4)
    #expect((lags.max() ?? 0) < 20)
  }

  @Test func tinyStrokesAreLeftAlone() {
    let dot = [InkInput(x: 1, y: 1, t: 0), InkInput(x: 1.2, y: 1, t: 0.01)]
    #expect(PenSmoother.smooth(dot, zoom: 1) == dot)
  }

  @Test func strokesWithoutTimesKeepTheirShape() {
    // Every sample at time 0: still smoothed as a stroke, not pulled into a line from start to end.
    let raw = (0..<120).map { i in InkInput(x: Float(i) * 2, y: sinf(Float(i) / 12) * 40, t: 0) }
    let smooth = PenSmoother.smooth(raw, zoom: 1)
    let peak = smooth.map(\.y).max() ?? 0
    #expect(peak > 30)
  }
}
