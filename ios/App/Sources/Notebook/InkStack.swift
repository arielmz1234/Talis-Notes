import PencilKit
import TaliCore
import UIKit

/// Writing and the page's other things in one stack, as Android draws them. PencilKit draws all its
/// ink over the page, so writing that something on the page covers (a shape, text or a picture in
/// front placed higher in the stack, after Send to back or Bring to front) is drawn by the page
/// instead, in its place, and PencilKit's copy is hidden behind an empty mask. It stays in
/// PencilKit's drawing, so everything that works on the drawing (erasing, picking up) is unchanged.
extension NoteCanvas.Coordinator {
  /// The mask that hides a stroke from PencilKit: a speck far off the paper (an empty mask is taken as none).
  static let hiddenSpot = CGRect(x: -100_000, y: -100_000, width: 1, height: 1)
  static let hidden = UIBezierPath(rect: hiddenSpot)

  static func isHidden(_ pk: PKStroke) -> Bool { pk.mask.map { $0.bounds == hiddenSpot } ?? false }

  /// Works out again which writing is covered, and moves it between PencilKit and the page.
  func restack() {
    guard let canvas, let pages, lasso.before == nil, inkBase == nil else { return }
    let strokes = canvas.drawing.strokes
    guard strokes.count == stored.count else { return }
    let under = pages.pages.under
    // What the page draws over PencilKit's level: shapes, text, pictures in front, locked writing.
    var over: [(z: Int64, box: TaliCore.Rect)] = under.shapes.map { s in (s.z, PageItem.shape(s).bounds) }
    over += under.texts.map { t in (t.z, PageItem.text(t).bounds) }
    over += under.pictures.filter(\.row.front).map { p in (p.row.z, p.row.rect) }
    over += under.lockedInk.map { s in (s.z, s.bounds) }
    let highest = over.map(\.z).max() ?? .min
    // From the top of the stack down: writing under anything the page draws that it touches goes to the page too.
    var covered = Set<Int>()
    for i in stored.indices.sorted(by: { stored[$0].stroke.z > stored[$1].stroke.z }) {
      let s = stored[i].stroke
      guard s.z < highest else { continue }
      let box = s.bounds
      if over.contains(where: { $0.z > s.z && $0.box.intersects(box) }) {
        covered.insert(i)
        over.append((s.z, box))
      }
    }
    var next = strokes
    var changed: [TaliCore.Rect] = []
    for i in strokes.indices {
      // The highlighter is always hidden from PencilKit: the notebook draws it (HighlightInk.swift).
      let hide = covered.contains(i) || Self.drawnByNotebook(stored[i].stroke)
      guard hide != Self.isHidden(strokes[i]) else { continue }
      next[i].mask = hide ? Self.hidden : mask(for: stored[i].stroke)
      changed.append(stored[i].stroke.bounds)
    }
    let buried = covered.sorted().map { i in (stroke: stored[i].stroke, pk: stored[i].stroke.pkStroke(mask: mask(for: stored[i].stroke))) }
    let wasBuried = under.buried.map(\.stroke)
    defer { refreshHighlights() }
    guard !changed.isEmpty || wasBuried.map(\.id) != buried.map(\.stroke.id) || zip(wasBuried, buried).contains(where: { $0 != $1.stroke }) else { return }
    pages.pages.under.buried = buried
    if !changed.isEmpty {
      loading = true
      canvas.undoManager?.disableUndoRegistration()
      setDrawing(PKDrawing(strokes: next))
      canvas.undoManager?.enableUndoRegistration()
      loading = false
    }
    redrawPage(unionOf(wasBuried + buried.map(\.stroke)))
  }
}
