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

/// Android's `AutoArrowTest`, ported as it is (same strokes, same wobble, same tolerances).
struct AndroidAutoArrowTests {
  /// The path through [corners], sampled every 2 units with a little hand wobble.
  private func drawn(_ corners: Pt..., wobble: Float = 0.6) -> [Pt] {
    var out: [Pt] = []
    var k = 0
    for i in 0..<(corners.count - 1) {
      let a = corners[i], b = corners[i + 1]
      let steps = max(2, Int(a.dist(b) / 2))
      for s in 0..<steps {
        let t = Float(s) / Float(steps)
        out.append(a + (b - a) * t + Pt(0, wobble * sinf(Float(k) * 0.7)))
        k += 1
      }
    }
    out.append(corners[corners.count - 1])
    return out
  }

  private func line(_ f: AutoArrow.Found?) -> (a: Pt, b: Pt, start: Bool, end: Bool, head: Float)? {
    guard let f, case let .line(a, b, s, e, _, head) = AutoArrow.shape(f, last: nil).geometry else { return nil }
    return (a, b, s, e, head)
  }

  private func expectArrow(_ a: Pt, _ b: Pt, _ found: AutoArrow.Found?) {
    guard let l = line(found) else {
      Issue.record("not an arrow")
      return
    }
    #expect(l.end && !l.start)
    #expect(abs(l.a.x - a.x) <= 2 && abs(l.a.y - a.y) <= 2)
    #expect(abs(l.b.x - b.x) <= 3 && abs(l.b.y - b.y) <= 3)
  }

  @Test func oneStrokeWithAVHead() {
    expectArrow(Pt(0, 0), Pt(200, 0), AutoArrow.oneStroke(drawn(Pt(0, 0), Pt(200, 0), Pt(172, -20), Pt(200, 0), Pt(172, 20))))
  }

  @Test func oneStrokeWithATriangleHead() {
    expectArrow(Pt(0, 0), Pt(230, 0), AutoArrow.oneStroke(drawn(Pt(0, 0), Pt(200, 0), Pt(200, -16), Pt(230, 0), Pt(200, 16), Pt(200, 0))))
  }

  @Test func aSlantedOneStrokeArrowKeepsItsAngle() {
    let ang: Float = 0.5
    let u = Pt(cosf(ang), sinf(ang)), n = Pt(-u.y, u.x)
    let tip = u * 180
    expectArrow(Pt(0, 0), tip, AutoArrow.oneStroke(drawn(Pt(0, 0), tip, tip - u * 26 + n * 18, tip, tip - u * 26 - n * 18)))
  }

