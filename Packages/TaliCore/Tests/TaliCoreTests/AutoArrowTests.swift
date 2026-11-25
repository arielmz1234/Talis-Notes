import Foundation
import Testing

@testable import TaliCore

/// Auto arrow as Android 1.0.33 tests it: what becomes an arrow, what stays ink, and the head's size.
struct AutoArrowTests {
  /// A stroke through [corners], a point every 2 units (as a pen leaves them).
  private func stroke(_ corners: [Pt], step: Float = 2) -> [Pt] {
    var out = [corners[0]]
    for i in 1..<corners.count {
      let a = corners[i - 1], b = corners[i]
      let n = max(Int((a.dist(b) / step).rounded(.up)), 1)
      for k in 1...n { out.append(a + (b - a) * (Float(k) / Float(n))) }
    }
    return out
  }

  private func turned(_ points: [Pt], by degrees: Float) -> [Pt] { points.map { $0.rotated(degrees * .pi / 180) } }

  private let line = [Pt(0, 0), Pt(300, 0)]

  /// A V at (300, 0) pointing right, each barb [barb] long and opening [open]° from the line.
  private func v(barb: Float = 36, open: Float = 34, at tip: Pt = Pt(300, 0)) -> [Pt] {
    let a = open * .pi / 180
    return [tip + Pt(-barb * cosf(a), -barb * sinf(a)), tip, tip + Pt(-barb * cosf(a), barb * sinf(a))]
  }

  private func arrow(_ found: AutoArrow.Found?, last: Float? = nil) -> (a: Pt, b: Pt, head: Float)? {
    guard let found, case let .line(a, b, false, true, false, head) = AutoArrow.shape(found, last: last).geometry else { return nil }
    return (a, b, head)
  }

  private func near(_ p: Pt, _ q: Pt, _ slack: Float = 0.5) -> Bool { p.dist(q) <= slack }

  // MARK: These become arrows

  @Test func oneStrokeWithAVHead() {
    let drawn = stroke([Pt(0, 0), Pt(300, 0)] + [v()[0], Pt(300, 0), v()[2]])
    guard let a = arrow(AutoArrow.oneStroke(drawn)) else { Issue.record("not an arrow"); return }
    #expect(near(a.a, Pt(0, 0)) && near(a.b, Pt(300, 0)))
  }

  @Test func oneStrokeWithATriangleHead() {
    // To the end, up to one corner, out to the tip, back to the other corner and home.
    let drawn = stroke([Pt(0, 0), Pt(300, 0), Pt(300, -20), Pt(335, 0), Pt(300, 20), Pt(300, 0)])
    guard let a = arrow(AutoArrow.oneStroke(drawn)) else { Issue.record("not an arrow"); return }
    // The tip is the triangle's point, on the line's own direction.
    #expect(near(a.a, Pt(0, 0)) && near(a.b, Pt(335, 0)))
  }

  @Test func aSlantedOneStrokeArrow() {
    let drawn = turned(stroke([Pt(0, 0), Pt(300, 0)] + [v()[0], Pt(300, 0), v()[2]]), by: 30)
    guard let a = arrow(AutoArrow.oneStroke(drawn)) else { Issue.record("not an arrow"); return }
    #expect(near(a.a, Pt(0, 0)) && near(a.b, Pt(300, 0).rotated(30 * .pi / 180)))
  }

  @Test func aLineThenAV() {
    guard let ends = AutoArrow.straight(stroke(line)) else { Issue.record("not straight"); return }
    guard let a = arrow(AutoArrow.twoStrokes(a: ends.a, b: ends.b, head: stroke(v()))) else { Issue.record("not an arrow"); return }
    #expect(near(a.a, Pt(0, 0)) && near(a.b, Pt(300, 0)))
  }

  @Test func aLineThenATriangle() {
    let triangle = stroke([Pt(300, -20), Pt(335, 0), Pt(300, 20), Pt(300, -20)])
    guard let a = arrow(AutoArrow.twoStrokes(a: Pt(0, 0), b: Pt(300, 0), head: triangle)) else { Issue.record("not an arrow"); return }
    #expect(near(a.a, Pt(0, 0)) && near(a.b, Pt(335, 0)))
  }

