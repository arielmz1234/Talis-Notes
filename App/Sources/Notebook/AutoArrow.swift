import PencilKit
import TaliCore
import UIKit

/// Auto arrow (Android 1.0.33, `NoteCanvasView.autoArrow`; a switch at the top of the Shapes
/// panel, off unless turned on): an arrow drawn by hand with a pen becomes a clean arrow shape as
/// the pen lifts, with no hold. One stroke (the line, then its head without lifting) or two (a
/// straight line, then its head as the very next stroke, with the same pen and colour, lifted less
/// than three seconds after the line). The rules are `TaliCore.AutoArrow`'s. What was drawn is kept
/// first, as its own undo steps; the swap for the arrow is one more, so one Undo brings back the
/// arrow exactly as drawn. The arrow is then picked up softly, as after hold-to-shape.
struct AutoArrowState {
  /// The stroke before this one when it was a straight line: its ends, its pen and when the pen lifted.
  var line: (id: String, a: Pt, b: Pt, pen: PenSlot, lifted: Date)?
}

/// The head length of the last auto arrow, kept while the app runs (any notebook): arrows drawn alike come out alike.
@MainActor
enum AutoArrowMemory {
  static var lastHead: Float?
}

extension NoteCanvas.Coordinator {
  /// Any other stroke, an undo or a redo: the line that was waiting for its head isn't any more.
  func forgetArrowLine() { arrows.line = nil }

  /// The stroke [s] was just kept, and the pen lifted from it at [lifted]. True when it, or it and
  /// the straight line before it, became an arrow. As on Android, the stroke is looked at as it was
  /// kept (smoothed), one stroke first, and then with the line before it.
  func autoArrow(after s: InkStroke, pen: PenSlot, lifted: Date) -> Bool {
    let waiting = arrows.line
    arrows.line = nil
    let pts = s.inputs.points.map { Pt($0.x, $0.y) }
    guard ShapeSettings.autoArrow, !parent.viewOnly, !pen.type.isHighlighter, pts.count >= 2 else { return false }
    var drawn: [PageItem] = [.ink(s)]
    var found = AutoArrow.oneStroke(pts)
    if found == nil, let line = waiting, line.pen.id == pen.id, line.pen.color == pen.color,
      lifted.timeIntervalSince(line.lifted) < AutoArrow.wait, let first = stored.first(where: { $0.stroke.id == line.id })?.stroke,
      let head = AutoArrow.twoStrokes(a: line.a, b: line.b, head: pts)
    {
      found = head
      drawn = [.ink(first), .ink(s)]
    }
    guard let found else {
      if let ends = AutoArrow.straight(pts) { arrows.line = (s.id, ends.a, ends.b, pen, lifted) }
      return false
    }
    let made = AutoArrow.shape(found, last: AutoArrowMemory.lastHead)
    AutoArrowMemory.lastHead = made.head
    // The pen's colour, width and line style, as a stroke held into a shape takes them.
    var shape = ShapeRow(geometry: made.geometry, pen: pen.type.rawValue, color: pen.color, size: pen.size, line: pen.lineStyle.rawValue)
    shape.id = newId()
    shape.z = topZ + 1
    shapeZ = max(shapeZ, shape.z)
    forgetCircle()
    // An undo step of its own: the stroke was kept a moment ago in this same turn of the run loop,
    // and the undo manager would make one step of the two, so Undo took the arrow and the stroke together.
    if let undo = canvas?.undoManager, undo.groupingLevel > 0 {
      undo.endUndoGrouping()
      undo.beginUndoGrouping()
    }
    commit(remove: drawn, add: [.shape(shape)])
    // The clean arrow is up in the frame the hand-drawn ink leaves: a picture of the page with the
    // arrow on it lies over its tiles until they've drawn it, or failing that the arrow itself is
    // drawn over the page for that moment. Nothing blinks in between.
    if !coverPage(shape.geometry.bounds(shape.size).map { CGRect($0) }) { showArrow(shape) }
    // Picked up softly, as after hold-to-shape: its dots are there, and the pen writes on through it.
    pickUpShape(shape, soft: true)
    UINotificationFeedbackGenerator().notificationOccurred(.success)
    return true
  }

  /// The arrow drawn over the page, taken away once the page's tiles have drawn the kept one.
  private func showArrow(_ shape: ShapeRow) {
    guard let canvas else { return }
    let z = canvas.zoomScale
    let path = CGMutablePath()
    for line in shape.strokes where line.count > 1 {
      path.addLines(between: line.map { CGPoint(x: CGFloat($0.x) * z, y: CGFloat($0.y) * z) })
    }
    let dark = pages?.pages.paper.dark ?? false
    let layer = CAShapeLayer()
    layer.fillColor = UIColor.clear.cgColor
    layer.lineCap = .round
    layer.lineJoin = .round
    layer.zPosition = 9
    layer.path = path
    layer.strokeColor = UIColor(argb: dark ? DarkInk.onDarkPaper(shape.inkColor) : shape.inkColor).cgColor
    layer.lineWidth = CGFloat(shape.size) * z
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    canvas.layer.addSublayer(layer)
    CATransaction.commit()
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
      CATransaction.begin()
      CATransaction.setDisableActions(true)
      layer.removeFromSuperlayer()
      CATransaction.commit()
    }
  }
}
