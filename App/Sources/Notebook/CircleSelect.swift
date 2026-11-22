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

  /// Caught: the loop turns into the lasso's dashes and fades as the selection's frame takes over.
  private func drawCaught(_ c: CircleLoop) {
    hold.ring.removeAllAnimations()
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    hold.ring.path = screenPath(c.loop, close: true)
    hold.ring.strokeColor = SelectionChromeView.accent.cgColor
    hold.ring.lineWidth = 1.5
    hold.ring.lineDashPattern = [6, 4]
    hold.ring.opacity = 0
    CATransaction.commit()
    let fade = CABasicAnimation(keyPath: "opacity")
    fade.fromValue = 1
    fade.toValue = 0
    fade.duration = 0.45
    fade.timingFunction = CAMediaTimingFunction(name: .easeOut)
    hold.ring.add(fade, forKey: "caught")
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
