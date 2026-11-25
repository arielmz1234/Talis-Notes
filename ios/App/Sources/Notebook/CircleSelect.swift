import PencilKit
import TaliCore
import UIKit

/// A stroke just drawn that closes on itself round something (Android's `LoopStroke`): the whole
/// stroke, its closed part, and when it began.
struct CircleLoop {
  var stroke: [Pt]
  var loop: [Pt]
  var began: Date
  var color: UIColor
  var size: CGFloat
}

/// Circle to select (Android's `noteLoop`, `armLoopPress`, `catchLoop`): with a pen that has it on,
/// a loop drawn round things, then pressed and held on for a moment, picks them up as the lasso
/// does, and the loop leaves the page.
extension NoteCanvas.Coordinator {
  /// How long the press rests on the loop before it catches (`CIRCLE_HOLD_MS`).
  static let circleHold: TimeInterval = 0.35
  /// How near the loop (screen points) the press has to land (`LOOP_REACH_DP`).
  static let circleReach: CGFloat = 14
  /// How near its start (screen points) the stroke's end has to come back (`CIRCLE_CLOSE_DP`).
  static let circleClose: Float = 30

  func installCircleSelect(on v: PKCanvasView) {
    hold.ring.fillColor = UIColor.clear.cgColor
    hold.ring.lineCap = .round
    hold.ring.lineJoin = .round
    hold.ring.zPosition = 9
    v.layer.addSublayer(hold.ring)
  }

  /// The stroke just finished: kept in mind if it's a loop round something.
  func noteCircle() {
    let pen = currentPen
    guard pen.circleSelect, !parent.viewOnly, let loop = Self.closedPart(hold.points, zoom: zoom), !picks(loop).isEmpty else {
      hold.circle = nil
      return
    }
    hold.circle = CircleLoop(stroke: hold.points, loop: loop, began: hold.began, color: UIColor(argb: pen.inkColor), size: CGFloat(pen.size))
  }

  func forgetCircle() {
    stopCircle()
    hold.circle = nil
  }

  /// The pen came down: on the last loop it may be the press that catches; anywhere else the loop is forgotten.
  func circlePressBegan(_ t: UITouch, at p: Pt) {
    stopCircle()
    guard let c = hold.circle, currentPen.circleSelect else {
      hold.circle = nil
      return
    }
    let reach = max(Float(Self.circleReach) / zoom, Float(c.size))
    let on = zip(c.stroke, c.stroke.dropFirst()).contains { Geo.distToSegment(p, $0, $1) < reach }
    guard on, let canvas else {
      hold.circle = nil
      return
    }
    hold.circlePress = t.location(in: canvas)
    drawCharge()
    hold.circleTimer = Timer.scheduledTimer(withTimeInterval: Self.circleHold, repeats: false) { [weak self] _ in
      MainActor.assumeIsolated { self?.catchCircle() }
    }
  }

  /// The press moved off: it's writing after all.
  func circlePressMoved(_ at: CGPoint) {
    guard let from = hold.circlePress else { return }
    if hypot(at.x - from.x, at.y - from.y) > Self.holdTolerance {
      stopCircle()
      hold.circle = nil
    }
  }

  /// The pen lifted. True when the touch was the press that caught the loop: nothing else happens with it.
  func circlePressEnded() -> Bool {
    let caught = hold.circlePress != nil && hold.circleTimer == nil && hold.circle == nil
    stopCircle()
    return caught
  }

  private func stopCircle() {
    hold.circleTimer?.invalidate()
    hold.circleTimer = nil
    if hold.circle != nil || hold.circlePress != nil {
      hold.ring.removeAllAnimations()
      hold.ring.path = nil
    }
    hold.circlePress = nil
  }

  /// What a loop would pick up: what the lasso would, leaving out writing begun with the loop itself.
  private func picks(_ loop: [Pt], since: Date? = nil) -> [String] {
    guard let area = Rect.around(loop) else { return [] }
    let pickLocked = LassoSettings.pickLocked
    let touched = pageItems.filter { item in
      guard !item.locked || pickLocked, LassoSettings.hits(item.pen), item.bounds.intersects(area) else { return false }
      return Self.lassoTouches(item, loop)
    }
    return withFills(touched.map(\.id))
  }