  @Test func aLineThenAHeadAtItsEnd() {
    guard let (a, b) = AutoArrow.straight(drawn(Pt(0, 0), Pt(200, 0))) else { Issue.record("not straight"); return }
    expectArrow(Pt(0, 0), Pt(200, 0), AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(175, -18), Pt(201, 0), Pt(175, 18))))
  }

  @Test func aLineThenATriangleHead() {
    guard let (a, b) = AutoArrow.straight(drawn(Pt(0, 0), Pt(0, 200))) else { Issue.record("not straight"); return }
    expectArrow(Pt(0, 0), Pt(0, 228), AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(-15, 200), Pt(0, 228), Pt(15, 200), Pt(-15, 200))))
  }

  @Test func aHeadAtTheStartPointsTheOtherWay() {
    guard let (a, b) = AutoArrow.straight(drawn(Pt(0, 0), Pt(200, 0))) else { Issue.record("not straight"); return }
    expectArrow(Pt(200, 0), Pt(0, 0), AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(25, -18), Pt(0, 0), Pt(25, 18))))
  }

  @Test func writingStaysWriting() {
    #expect(AutoArrow.straight(drawn(Pt(0, 0), Pt(25, 0))) == nil)
    #expect(AutoArrow.oneStroke(drawn(Pt(0, 0), Pt(25, 0), Pt(18, -6), Pt(25, 0), Pt(18, 6))) == nil)
    #expect(AutoArrow.oneStroke(drawn(Pt(0, 0), Pt(60, 0), Pt(20, 90))) == nil)
    #expect(AutoArrow.oneStroke(drawn(Pt(0, 0), Pt(200, 0), Pt(175, -20))) == nil)
    guard let (a, b) = AutoArrow.straight(drawn(Pt(0, 0), Pt(200, 0))) else { Issue.record("not straight"); return }
    #expect(AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(200, -20), Pt(200, 20))) == nil)
    #expect(AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(185, 30), Pt(200, 60), Pt(215, 30))) == nil)
    #expect(AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(200, 0), Pt(200, 180))) == nil)
    let circle = (0...72).map { i -> Pt in
      let t = Float(i) * .pi * 2 / 72
      return Pt(215 - 15 * cosf(t), 15 * sinf(t))
    }
    #expect(AutoArrow.twoStrokes(a: a, b: b, head: circle) == nil)
    #expect(AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(225, -18), Pt(199, 0), Pt(225, 18))) == nil)
  }

  @Test func aWobblyLineIsNotAShaft() {
    #expect(AutoArrow.straight(drawn(Pt(0, 0), Pt(60, 30), Pt(120, 0), Pt(200, 30))) == nil)
  }

  @Test func theCleanHeadIsAStandardHeadAsLongAsTheDrawnOne() {
    guard let (a, b) = AutoArrow.straight(drawn(Pt(0, 0), Pt(300, 0))) else { Issue.record("not straight"); return }
    guard let small = line(AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(280, -8), Pt(301, 0), Pt(280, 8)))),
      let bigFound = AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(255, -40), Pt(301, 0), Pt(255, 40)))
    else { Issue.record("not arrows"); return }
    let big = AutoArrow.shape(bigFound, last: nil).geometry
    #expect(abs(small.head - 20) < 0.01)
    guard case let .line(_, _, _, _, _, bigHead) = big else { Issue.record("not a line"); return }
    #expect(abs(bigHead - 60) < 0.01)
    let barbs = big.paths(2)[1]
    #expect(abs(barbs[0].dist(barbs[1]) - 60) < 0.5 && abs(barbs[2].dist(barbs[1]) - 60) < 0.5)
    #expect(abs(abs(barbs[0].y - barbs[1].y) - 30) < 0.5 && abs(abs(barbs[2].y - barbs[1].y) - 30) < 0.5)
    guard case let .line(_, _, _, _, _, doubled) = big.transformed(.scaleAround(Pt(0, 0), 2)) else { Issue.record("not a line"); return }
    #expect(abs(doubled - 120) < 0.5)
    guard let tiny = AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(290, -9), Pt(301, 0), Pt(290, 9))) else { Issue.record("not an arrow"); return }
    let t = AutoArrow.shape(tiny, last: nil).geometry.paths(2)[1]
    #expect(t[0].dist(t[1]) >= 19.9)
  }

  /// A V with its point rounded off: from [l] round a curve of [round] radius near [tip] to [r].
  private func roundedV(_ l: Pt, _ tip: Pt, _ r: Pt, round: Float) -> [Pt] {
    let inL = tip + (l - tip).normalized() * round
    let inR = tip + (r - tip).normalized() * round
    var out = drawn(l, inL)
    for k in 1...12 {
      let t = Float(k) / 12
      out.append(inL * ((1 - t) * (1 - t)) + tip * (2 * t * (1 - t)) + inR * (t * t))
    }
    return out + drawn(inR, r)
  }

  @Test func roundedAndNearlyUHeadsCount() {
    guard let (a, b) = AutoArrow.straight(drawn(Pt(0, 0), Pt(0, 60))) else { Issue.record("not straight"); return }
    #expect(AutoArrow.twoStrokes(a: a, b: b, head: roundedV(Pt(-9, 44), Pt(0, 62), Pt(9, 44), round: 6)) != nil)
    let u = drawn(Pt(-7, 40), Pt(-6, 54)) + (0...12).map { k -> Pt in
      let t = Float.pi * Float(k) / 12
      return Pt(-6 * cosf(t), 54 + 6 * sinf(t))
    } + drawn(Pt(6, 54), Pt(7, 40))
    guard let arrow = line(AutoArrow.twoStrokes(a: a, b: b, head: u)) else { Issue.record("not an arrow"); return }
    #expect(arrow.b.y > 55)
  }

  @Test func aSlightlyBowedShaftCounts() {
    let bowed = (0...30).map { k -> Pt in
      let t = Float(k) / 30
      return Pt(80 * t, -4 * sinf(.pi * t))
    }
    expectArrow(Pt(0, 0), Pt(81, 0), AutoArrow.pair(bowed, drawn(Pt(66, -10), Pt(81, 0), Pt(66, 10))))
  }

  @Test func theHeadCanComeFirst() {
    let head = drawn(Pt(-10, 14), Pt(0, 0), Pt(10, 14))
    let shaft = drawn(Pt(1, 60), Pt(0, 1))
    expectArrow(Pt(1, 60), Pt(0, 0), AutoArrow.pair(head, shaft))
    // A Y written as a V then its stem stays writing.
    #expect(AutoArrow.pair(drawn(Pt(-12, -20), Pt(0, 0), Pt(12, -20)), drawn(Pt(0, 0), Pt(0, 45))) == nil)
  }

  @Test func aHeadDrawnLikeTheLastOneGetsExactlyTheSameHead() {
    guard let (a, b) = AutoArrow.straight(drawn(Pt(0, 0), Pt(300, 0))),
      let first = AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(255, -40), Pt(301, 0), Pt(255, 40))),
      let second = AutoArrow.twoStrokes(a: a, b: b, head: drawn(Pt(250, -42), Pt(301, 0), Pt(250, 42)))
    else { Issue.record("not arrows"); return }
    let head = AutoArrow.shape(first, last: nil).head
    #expect(AutoArrow.shape(second, last: head).head == head)
  }
}
