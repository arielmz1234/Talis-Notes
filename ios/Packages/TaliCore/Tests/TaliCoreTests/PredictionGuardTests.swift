import Foundation
import Testing

@testable import TaliCore

/// The predicted tail only carries on the pen's way, not too far, and never as the pen lifts
/// (Android's `PredictionGuardTest`, plus the lift).
struct PredictionGuardTests {
  private func path(_ pts: [(Float, Float)], pressure: [Float]? = nil) -> [InkInput] {
    pts.enumerated().map { i, p in InkInput(x: p.0, y: p.1, t: Float(i) / 240, pressure: pressure?[i] ?? 0.5) }
  }

  private func guess(_ x: Float, _ y: Float, after real: [InkInput]) -> [InkInput] {
    [InkInput(x: x, y: y, t: real.last!.t + 0.01, pressure: 0.5)]
  }

  private let along = [(Float(0), Float(0)), (5, 0), (10, 0), (15, 0), (20, 0)]

  @Test func aGuessAlongTheStrokeIsKept() {
    let real = path(along)
    let out = PredictionGuard.tame(guess(26, 1, after: real), after: real, raw: real, zoom: 1, maxReach: 30)
    #expect(out.count == 1 && out[0].x == 26)
  }

  @Test func aGuessPastATurnIsDropped() {
    // Going down, the guess shoots back up (the top of a loop), or sharply aside.
    let real = path([(0, 0), (0, 5), (0, 10), (0, 15), (0, 20)])
    #expect(PredictionGuard.tame(guess(0, 8, after: real), after: real, raw: real, zoom: 1, maxReach: 30).isEmpty)
    #expect(PredictionGuard.tame(guess(14, 22, after: real), after: real, raw: real, zoom: 1, maxReach: 30).isEmpty)
  }

  @Test func aGuessTooFarAheadIsShortened() {
    // 20 points travelled: the guess may reach 24 past the pen, along its own direction.
    let real = path(along)
    let out = PredictionGuard.tame(guess(80, 0, after: real), after: real, raw: real, zoom: 1, maxReach: 30)
    #expect(out.count == 1 && abs(out[0].x - 44) < 0.01 && abs(out[0].y) < 0.01)
    // And never past 2.5 mm on screen, measured on screen: zoomed in 2×, 13 points are 6.5 units.
    let near = PredictionGuard.tame(guess(80, 0, after: real), after: real, raw: real, zoom: 2)
    #expect(near.count == 1 && abs(near[0].x - 26.5) < 0.01)
  }

  @Test func standingStillDrawsNoGuess() {
    let real = path([(10, 10), (10.2, 10), (10.4, 10.1), (10.5, 10), (10.6, 10)])
    #expect(PredictionGuard.tame(guess(14, 10, after: real), after: real, raw: real, zoom: 1).isEmpty)
  }

  @Test func liftingThePenDrawsNoGuess() {
    // Moving fast to the right while the pressure falls away: the pen is leaving the screen.
    let real = path(along, pressure: [0.6, 0.6, 0.55, 0.4, 0.25])
    #expect(PredictionGuard.lifting(real))
    #expect(PredictionGuard.tame(guess(26, 0, after: real), after: real, raw: real, zoom: 1).isEmpty)
    // Pressing evenly, or a finger (always the same), it isn't.
    #expect(!PredictionGuard.lifting(path(along, pressure: [0.5, 0.52, 0.49, 0.51, 0.5])))
    #expect(!PredictionGuard.lifting(path(along)))
    // Lighter but pressing back down: not lifting.
    #expect(!PredictionGuard.lifting(path(along, pressure: [0.6, 0.6, 0.3, 0.25, 0.35])))
  }

  @Test func guessesNotAheadOfThePenAreLeftOut() {
    let real = path(along)
    let stale = [InkInput(x: 22, y: 0, t: real.last!.t, pressure: 0.5)]
    #expect(PredictionGuard.tame(stale, after: real, raw: real, zoom: 1).isEmpty)
  }
}