  /// Held long enough: the loop's ink and the press go, and what it goes round is picked up.
  private func catchCircle() {
    hold.circleTimer = nil
    guard let c = hold.circle, case .pen = parent.tool, !parent.viewOnly else { return stopCircle() }
    hold.circle = nil
    stopShapeTimer()
    // The loop and the press leave the page; strokes begun from the loop on are dropped if they arrive.
    dropInk(since: c.began.addingTimeInterval(-0.05))
    let picked = picks(c.loop)
    guard !picked.isEmpty else { return stopCircle() }
    setSelection(Set(picked), menu: true)
    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    parent.onHint(L("circled_and_picked_up"))
    drawCaught(c)
  }

  private func stopShapeTimer() {
    hold.timer?.invalidate()
    hold.timer = nil
  }

  private func screenPath(_ pts: [Pt], close: Bool) -> CGPath {
    let z = CGFloat(zoom)
    let path = CGMutablePath()
    path.addLines(between: pts.map { CGPoint(x: CGFloat($0.x) * z, y: CGFloat($0.y) * z) })
    if close { path.closeSubpath() }
    return path
  }

  /// While the press rests: a soft glow runs round the loop as it charges.
  private func drawCharge() {
    guard let c = hold.circle else { return }
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    hold.ring.path = screenPath(c.stroke, close: false)
    hold.ring.strokeColor = SelectionChromeView.accent.withAlphaComponent(0.25).cgColor
    hold.ring.lineWidth = c.size * CGFloat(zoom) + 6
    hold.ring.lineDashPattern = nil
    hold.ring.opacity = 1
    CATransaction.commit()
    let run = CABasicAnimation(keyPath: "strokeEnd")
    run.fromValue = 0
    run.toValue = 1
    run.duration = Self.circleHold
    run.timingFunction = CAMediaTimingFunction(name: .easeInEaseOut)
    hold.ring.add(run, forKey: "charge")
  }

  /// Caught (Android's `drawCircleCatch`): the loop, evenly spaced and closed, pulls in point for
  /// point onto the selection's frame, its ink turning into the lasso's marching dashes with a flash
  /// of glow and a soft fill as it goes.
  private func drawCaught(_ c: CircleLoop) {
    hold.ring.removeAllAnimations()
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    hold.ring.path = nil
    CATransaction.commit()
    guard let canvas else { return }
    let z = CGFloat(zoom)
    let n = 120
    let screen = c.loop.map { Pt($0.x * Float(z), $0.y * Float(z)) }
    let from = Geo.resample(screen + [screen[0]], n)
    let frame = lasso.bounds.map { CGRect(screenFrame($0)).integral.insetBy(dx: 0.5, dy: 0.5) }
    let to = frame.flatMap { f in from.count == n ? Self.framePoints(f, from) : nil }
    hold.catcher?.stop()
    hold.catcher = CircleCatch(on: canvas.layer, from: from.map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) }, to: to, color: c.color, pen: c.size * z)
  }

  /// Points round [r] matching [loop] point for point: from the place nearest its first point,
  /// going round the same way (Android's `framePoints`).
  static func framePoints(_ r: CGRect, _ loop: [Pt]) -> [CGPoint] {
    let w = r.width, h = r.height, per = 2 * (w + h)
    func at(_ s0: CGFloat) -> CGPoint {
      var s = (s0.truncatingRemainder(dividingBy: per) + per).truncatingRemainder(dividingBy: per)
      if s < w { return CGPoint(x: r.minX + s, y: r.minY) }
      s -= w
      if s < h { return CGPoint(x: r.maxX, y: r.minY + s) }
      s -= h
      if s < w { return CGPoint(x: r.maxX - s, y: r.maxY) }
      s -= w
      return CGPoint(x: r.minX, y: r.maxY - s)
    }
    let first = CGPoint(x: CGFloat(loop[0].x), y: CGFloat(loop[0].y))
    var s0: CGFloat = 0, best = CGFloat.greatestFiniteMagnitude
    for k in 0..<240 {
      let s = per * CGFloat(k) / 240
      let p = at(s)
      let d = hypot(p.x - first.x, p.y - first.y)
      if d < best {
        best = d
        s0 = s
      }
    }
    func area(_ pts: [CGPoint]) -> CGFloat {
      var a: CGFloat = 0
      for i in pts.indices {
        let p = pts[i], q = pts[(i + 1) % pts.count]
        a += p.x * q.y - q.x * p.y
      }
      return a
    }
    let corners = [CGPoint(x: r.minX, y: r.minY), CGPoint(x: r.maxX, y: r.minY), CGPoint(x: r.maxX, y: r.maxY), CGPoint(x: r.minX, y: r.maxY)]
    let dir: CGFloat = (area(loop.map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) }) > 0) == (area(corners) > 0) ? 1 : -1
    let n = loop.count
    return (0..<n).map { at(s0 + dir * per * CGFloat($0) / CGFloat(n - 1)) }
  }

  /// The closed part of [pts] (Android's `circleLoop`): from where its end came back near its own
  /// path to the end; nil for a line gone over and back, or a loop too small to mean it.
  static func closedPart(_ pts: [Pt], zoom: Float) -> [Pt]? {
    let n = pts.count
    guard n >= 10 else { return nil }
    let close = circleClose / zoom
    let end = pts[n - 1]
    var walked: Float = 0
    var best = -1
    var bestD = Float.greatestFiniteMagnitude
    for i in stride(from: n - 2, through: 0, by: -1) {
      walked += pts[i + 1].dist(pts[i])
      if walked < close * 4 { continue }
      let d = pts[i].dist(end)
      if d < bestD {
        bestD = d
        best = i
      }
    }
    guard best >= 0, bestD <= close else { return nil }
    let loop = Array(pts[best...])
    guard let box = Rect.around(loop), min(box.width, box.height) * zoom >= 20 else { return nil }
    var area: Float = 0
    for i in loop.indices {
      let a = loop[i], b = loop[(i + 1) % loop.count]
      area += a.x * b.y - b.x * a.y
    }
    guard abs(area / 2) >= 0.25 * box.width * box.height else { return nil }
    return loop
  }
}

