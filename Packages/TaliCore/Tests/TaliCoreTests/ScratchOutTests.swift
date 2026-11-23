import Foundation
import Testing
@testable import TaliCore

/// Scribble to delete: what counts as a scribble, and what it covers.
struct ScratchOutTests {
  /// Straight lines through [corners], a point every [step] units, with a seeded wobble.
  private func path(_ corners: [Pt], step: Float = 3, jitter: Float = 1.5) -> [Pt] {
    var state: UInt64 = 7
    func wobble() -> Float {
      state = state &* 6_364_136_223_846_793_005 &+ 1_442_695_040_888_963_407
      return (Float(state >> 40) / Float(1 << 24) - 0.5) * jitter
    }
    var out: [Pt] = []
    for (a, b) in zip(corners, corners.dropFirst()) {
      let n = max(Int(a.dist(b) / step), 2)
      for k in 0..<n {
        let p = a + (b - a) * (Float(k) / Float(n))
        out.append(Pt(p.x + wobble(), p.y + wobble()))
      }
    }
    if let last = corners.last { out.append(last) }
    return out
  }

  private func zigzag(legs: Int, _ corner: (Int) -> Pt) -> [Pt] { path((0...legs).map(corner)) }

  @Test func scrubbingAcrossIsAScribble() {
    #expect(ScratchOut.area(zigzag(legs: 7) { Pt($0 % 2 == 0 ? 0 : 100, Float($0) * 3) }, zoom: 1) != nil)
  }

  @Test func scrubbingInPlaceIsAScribble() {
    #expect(ScratchOut.area(path((0...7).map { Pt($0 % 2 == 0 ? 0 : 80, 0) }, jitter: 2), zoom: 1) != nil)
  }

  @Test func aTightZigzagAlongAWordIsAScribble() {
    #expect(ScratchOut.area(zigzag(legs: 20) { Pt(Float($0) * 6, $0 % 2 == 0 ? 0 : 30) }, zoom: 1) != nil)
  }

  @Test func aLooseZigzagStaysInk() {
    #expect(ScratchOut.area(zigzag(legs: 10) { Pt(Float($0) * 12, $0 % 2 == 0 ? 0 : 30) }, zoom: 1) == nil)
    #expect(ScratchOut.area(zigzag(legs: 6) { Pt(Float($0) * 40, $0 % 2 == 0 ? 0 : 80) }, zoom: 1) == nil)
  }

  @Test func writingStaysInk() {
    let cursive = (0..<200).map { i -> Pt in
      let x = Float(i) * 0.5
      return Pt(x, -25 * abs(sinf(x * .pi / 20)))
    }
    #expect(ScratchOut.area(cursive, zoom: 1) == nil)
    let dense = (0..<200).map { i -> Pt in
      let x = Float(i) * 0.3
      return Pt(x, -20 * abs(sinf(x * .pi / 10)))
    }
    #expect(ScratchOut.area(dense, zoom: 1) == nil)
  }

  @Test func linesLoopsAndOneReturnStayInk() {
    #expect(ScratchOut.area(path([Pt(0, 0), Pt(200, 10)]), zoom: 1) == nil)
    #expect(ScratchOut.area(path([Pt(0, 0), Pt(100, 0), Pt(0, 4)]), zoom: 1) == nil)
    #expect(ScratchOut.area((0..<126).map { Pt(50 * cosf(Float($0) / 20), 50 * sinf(Float($0) / 20)) }, zoom: 1) == nil)
    let spiral = (0..<200).map { t -> Pt in
      let r = 5 + Float(t) / 3
      return Pt(r * cosf(Float(t) / 8), r * sinf(Float(t) / 8))
    }
    #expect(ScratchOut.area(spiral, zoom: 1) == nil)
  }

  @Test func tooSmallOnScreenStaysInk() {
    let tiny = path((0...7).map { Pt($0 % 2 == 0 ? 0 : 10, Float($0)) }, step: 1, jitter: 0.3)
    #expect(ScratchOut.area(tiny, zoom: 1) == nil)
    // Zoomed in, the same scribble is big enough on screen.
    #expect(ScratchOut.area(tiny, zoom: 3) != nil)
  }

  @Test func itTakesWhatItMostlyCovers() throws {
    let area = try #require(ScratchOut.area(zigzag(legs: 7) { Pt($0 % 2 == 0 ? 0 : 100, Float($0) * 3) }, zoom: 1))
    #expect(ScratchOut.covers([[Pt(10, 5), Pt(90, 15)]], area), "a word under it")
    #expect(ScratchOut.covers([[Pt(50, 10)]], area), "a dot under it")
    #expect(!ScratchOut.covers([[Pt(80, 10), Pt(300, 10)]], area), "a long line it only crosses the start of")
    #expect(!ScratchOut.covers([[Pt(0, 60), Pt(100, 60)]], area), "a line below it")
    // A shape's lines count together: a rectangle with one side under the scribble stays.
    let box = [Pt(10, 10), Pt(90, 10), Pt(90, 200), Pt(10, 200), Pt(10, 10)]
    #expect(!ScratchOut.covers([box], area))
  }
}