  @Test func aHeadAtTheLinesStartPointsBack() {
    let head = stroke([Pt(30, -20), Pt(0, 0), Pt(30, 20)])
    guard let a = arrow(AutoArrow.twoStrokes(a: Pt(0, 0), b: Pt(300, 0), head: head)) else { Issue.record("not an arrow"); return }
    #expect(near(a.a, Pt(300, 0)) && near(a.b, Pt(0, 0)))
  }

  @Test func aNearlyLevelArrowLocksLevel() {
    // 2° off level: within the line snap's 3°, so it comes out exactly level at the same length.
    let drawn = turned(stroke([Pt(0, 0), Pt(300, 0)] + [v()[0], Pt(300, 0), v()[2]]), by: 2)
    guard let a = arrow(AutoArrow.oneStroke(drawn)) else { Issue.record("not an arrow"); return }
    #expect(abs(a.b.y) < 0.01 && abs(a.b.x - 300) < 0.5)
  }

  // MARK: Head size, on a 300-long line

  private func barbs(_ g: ShapeGeometry, pen: Float = 2.4) -> (length: [Float], open: [Float])? {
    guard case let .line(a, b, _, _, _, _) = g, g.paths(pen).count == 2 else { return nil }
    let head = g.paths(pen)[1]
    guard head.count == 3 else { return nil }
    let back = (a - b).normalized()
    let sides = [head[0] - b, head[2] - b]
    return (sides.map { $0.length() }, sides.map { acosf(min(max(back.dot($0.normalized()), -1), 1)) * 180 / .pi })
  }

  @Test func drawnBarbsGiveTheHeadsSize() {
    for (barb, head): (Float, Float) in [(22.5, 20), (61, 60)] {
      guard let found = AutoArrow.twoStrokes(a: Pt(0, 0), b: Pt(300, 0), head: stroke(v(barb: barb), step: 0.75)) else { Issue.record("\(barb): not an arrow"); return }
      #expect(abs(found.drawn - barb) < 0.5)
      let made = AutoArrow.shape(found, last: nil)
      #expect(made.head == head)
      guard let drawn = barbs(made.geometry) else { Issue.record("no head"); return }
      // Two equal barbs of that length, opening 30° each side.
      #expect(drawn.length.allSatisfy { abs($0 - head) < 0.01 })
      #expect(drawn.open.allSatisfy { abs($0 - 30) < 0.5 })
    }
  }

  @Test func aTinyDrawnHeadStillShowsTheUsualHead() {
    guard let found = AutoArrow.twoStrokes(a: Pt(0, 0), b: Pt(300, 0), head: stroke(v(barb: 12, open: 45), step: 0.75)) else { Issue.record("not an arrow"); return }
    let made = AutoArrow.shape(found, last: nil)
    #expect(made.head == 10)
    #expect(barbs(made.geometry)?.length.allSatisfy { abs($0 - 20) < 0.01 } ?? false)
  }

  @Test func doublingTheShapeDoublesTheHead() {
    let arrow = ShapeGeometry.line(a: Pt(0, 0), b: Pt(300, 0), arrowStart: false, arrowEnd: true, ticks: false, head: 60)
    guard case let .line(_, _, _, _, _, head) = arrow.transformed(.scaleAround(Pt(0, 0), 2)) else { Issue.record("not a line"); return }
    #expect(head == 120)
    #expect(barbs(arrow.transformed(.scaleAround(Pt(0, 0), 2)))?.length.allSatisfy { abs($0 - 120) < 0.01 } ?? false)
  }

  @Test func movingAnEndDotKeepsTheHead() {
    let arrow = ShapeGeometry.line(a: Pt(0, 0), b: Pt(300, 0), arrowStart: false, arrowEnd: true, ticks: false, head: 60)
    guard case let .line(_, _, _, true, _, head) = arrow.dragging(1, to: Pt(200, 150)) else { Issue.record("not an arrow"); return }
    #expect(head == 60)
    // A long head is never more than 0.6 of a line made short.
    guard let short = barbs(arrow.withHandle(1, Pt(50, 0))) else { Issue.record("no head"); return }
    #expect(short.length.allSatisfy { abs($0 - 30) < 0.01 })
  }

