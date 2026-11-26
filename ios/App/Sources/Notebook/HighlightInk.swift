import PencilKit
import TaliCore
import UIKit

/// The highlighter, drawn by the notebook instead of PencilKit. PencilKit draws its drawing a moment
/// after it's set, and never says exactly when it has: see-through ink handed between it and an
/// overlay (picked up, put down, just written) showed twice for a moment, darker, or not at all, so
/// the highlighter flickered and changed its strength (the owner's videos, 25 Nov 2026). Drawn here,
/// as vector shapes in the same frame as everything else, it's never in two places or none.
/// PencilKit keeps it in its drawing behind the empty mask buried writing uses (InkStack.swift), so
/// the lasso, the eraser and undo work on it as before. It sits over the page and under PencilKit's
/// writing, as Android draws the highlighter under the writing.
final class HighlightView: UIView {
  private struct Entry {
    var stroke: InkStroke
    var dark: Bool
    var layer: CAShapeLayer
  }
  private var entries: [String: Entry] = [:]

  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
    isUserInteractionEnabled = false
    layer.anchorPoint = .zero
  }

  required init?(coder: NSCoder) { fatalError() }

  /// The outline [s] is filled with, kept to [mask] (its page) as `pkStroke` keeps it.
  static func outline(_ s: InkStroke, mask: UIBezierPath?) -> CGPath? {
    guard let look = InkLook(pen: s.pen, line: s.line, square: s.square) else { return nil }
    let at = s.drawnPoints.map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) }
    guard !at.isEmpty else { return nil }
    var shape = look.outline(size: CGFloat(s.size), points: at)
    if let mask, !mask.bounds.contains(shape.boundingBoxOfPath) { shape = shape.intersection(mask.cgPath) }
    return shape
  }

  /// The colour PencilKit would draw [s] in: on dark paper, its own dark form of the colour.
  static func colour(_ s: InkStroke, dark: Bool) -> CGColor {
    let c = UIColor(argb: s.color)
    return (dark ? PKInkingTool.convertColor(c, from: .light, to: .dark) : c).cgColor
  }

  /// Shows [strokes], bottom first, changing only what changed (in this frame, with no animation).
  func show(_ strokes: [InkStroke], dark: Bool, mask: (InkStroke) -> UIBezierPath?) {
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    var next: [String: Entry] = [:]
    var order: [CALayer] = []
    for s in strokes {
      var key = s.id
      var n = 1
      while next[key] != nil {
        key = "\(s.id)#\(n)"
        n += 1
      }
      var e: Entry
      if let had = entries[key], had.stroke == s, had.dark == dark {
        e = had
      } else {
        let l = entries[key]?.layer ?? CAShapeLayer()
        l.path = Self.outline(s, mask: mask(s))
        l.fillColor = Self.colour(s, dark: dark)
        l.contentsScale = window?.screen.scale ?? traitCollection.displayScale
        e = Entry(stroke: s, dark: dark, layer: l)
      }
      e.stroke = s
      next[key] = e
      order.append(e.layer)
    }
    let now = layer.sublayers ?? []
    if now.count != order.count || zip(now, order).contains(where: { $0 !== $1 }) { layer.sublayers = order }
    entries = next
    CATransaction.commit()
  }
}

/// Highlighter strokes in drawings set from here that aren't (yet) in `stored`, by their key in the
/// drawing: an eraser's pieces mid-sweep, a selection being resized.
struct HighlightInk {
  var byKey: [NoteCanvas.Coordinator.StrokeKey: InkStroke] = [:]
}

extension NoteCanvas.Coordinator {
  /// Strokes the notebook draws itself (see HighlightView).
  static func drawnByNotebook(_ s: InkStroke) -> Bool { s.pen.isHighlighter && InkLook(pen: s.pen, line: s.line, square: s.square) != nil }

  /// [s] as it goes into the canvas's drawing: kept to its page, or, for the highlighter, hidden
  /// (the notebook draws it).
  func canvasStroke(_ s: InkStroke) -> PKStroke {
    guard Self.drawnByNotebook(s) else { return s.pkStroke(mask: mask(for: s)) }
    var pk = s.pkStroke(mask: nil)
    pk.mask = Self.hidden
    let key = StrokeKey(pk)
    highlightInk.byKey[key] = s
    if highlightInk.byKey.count > 4000 { highlightInk.byKey = [key: s] }
    return pk
  }

  /// The stored stroke behind [pk] in the canvas's drawing.
  private func known() -> [StrokeKey: InkStroke] {
    var out = highlightInk.byKey
    for (k, s) in stored { out[k] = s }
    return out
  }

  /// [d] with the highlighter shown, for pictures of the page (thumbnails, the pages overview, a
  /// screenshot, the fill's walls): what's hidden only because the notebook draws it.
  func visibleDrawing(_ d: PKDrawing) -> PKDrawing {
    guard d.strokes.contains(where: Self.isHidden) else { return d }
    let table = known()
    let buried = Set(pages?.pages.under.buried.map(\.stroke.id) ?? [])
    return PKDrawing(strokes: d.strokes.map { pk in
      guard Self.isHidden(pk), let s = table[StrokeKey(pk)], Self.drawnByNotebook(s), !buried.contains(s.id) else { return pk }
      var shown = s.pkStroke(mask: mask(for: s))
      shown.transform = pk.transform
      return shown
    })
  }

  /// Shows the highlighter in [d] (the canvas's drawing when not given) that the page doesn't draw.
  func refreshHighlights(_ d: PKDrawing? = nil) {
    guard let canvas, let view = highlights else { return }
    let strokes = (d ?? canvas.drawing).strokes
    let table = known()
    let buried = Set(pages?.pages.under.buried.map(\.stroke.id) ?? [])
    var shown: [InkStroke] = []
    for pk in strokes where Self.isHidden(pk) {
      guard let s = table[StrokeKey(pk)], Self.drawnByNotebook(s), !buried.contains(s.id) else { continue }
      shown.append(pk.transform == .identity ? s : s.transformed(Affine(a: Float(pk.transform.a), b: Float(pk.transform.b), c: Float(pk.transform.c), d: Float(pk.transform.d), tx: Float(pk.transform.tx), ty: Float(pk.transform.ty))))
    }
    view.show(shown, dark: parent.settings.dark) { self.mask(for: $0) }
  }
}
