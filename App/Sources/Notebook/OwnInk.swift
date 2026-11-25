import PencilKit
import TaliCore
import UIKit

/// Every pen's ink is drawn live by the notebook, not by PencilKit: the stroke is smoothed as it's
/// written (Android's `PenSmoother`, so slow writing shows no jitter), kept to its page when Write
/// between pages is off (Android's split strokes), and shown with PencilKit's own renderer from the
/// very stroke that's kept. Nothing about it changes when the pen lifts: the live picture stays until
/// the canvas has drawn the kept stroke. The laser is drawn here too.
enum OwnInk {
  case laser
  case straight(PenSlot)
  case trace(PenSlot)
}

struct OwnInkState {
  var watcher: StrokeWatcher?
  /// Fixed when the touch lands, so changing pens mid-stroke can't change what it writes.
  var mode: OwnInk?
  var laser = LaserState()
  var samples: [InkInput] = []
  var start = Pt.zero
  var end = Pt.zero
  var startTime: TimeInterval = 0
  var began = Date()
  /// The stroke in hand, as PencilKit will draw it once kept.
  var live = CALayer()
  /// Strokes just kept, still shown here until the canvas has drawn them.
  var handedIn: [CALayer] = []
  /// Strokes kept from here and when they began, so a scribble or a caught loop can take them back.
  var kept: [(id: String, began: Date)] = []
}

extension NoteCanvas.Coordinator {
  /// Nil when the tool isn't one that writes (or she's only looking).
  var ownInk: OwnInk? {
    guard !parent.viewOnly else { return nil }
    switch parent.tool {
    case .laser: return .laser
    case .pen(let id):
      let pen = parent.pens.first { $0.id == id } ?? PenSlot.defaults[0]
      return pen.straight ? .straight(pen) : .trace(pen)
    default: return nil
    }
  }

  var tracesOwnInk: Bool {
    if case .trace = ownInk { return true }
    return false
  }

  func installOwnInk(on v: PKCanvasView) {
    let w = StrokeWatcher(target: nil, action: nil)
    w.delegate = self
    w.accepts = { [weak self] t in self.map { $0.ownInk != nil && $0.draws(t) && $0.claim(t) == nil } ?? false }
    w.began = { [weak self] t, _ in self?.ownBegan(t) }
    w.moved = { [weak self] t, e in self?.ownMoved(t, e) }
    w.ended = { [weak self] cancelled in self?.ownEnded(cancelled) }
    v.addGestureRecognizer(w)
    // One finger or the pen draws; scrolling waits until it's clear this touch doesn't.
    v.panGestureRecognizer.require(toFail: w)
    own.watcher = w
    own.laser.layer.zPosition = 11
    v.layer.addSublayer(own.laser.layer)
    own.live.zPosition = 8
    v.layer.addSublayer(own.live)
  }

  func worldPoint(of t: UITouch) -> Pt {
    guard let canvas else { return .zero }
    let p = t.location(in: canvas)
    return Pt(Float(p.x / canvas.zoomScale), Float(p.y / canvas.zoomScale))
  }

  private func ownBegan(_ t: UITouch) {
    own.mode = ownInk
    switch own.mode {
    case .laser: laserBegan(worldPoint(of: t))
    case .straight(let pen):
      own.began = Date()
      own.start = worldPoint(of: t)
      own.end = StraightLine.end(from: own.start, to: own.start)
      showLive(pen, StraightLine.inputs(own.start, own.end))
    case .trace(let pen):
      own.startTime = t.timestamp
      own.began = Date()
      own.samples = [sample(t)]
      showLive(pen, InkInputs(points: own.samples, tool: .stylus))
    case nil: break
    }
  }

  private func ownMoved(_ t: UITouch, _ e: UIEvent) {
    let touches = e.coalescedTouches(for: t) ?? [t]
    switch own.mode {
    case .laser: laserMoved(touches.map(worldPoint(of:)))
    case .straight(let pen):
      own.end = StraightLine.end(from: own.start, to: worldPoint(of: touches.last ?? t))
      showLive(pen, StraightLine.inputs(own.start, own.end))
    case .trace(let pen):
      own.samples += touches.map(sample)
      // The stroke just became a shape (hold-to-shape draws that), or was taken back (the press that
      // caught a loop): it isn't shown any more.
      if hold.live != nil || (hold.dropSince.map { own.began >= $0 } ?? false) {
        clearLive()
        return
      }
      // Where the pen is about to be, so the line keeps up with it; only what it really did is kept.
      let ahead = (e.predictedTouches(for: t) ?? []).map(sample)
      showLive(pen, InkInputs(points: smoothed(pen, own.samples + ahead), tool: .stylus))
    case nil: break
    }
  }

  private func ownEnded(_ cancelled: Bool) {
    let mode = own.mode
    own.mode = nil
    switch mode {
    case .laser: laserEnded()
    case .straight(let pen):
      forgetArrowLine()
      if cancelled { clearLive() } else { hand(pen, StraightLine.inputs(own.start, own.end), began: own.began) }
    case .trace(let pen):
      let inputs = InkInputs(points: smoothed(pen, own.samples), tool: .stylus)
      let began = own.began
      // As the pen gave them, before smoothing: what Auto arrow looks at.
      let raw = own.samples.map { Pt($0.x, $0.y) }
      own.samples = []
      if cancelled || hold.live != nil {
        forgetArrowLine()
        clearLive()
      } else {
        hand(pen, inputs, began: began, raw: raw)
      }
    case nil: break
    }
  }