/// The caught loop's close-in, played over the canvas (Android's `CIRCLE_CATCH_MS` of `drawCircleCatch`).
@MainActor
final class CircleCatch: NSObject {
  static let duration: CFTimeInterval = 0.56
  private let from: [CGPoint]
  private let to: [CGPoint]?
  private let color: UIColor
  private let pen: CGFloat
  private let start = CACurrentMediaTime()
  private let fill = CAShapeLayer(), glow = CAShapeLayer(), ink = CAShapeLayer(), dashes = CAShapeLayer()
  private var link: CADisplayLink?

  init(on host: CALayer, from: [CGPoint], to: [CGPoint]?, color: UIColor, pen: CGFloat) {
    self.from = from
    self.to = to
    self.color = color
    self.pen = pen
    super.init()
    let accent = SelectionChromeView.accent
    fill.fillColor = accent.withAlphaComponent(0x1C / 255).cgColor
    for l in [glow, ink, dashes] {
      l.fillColor = nil
      l.lineCap = .round
      l.lineJoin = .round
    }
    glow.strokeColor = accent.cgColor
    glow.lineWidth = pen + 12
    ink.strokeColor = color.cgColor
    dashes.strokeColor = accent.cgColor
    dashes.lineDashPattern = [6, 4]
    dashes.lineWidth = 1.5
    for (i, l) in [fill, glow, ink, dashes].enumerated() {
      l.zPosition = 9 + CGFloat(i) * 0.01
      host.addSublayer(l)
    }
    step()
    let link = CADisplayLink(target: self, selector: #selector(tick))
    link.add(to: .main, forMode: .common)
    self.link = link
  }

  @objc private func tick() { step() }

  private static func easeOut(_ t: CGFloat) -> CGFloat { 1 - pow(1 - min(max(t, 0), 1), 3) }

  private func step() {
    let e = CGFloat((CACurrentMediaTime() - start) * 1000)
    if e >= CGFloat(Self.duration * 1000) { return stop() }
    let pull = Self.easeOut((e - 70) / (CGFloat(Self.duration * 1000) - 70))
    let pts = to.map { to in zip(from, to).map { a, b in CGPoint(x: a.x + (b.x - a.x) * pull, y: a.y + (b.y - a.y) * pull) } } ?? from
    let gone = to == nil ? pull : 0
    let path = CGMutablePath()
    path.addLines(between: pts)
    path.closeSubpath()
    // Ink turns into the lasso's dashes over the first 220 ms, with a flash of the glow as it's caught.
    let turn = Self.easeOut(e / 220)
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    for l in [fill, glow, ink, dashes] { l.path = path }
    fill.opacity = Float(turn * (1 - pull))
    glow.opacity = Float(45.0 / 255 * (1 - turn) * (1 - gone))
    ink.opacity = Float(1 - turn)
    ink.lineWidth = pen + (1.5 - pen) * turn
    dashes.opacity = Float(turn * (1 - gone))
    // The dashes march round as it tightens, settling on the frame's own pattern.
    dashes.lineDashPhase = (1 - pull) * 30
    CATransaction.commit()
  }

  func stop() {
    link?.invalidate()
    link = nil
    for l in [fill, glow, ink, dashes] { l.removeFromSuperlayer() }
  }
}
