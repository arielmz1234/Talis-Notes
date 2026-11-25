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

  /// A pinch begins on an endless canvas: where it starts from, and the zoom at which everything
  /// written fits (where resistance begins).
  func pinchBegan(_ sv: UIScrollView) {
    pinchStart = nil
    guard !paged, let pinch = sv.pinchGestureRecognizer, pinch.numberOfTouches >= 2 else { return }
    let a = pinch.location(ofTouch: 0, in: sv), b = pinch.location(ofTouch: 1, in: sv)
    let spread = hypot(a.x - b.x, a.y - b.y)
    guard spread > 1 else { return }
    let room = (width: Float(sv.bounds.width - 32), height: Float(sv.bounds.height - parent.topClearance - 40))
    let z = sv.zoomScale
    // Already further out than resistance would take her (the writing grew, the screen turned): it begins here instead.
    let free = CGFloat(min(ZoomResist.free(content: contentBounds, room: room), Float(z) * ZoomResist.reach / 1.01))
    pinchStart = (spread, CGFloat(ZoomResist.raw(forShown: Float(z), free: Float(free))), free)
  }

  /// During a pinch on an endless canvas: the zoom the fingers ask for (from how far apart they are
  /// now against when they landed, so it doesn't matter how the scroll view works its own out),
  /// resisted past where everything written fits, with the page under the fingers' middle kept
  /// where it is. True when it set the zoom itself (the scroll view's own update is then replaced).
  func resistZoom(_ sv: UIScrollView) -> Bool {
    guard !paged, !resisting, let start = pinchStart, let pinch = sv.pinchGestureRecognizer, pinch.numberOfTouches >= 2 else { return false }
    let a = pinch.location(ofTouch: 0, in: sv), b = pinch.location(ofTouch: 1, in: sv)
    let spread = hypot(a.x - b.x, a.y - b.y)
    guard spread > 1 else { return false }
    let raw = start.raw * spread / start.spread
    let want = min(max(CGFloat(ZoomResist.resisted(Float(raw), free: Float(start.free))), sv.minimumZoomScale), sv.maximumZoomScale)
    let now = sv.zoomScale
    guard now > 0, abs(want - now) > now * 0.0005 else { return false }
    let mid = pinch.location(in: sv)
    let onScreen = CGPoint(x: mid.x - sv.contentOffset.x, y: mid.y - sv.contentOffset.y)
    let world = CGPoint(x: mid.x / now, y: mid.y / now)
    resisting = true
    sv.zoomScale = want
    sv.contentOffset = CGPoint(x: world.x * want - onScreen.x, y: world.y * want - onScreen.y)
    resisting = false
    return true
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