  private func smoothed(_ pen: PenSlot, _ points: [InkInput]) -> [InkInput] {
    PenSmoother.smooth(points, zoom: Float(canvas?.zoomScale ?? 1))
  }

  /// One pen sample as Android keeps them (see `InkStroke.from`): seconds since the stroke began,
  /// half of Apple's force as pressure, the tilt from upright and the way the pencil leans.
  private func sample(_ t: UITouch) -> InkInput {
    let p = worldPoint(of: t)
    let lean = t.type == .pencil ? t.azimuthAngle(in: canvas) : 0
    return InkInput(
      x: p.x, y: p.y, t: Float(max(t.timestamp - own.startTime, 0)), pressure: t.type == .pencil ? Float(min(max(t.force / 2, 0), 1)) : 0.5,
      tilt: Float(max(0, .pi / 2 - t.altitudeAngle)), orientation: Float(lean < 0 ? lean + 2 * .pi : lean))
  }

  private func stroke(_ pen: PenSlot, _ inputs: InkInputs, id: String = "") -> InkStroke {
    InkStroke(id: id, z: topZ + 1, pen: pen.type, tags: InkStroke.tags(for: pen), color: pen.inkColor, size: pen.size, inputs: inputs)
  }

  /// The stroke in hand drawn as the canvas will draw it once kept (its page's edge included), over
  /// the part of it on screen.
  private func showLive(_ pen: PenSlot, _ inputs: InkInputs) {
    guard let canvas, !inputs.points.isEmpty else { return }
    let s = stroke(pen, inputs)
    let pk = s.pkStroke(mask: mask(for: s))
    let z = canvas.zoomScale
    let seen = CGRect(x: canvas.bounds.minX / z, y: canvas.bounds.minY / z, width: canvas.bounds.width / z, height: canvas.bounds.height / z)
    var box = pk.renderBounds.intersection(seen).integral
    if let m = pk.mask { box = box.intersection(m.bounds.insetBy(dx: -1, dy: -1)) }
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    if box.isNull || box.isEmpty {
      own.live.contents = nil
    } else {
      let scale = canvas.traitCollection.displayScale
      var image: UIImage?
      canvas.traitCollection.performAsCurrent { image = PKDrawing(strokes: [pk]).image(from: box, scale: z * scale) }
      own.live.frame = CGRect(x: box.minX * z, y: box.minY * z, width: box.width * z, height: box.height * z)
      own.live.contentsScale = scale
      own.live.contents = image?.cgImage
    }
    CATransaction.commit()
  }

  func clearLive() {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    own.live.contents = nil
    CATransaction.commit()
  }

  /// The stroke is kept: its live picture stays on until the canvas shows it, and the next stroke
  /// is drawn in a new one. Kept a moment later, so a scribble to delete or a stroke that became a
  /// shape (which end on the same lift) can still say it isn't.
  private func hand(_ pen: PenSlot, _ inputs: InkInputs, began: Date, raw: [Pt]? = nil) {
    guard let canvas, !inputs.points.isEmpty else { return clearLive() }
    let lifted = Date()
    showLive(pen, inputs)
    let shown = own.live
    own.handedIn.append(shown)
    let next = CALayer()
    next.zPosition = shown.zPosition
    canvas.layer.addSublayer(next)
    own.live = next
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      if self.hold.dropSince.map({ began >= $0 }) ?? false {
        self.forgetArrowLine()
        self.letGo(shown, now: true)
        return
      }
      let s = self.stroke(pen, inputs, id: newId())
      // Only what its page shows is kept (Android's split strokes): the rest would come back into
      // view when it's moved.
      self.growPages(toHold: s.bounds)
      let kept = self.keptToPage(s)
      for piece in kept { self.own.kept.append((piece.id, began)) }
      if self.own.kept.count > 50 { self.own.kept.removeFirst(self.own.kept.count - 50) }
      self.commit(remove: [], add: kept.map(PageItem.ink))
      // A stroke cut at its page's edge isn't looked at for an arrow: part of what was drawn is gone.
      let whole = kept.count == 1 && kept[0].inputs.points.count == s.inputs.points.count
      if let raw, whole, self.autoArrow(after: s, pen: pen, raw: raw, began: began, lifted: lifted) {
        // The clean arrow is already up, in this same frame: the hand-drawn ink goes as it comes.
        self.letGo(shown, now: true)
      } else {
        if raw == nil || !whole { self.forgetArrowLine() }
        self.afterRender { self.letGo(shown, now: false) }
      }
    }
  }

  private func letGo(_ shown: CALayer, now: Bool) {
    shown.removeFromSuperlayer()
    own.handedIn.removeAll { $0 === shown }
  }

  /// Ids of strokes kept from here that began at or after [since].
  func ownStrokes(since: Date) -> Set<String> {
    Set(own.kept.filter { $0.began >= since }.map(\.id))
  }
}
