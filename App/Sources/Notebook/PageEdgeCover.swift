import PencilKit
import TaliCore
import UIKit

/// With Write between pages off, a stroke belongs to the page it began on and nothing of it shows
/// past that page's edge, while it's being written too (Android's split strokes): it's kept whole,
/// so moving it shows the rest. PencilKit draws the live stroke itself and can't hide part of it,
/// so the moment the pen leaves the page, everything around the page is covered by a picture of
/// itself as it was, until the stroke is in, drawn with its page's mask.
struct PageEdgeCoverState {
  /// The page the stroke in hand began on (world units), while it's cut off at its edges.
  var page: CGRect?
  var cover: UIView?
  var ticket = 0
}

extension NoteCanvas.Coordinator {
  /// A stroke began at [p] (world units): its page, if it is kept to one.
  func edgeCoverBegan(at p: CGPoint) {
    removeEdgeCover(now: true)
    edgeCover.page = pageClip(startingAt: Float(p.y)).flatMap { paged && !parent.settings.between ? $0 : nil }
  }

  /// The pen is at [p] (world units): leaving its page, the area around the page is covered.
  func edgeCoverMoved(to p: CGPoint) {
    guard let page = edgeCover.page, edgeCover.cover == nil, let canvas else { return }
    let reach = CGFloat(currentPen.size)
    if page.insetBy(dx: reach, dy: reach).contains(p) { return }
    guard let host = canvas.superview, let shot = canvas.snapshotView(afterScreenUpdates: false) else { return }
    shot.frame = canvas.frame
    shot.isUserInteractionEnabled = false
    // Covers everything on screen but the stroke's own page.
    let z = canvas.zoomScale
    let onScreen = CGRect(x: page.minX * z - canvas.bounds.minX, y: page.minY * z - canvas.bounds.minY, width: page.width * z, height: page.height * z)
    let around = UIBezierPath(rect: shot.bounds)
    around.append(UIBezierPath(rect: onScreen))
    let m = CAShapeLayer()
    m.path = around.cgPath
    m.fillRule = .evenOdd
    shot.layer.mask = m
    host.insertSubview(shot, aboveSubview: canvas)
    edgeCover.cover = shot
  }

  /// The stroke is over: the cover goes once PencilKit has drawn the stroke with its mask.
  func edgeCoverEnded() {
    edgeCover.page = nil
    removeEdgeCover(now: false)
  }

  private func removeEdgeCover(now: Bool) {
    edgeCover.ticket += 1
    guard let cover = edgeCover.cover else { return }
    if now {
      cover.removeFromSuperview()
      edgeCover.cover = nil
      return
    }
    let ticket = edgeCover.ticket
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [weak self] in
      guard let self, self.edgeCover.ticket == ticket else { return }
      cover.removeFromSuperview()
      if self.edgeCover.cover === cover { self.edgeCover.cover = nil }
    }
  }
}