  @Test func anArrowWithoutAHeadLengthIsUnchanged() {
    let plain = ShapeGeometry.line(a: Pt(0, 0), b: Pt(300, 0), arrowStart: false, arrowEnd: true, ticks: false)
    #expect(barbs(plain)?.length.allSatisfy { abs($0 - 20) < 0.01 } ?? false)
    #expect(plain.json["head"] == nil)
    // And the head length is stored only when there is one, reading back the same.
    let drawn = ShapeGeometry.line(a: Pt(0, 0), b: Pt(300, 0), arrowStart: false, arrowEnd: true, ticks: false, head: 60)
    #expect(ShapeGeometry.decode(drawn.json) == drawn)
    #expect(ShapeGeometry.decode(plain.json) == plain)
  }

  // MARK: The lock onto the last head

  @Test func headsNearTheLastOneMatchIt() {
    for drawn: Float in [58, 70, 50] { #expect(AutoArrow.headSize(drawn: drawn, last: 60) == 60) }
    #expect(AutoArrow.headSize(drawn: 47, last: 60) == 40)
    #expect(AutoArrow.headSize(drawn: 79, last: 60) == 70)
    #expect(AutoArrow.headSize(drawn: 38.9, last: nil) == 30)
  }

  // MARK: These stay as ink

  private func twoStrokes(_ first: [Pt], _ second: [Pt]) -> AutoArrow.Found? {
    guard let ends = AutoArrow.straight(stroke(first)) else { return nil }
    return AutoArrow.twoStrokes(a: ends.a, b: ends.b, head: stroke(second))
  }

  @Test func aShortDashAndAChevron() {
    #expect(AutoArrow.straight(stroke([Pt(0, 0), Pt(25, 0)])) == nil)
    #expect(AutoArrow.oneStroke(stroke([Pt(0, 0), Pt(25, 0)])) == nil)
    #expect(AutoArrow.oneStroke(stroke([Pt(30, -12), Pt(45, 0), Pt(30, 12)])) == nil)
  }

  @Test func aSeven() {
    #expect(AutoArrow.oneStroke(stroke([Pt(0, 0), Pt(100, 0), Pt(40, 150)])) == nil)
  }

  @Test func aSingleBarb() {
    #expect(AutoArrow.oneStroke(stroke([Pt(0, 0), Pt(300, 0), Pt(270, -20)])) == nil)
    #expect(twoStrokes(line, [Pt(300, 0), Pt(270, -20)]) == nil)
  }

  @Test func aTBarAtTheEnd() {
    #expect(twoStrokes(line, [Pt(300, -20), Pt(300, 20)]) == nil)
    #expect(AutoArrow.oneStroke(stroke([Pt(0, 0), Pt(300, 0), Pt(300, -20), Pt(300, 20)])) == nil)
  }

  @Test func aVBelowTheLinesEnd() {
    #expect(twoStrokes(line, v(at: Pt(300, 50))) == nil)
  }

  @Test func aLongSecondLine() {
    #expect(twoStrokes(line, [Pt(300, -150), Pt(300, 150)]) == nil)
    #expect(twoStrokes(line, [Pt(300, 0), Pt(600, 10)]) == nil)
  }

  @Test func aCircleOnTheEnd() {
    let circle = (0...60).map { Pt(300, 0) + Pt.polar(20, 2 * .pi * Float($0) / 60) }
    #expect(AutoArrow.twoStrokes(a: Pt(0, 0), b: Pt(300, 0), head: circle) == nil)
  }

  @Test func aVPointingBackAlongTheLine() {
    #expect(twoStrokes(line, [Pt(330, -20), Pt(300, 0), Pt(330, 20)]) == nil)
    #expect(AutoArrow.oneStroke(stroke([Pt(0, 0), Pt(300, 0), Pt(330, -20), Pt(300, 0), Pt(330, 20)])) == nil)
  }

  // MARK: What counts as a straight line

  @Test func straightLines() {
    #expect(AutoArrow.straight(stroke([Pt(0, 0), Pt(150, 4), Pt(300, 0)])) != nil)
    // Too bowed, and one that doubles back on itself.
    #expect(AutoArrow.straight(stroke([Pt(0, 0), Pt(150, 30), Pt(300, 0)])) == nil)
    #expect(AutoArrow.straight(stroke([Pt(0, 0), Pt(300, 0), Pt(200, 0)])) == nil)
  }
}
