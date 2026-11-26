import Foundation
import Testing

@testable import TaliCore

/// Android's ink model for handwriting (`SlidingWindowModel(20 ms, 360 Hz)`).
struct InkModelTests {
  private func wobble(_ pts: [InkInput]) -> Float {
    guard pts.count > 2 else { return 0 }
    var turn: Float = 0
    for i in 1..<(pts.count - 1) {
      let a = Pt(pts[i].x - pts[i - 1].x, pts[i].y - pts[i - 1].y), b = Pt(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
      if a.length() > 1e-4, b.length() > 1e-4 { turn += abs(atan2f(a.cross(b), a.dot(b))) }
    }
    return turn / Float(pts.count - 2)
  }

  @Test func itFillsInAt360ASecondAndKeepsTheEnds() {
    let raw = (0...60).map { i in InkInput(x: Float(i) * 2, y: 0, t: Float(i) / 240, pressure: 0.5) }
    let out = InkModel.slidingWindow(raw)
    #expect(out.count == Int((0.25 * 360).rounded(.up)) + 1)
    #expect(out.first?.x == raw.first?.x && out.last?.x == raw.last?.x)
    #expect(out.allSatisfy { abs($0.y) < 1e-4 })
  }

  @Test func jitterIsAveragedOut() {
    // A slow line with a sample-to-sample shake: the drawn path turns far less.
    let raw = (0..<120).map { i in InkInput(x: Float(i) * 0.5, y: i % 2 == 0 ? 0.4 : -0.4, t: Float(i) / 240) }
    let out = InkModel.slidingWindow(raw)
    #expect(wobble(out) < wobble(raw) * 0.2)
    #expect(out.dropFirst(5).dropLast(5).allSatisfy { abs($0.y) < 0.15 })
  }

  @Test func aCurveKeepsItsShape() {
    // A circle written at an ordinary speed: averaged over 20 ms it stays round (radius within 3%).
    let raw = (0...240).map { i -> InkInput in
      let a = Float(i) / 240 * 2 * .pi
      return InkInput(x: 100 * cosf(a), y: 100 * sinf(a), t: Float(i) / 240)
    }
    let out = InkModel.slidingWindow(raw)
    #expect(out.allSatisfy { abs(hypotf($0.x, $0.y) - 100) < 3 })
  }

  @Test func strokesWithoutTimesAreLeftAlone() {
    let raw = (0..<30).map { i in InkInput(x: Float(i), y: Float(i % 3), t: 0) }
    #expect(InkModel.slidingWindow(raw) == raw)
    // A shape's samples (StrokeAlong): its corners stay where they were.
    let box = StrokeAlong.inputs([Pt(0, 0), Pt(100, 0), Pt(100, 100), Pt(0, 100), Pt(0, 0)]).points
    #expect(InkModel.slidingWindow(box) == box)
    let dot = [InkInput(x: 1, y: 1, t: 0), InkInput(x: 1.2, y: 1, t: 0.01)]
    #expect(InkModel.slidingWindow(dot) == dot)
  }
}
