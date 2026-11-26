import PencilKit
import TaliCore
import UIKit

/// The endless canvas (Android's `pageCount == null`): one page with paper all over it, reaching a
/// screen past everything written and everything seen, so she can always keep going right and down.
extension NoteCanvas.Coordinator {
  /// How big the endless canvas is now. It only grows, so nothing jumps while she looks around.
  func endlessSize() -> CGSize {
    guard let canvas else { return CGSize(width: CGFloat(A4.width), height: CGFloat(A4.height)) }
    let z = max(canvas.zoomScale, 0.01)
    let view = CGSize(width: canvas.bounds.width / z, height: canvas.bounds.height / z)
    let seen = CGPoint(x: (canvas.contentOffset.x + canvas.bounds.width) / z, y: (canvas.contentOffset.y + canvas.bounds.height) / z)
    let content = contentBounds.map(CGRect.init) ?? .null
    let right = max(CGFloat(A4.width), seen.x, content.isNull ? 0 : content.maxX) + view.width
    let bottom = max(CGFloat(A4.height), seen.y, content.isNull ? 0 : content.maxY) + view.height
    let old = pages?.pages.endless ?? .zero
    return CGSize(width: max(right, old.width), height: max(bottom, old.height))
  }

  /// Scrolled within half a screen of the edge: the canvas grows.
  func growEndless() {
    guard !paged, let canvas, let world = pages?.pages.endless else { return }
    let z = max(canvas.zoomScale, 0.01)
    let right = (canvas.contentOffset.x + canvas.bounds.width * 1.5) / z
    let bottom = (canvas.contentOffset.y + canvas.bounds.height * 1.5) / z
    if right > world.width || bottom > world.height { updateContent() }
  }

  /// How far out pages zoom (a phone needs this much to fit a page across). An endless canvas has
  /// no wall: zooming out gets harder the farther past what's written (`ZoomResist`); its floor is
  /// only there so the scroll view never bounces back from one.
  static let pagesLeastZoom: CGFloat = 0.2
  static let endlessLeastZoom: CGFloat = 0.05

  var leastZoom: CGFloat { paged ? Self.pagesLeastZoom : Self.endlessLeastZoom }

