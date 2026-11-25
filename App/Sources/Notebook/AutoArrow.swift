import PencilKit
import TaliCore
import UIKit

/// Auto arrow (Android 1.0.33; a switch at the top of the Shapes panel, off unless turned on): an
/// arrow drawn by hand with a pen becomes a clean arrow shape as the pen lifts, with no hold. One
/// stroke (the line, then its head without lifting) or two (a straight line, then its head as the
/// very next stroke, with the same pen and colour, begun within three seconds). The rules are
/// `TaliCore.AutoArrow`'s. What was drawn is kept first, as its own undo steps; the swap for the
/// arrow is one more, so one Undo brings back the arrow exactly as drawn.
struct AutoArrowState {
  /// The stroke before this one when it was a straight line: its ends, its pen and when the pen lifted.
  var line: (id: String, a: Pt, b: Pt, pen: PenSlot, lifted: Date)?
}

/// The head length of the last auto arrow, kept while the app runs: arrows drawn alike come out alike.
@MainActor
enum AutoArrowMemory {
  static var lastHead: Float?
}

extension NoteCanvas.Coordinator {
  /// Any other stroke, an undo or a redo: the line that was waiting for its head isn't any more.
  func forgetArrowLine() { arrows.line = nil }

  /// The stroke [s] was just kept ([raw]: its points as the pen gave them; [began] and [lifted]:
  /// when the pen came down and went up). True when it, or it and the straight line before it,
  /// became an arrow.
  func autoArrow(after s: InkStroke, pen: PenSlot, raw: [Pt], began: Date, lifted: Date) -> Bool {
    let waiting = arrows.line
    arrows.line = nil
    guard ShapeSettings.autoArrow, !parent.viewOnly, !pen.type.isHighlighter else { return false }
    var onLine: AutoArrow.Found?
    var drawn: [PageItem] = [.ink(s)]
    if let line = waiting, line.pen.id == pen.id, line.pen.type == pen.type, line.pen.color == pen.color,
      began.timeIntervalSince(line.lifted) <= AutoArrow.wait, let first = stored.first(where: { $0.stroke.id == line.id })?.stroke,
      let head = AutoArrow.twoStrokes(a: line.a, b: line.b, head: raw)
    {
      onLine = head
      drawn = [.ink(first), .ink(s)]
    }
    guard let found = onLine ?? AutoArrow.oneStroke(raw) else {
      if let ends = AutoArrow.straight(raw) { arrows.line = (s.id, ends.a, ends.b, pen, lifted) }
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
    commit(remove: drawn, add: [.shape(shape)])
    // The clean arrow is up in the frame the hand-drawn ink leaves: a picture of the page with the
    // arrow on it lies over its tiles until they've drawn it, or failing that the arrow itself is
    // drawn over the page for that moment. Nothing blinks in between.
    if !coverPage(shape.geometry.bounds(shape.size).map { CGRect($0) }) { showArrow(shape) }
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
