import PencilKit
import TaliCore
import UIKit

/// Scribble to delete (off unless turned on in the eraser's settings): with a pen, writing or shapes
/// scrubbed over back and forth leave the page with the scribble, in one undo step. What it would
/// take is what the eraser takes (its Erases filter, never locked things, typed text or pictures),
/// but only what lies under the scribble (`ScratchOut.takes`). A scribble over nothing stays as ink.
/// The scribble and what it took go at once, as on Android.
extension NoteCanvas.Coordinator {
  /// The stroke just finished: true when it was a scribble that took things off the page.
  func scribbleDelete() -> Bool {
    guard parent.eraser.scribbleDelete, !parent.viewOnly, case .pen = parent.tool, !currentPen.type.isHighlighter,
      let area = ScratchOut.area(hold.points, zoom: zoom), let reach = Rect.around(area)
    else { return false }
    let since = hold.began.addingTimeInterval(-0.05)
    let affects = parent.eraser.affects
    var gone: [String] = []
    for (key, s) in stored where key.created < since.timeIntervalSinceReferenceDate {
      guard affects.hits(s.pen), s.bounds.intersects(reach) else { continue }
      if ScratchOut.takes([s.inputs.points.map { Pt($0.x, $0.y) }], area) { gone.append(s.id) }
    }
    for shape in shapes where !shape.locked {
      guard affects.hits(PenType(rawValue: shape.pen) ?? .FINELINER), let box = shape.geometry.bounds(shape.size), box.intersects(reach) else { continue }
      if ScratchOut.takes(shape.geometry.paths(shape.size), area) { gone.append(shape.id) }
    }
    guard !gone.isEmpty else { return false }
    // The scribble leaves the page without an undo step of its own; what it covered goes in one,
    // with the fills it outlines, as a selection's Delete takes them.
    dropInk(since: since)
    let ids = Set(withFills(gone, pickLocked: false))
    commit(remove: pageItems.filter { ids.contains($0.id) }, add: [])
    UIImpactFeedbackGenerator(style: .light).impactOccurred()
    return true
  }
}