  /// On an endless canvas the notebook zooms itself (Android's camera): the scroll view's own pinch
  /// is off and this one sets the zoom and the place. Changing the zoom from inside the scroll view's
  /// pinch fought it: a small pinch out jumped down and to the right, to 24%, and her writing was
  /// gone (the owner's video, 25 Nov 2026). Pages keep the scroll view's pinch.
  func installEndlessPinch(on v: UIScrollView) {
    let pinch = UIPinchGestureRecognizer(target: self, action: #selector(endlessPinched(_:)))
    pinch.delegate = self
    pinch.cancelsTouchesInView = false
    v.addGestureRecognizer(pinch)
    endlessPinch.recognizer = pinch
    applyPinchMode()
  }

  /// Which pinch zooms: the scroll view's for pages, this one for an endless canvas.
  func applyPinchMode() {
    guard let canvas else { return }
    // The limit first: setting it turns the scroll view's pinch back on.
    canvas.minimumZoomScale = leastZoom
    canvas.pinchGestureRecognizer?.isEnabled = paged
    endlessPinch.recognizer?.isEnabled = !paged
    (canvas as? NoteCanvasView)?.ownPinchAllowed = { [weak self] in self?.paged ?? true }
  }

  /// The pinch on an endless canvas (Android's `PinchResist` with its own camera). Out, the zoom
  /// carries on along the resistance; in, it follows the fingers (`ZoomResist.pinched`), measured from
  /// how far apart they are against where it was last taken from. The page under the fingers' middle
  /// stays under it, so moving the fingers together moves the page too. A finger lifting or landing,
  /// or the zoom held at its closest, takes the measure again from there, so nothing jumps.
  @objc func endlessPinched(_ g: UIPinchGestureRecognizer) {
    guard let canvas, !paged else { return }
    switch g.state {
    case .began:
      // The scroll view's own drag lets go: this pinch moves the page itself.
      canvas.panGestureRecognizer.isEnabled = false
      canvas.panGestureRecognizer.isEnabled = true
      zoomedByHand = true
      let room = (width: Float(canvas.bounds.width - 32), height: Float(canvas.bounds.height - parent.topClearance - 40))
      endlessPinch.free = CGFloat(ZoomResist.freeAt(ZoomResist.free(content: contentBounds, room: room), zoomNow: Float(canvas.zoomScale)))
      anchorPinch(g)
    case .changed:
      guard g.numberOfTouches >= 2 else { return }
      if g.numberOfTouches != endlessPinch.touches { return anchorPinch(g) }
      let ratio = g.scale / max(endlessPinch.scale, 0.0001)
      let asked = CGFloat(ZoomResist.pinched(startZoom: Float(endlessPinch.zoom), free: Float(endlessPinch.free), ratio: Float(ratio)))
      let want = min(max(asked, canvas.minimumZoomScale), canvas.maximumZoomScale)
      let mid = g.location(in: canvas)
      let onScreen = CGPoint(x: mid.x - canvas.contentOffset.x, y: mid.y - canvas.contentOffset.y)
      if abs(canvas.zoomScale - want) > want * 0.0002 { canvas.setZoomScale(want, animated: false) }
      let focus = endlessPinch.focus
      canvas.contentOffset = CGPoint(x: focus.x * want - onScreen.x, y: focus.y * want - onScreen.y)
      // Held at the closest zoom (or the floor): carry on from there, so pinching back acts at once.
      if abs(want - asked) > asked * 1e-3 { anchorPinch(g) }
    case .ended, .cancelled, .failed:
      updateContent()
      makeRoom()
    default: break
    }
  }

  /// Takes the pinch's measure from here: the zoom now, the fingers' scale and count, and the point
  /// of the page under their middle.
  private func anchorPinch(_ g: UIPinchGestureRecognizer) {
    guard let canvas else { return }
    let z = max(canvas.zoomScale, 0.0001)
    let mid = g.location(in: canvas)
    endlessPinch.zoom = z
    endlessPinch.scale = g.scale
    endlessPinch.touches = g.numberOfTouches
    endlessPinch.focus = CGPoint(x: mid.x / z, y: mid.y / z)
  }

  /// How much room an endless canvas keeps round the writing on every side (world units): it's
  /// moved into the middle of that much paper, so she can go left and up as well as right and down.
  static let endlessRoom: Float = 6000

  /// How far this notebook's writing was moved to give it room on an endless canvas (kept on this
  /// device only), so going back to pages puts it exactly where it was.
  var endlessShift: Pt {
    get {
      let v = UserDefaults.standard.array(forKey: "endlessShift-\(parent.notebook.id)") as? [Double] ?? []
      return v.count == 2 ? Pt(Float(v[0]), Float(v[1])) : .zero
    }
    set {
      let key = "endlessShift-\(parent.notebook.id)"
      if newValue == .zero { UserDefaults.standard.removeObject(forKey: key) } else { UserDefaults.standard.set([Double(newValue.x), Double(newValue.y)], forKey: key) }
    }
  }

  /// Switches between A4 pages and one endless page (the ⋯ menu's Endless canvas). The notebook
  /// given to the canvas has already switched. Undo can't reach back across the switch.
  func setEndless(_ on: Bool) {
    clearSelection()
    finishText()
    if on {
      try? parent.db.run("UPDATE notebooks SET pages=NULL WHERE id=?", [.text(parent.notebook.id)])
      // Writing is no longer hidden past page edges.
      saveNow()
      makeRoom(force: true)
    } else {
      convertToPages()
    }
    canvas?.undoManager?.removeAllActions()
    reportUndo()
    updateChrome()
  }

  /// On an endless canvas: when the view is near the left or top edge of the paper (or [force]),
  /// the writing moves into the middle of fresh room, and the view moves with it, so nothing seems to move.
  func makeRoom(force: Bool = false) {
    guard !paged, let canvas else { return }
    let z = max(canvas.zoomScale, 0.01)
    let view = CGRect(x: canvas.contentOffset.x / z, y: canvas.contentOffset.y / z, width: canvas.bounds.width / z, height: canvas.bounds.height / z)
    let room = CGFloat(Self.endlessRoom)
    let content = contentBounds.map(CGRect.init) ?? .null
    let left = min(view.minX, content.isNull ? view.minX : content.minX)
    let top = min(view.minY, content.isNull ? view.minY : content.minY)
    let dx = left < room / 2 || force ? (room - left).rounded() : 0
    let dy = top < room / 2 || force ? (room - top).rounded() : 0
    guard dx != 0 || dy != 0 else {
      if force { load() }
      return
    }
    saveNow()
    let rows = (try? parent.db.items(of: parent.notebook.id)) ?? []
    write(rows.map { $0.translated(Float(dx), Float(dy)) })
    endlessShift = Pt(endlessShift.x + Float(dx), endlessShift.y + Float(dy))
    let offset = canvas.contentOffset
    load()
    updateContent()
    canvas.setContentOffset(CGPoint(x: offset.x + dx * z, y: offset.y + dy * z), animated: false)
  }

  /// Turns the endless canvas into A4 pages (Android's `convertToPages`). The room it was given
  /// is taken away first, so writing from the pages goes back exactly where it was; only writing
  /// that is then left of, above or wider than the first page comes onto the pages (centred when
  /// it fits), and there are as many pages as it needs.
  private func convertToPages() {
    guard let canvas else { return }
    saveNow()
    let back = endlessShift
    endlessShift = .zero
    let before = ((try? parent.db.items(of: parent.notebook.id)) ?? []).map { $0.translated(-back.x, -back.y) }
    let content = before.reduce(Rect?.none) { r, i in
      let b = Rect(i.l, i.t, i.r, i.b)
      return r.map { Rect(min($0.left, b.left), min($0.top, b.top), max($0.right, b.right), max($0.bottom, b.bottom)) } ?? b
    }
    let shift = layout.shiftOntoPages(content)
    if shift.x != 0 || shift.y != 0 || back != .zero {
      write(before.map { $0.translated(shift.x, shift.y) })
    }
    let moved = content.map { Rect($0.left + shift.x, $0.top + shift.y, $0.right + shift.x, $0.bottom + shift.y) }
    // The view keeps the place she was looking at, now on the pages.
    let z = canvas.zoomScale
    let lookY = (canvas.contentOffset.y + canvas.contentInset.top) / z - CGFloat(back.y - shift.y)
    pageCount = layout.needed(moved)
    persistPages()
    load()
    // The page she was on, across the screen with its top under the toolbars.
    let page = min(max(layout.indexAt(Float(lookY)), 0), pageCount - 1)
    let fit = min((canvas.bounds.width * 0.94) / CGFloat(layout.width), 1.5)
    canvas.setZoomScale(fit, animated: false)
    updateContent()
    canvas.setContentOffset(CGPoint(x: -canvas.contentInset.left, y: CGFloat(layout.rect(page).top) * fit - canvas.contentInset.top), animated: false)
  }

  private func write(_ rows: [ItemRow]) {
    for r in rows { try? parent.db.put(r, in: parent.notebook.id) }
  }

  /// Everything on an endless canvas on screen at once (Android's `fitContent`).
  func fitEverything() {
    guard let canvas else { return }
    let content = contentBounds.map(CGRect.init) ?? .null
    guard !content.isNull, content.width > 1 || content.height > 1 else { return }
    let r = content.insetBy(dx: -24, dy: -24)
    let room = CGSize(width: canvas.bounds.width - 32, height: canvas.bounds.height - parent.topClearance - 40)
    let fit = min(max(min(room.width / r.width, room.height / r.height), canvas.minimumZoomScale), 1.5)
    canvas.setZoomScale(fit, animated: false)
    updateContent()
    canvas.setContentOffset(CGPoint(x: r.minX * fit - (canvas.bounds.width - r.width * fit) / 2, y: r.minY * fit - canvas.contentInset.top), animated: true)
  }
}

/// The endless canvas's pinch: the recogniser, where resistance begins, and the measure it's taken
/// from (the zoom, the fingers' scale and count, the page's point under their middle).
struct EndlessPinchState {
  var recognizer: UIPinchGestureRecognizer?
  var free: CGFloat = 1
  var zoom: CGFloat = 1
  var scale: CGFloat = 1
  var touches = 0
  var focus = CGPoint.zero
}
