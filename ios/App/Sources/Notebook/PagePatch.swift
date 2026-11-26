import PencilKit
import TaliCore
import UIKit

/// Plain paper under the tiled page layer. A tile shows nothing for the moment it takes to be drawn
/// again (after a change, or the first time it comes into view), and the desk showed through there:
/// squares of the desk's colour over a page being loaded, and a flash under anything see-through
/// that was just picked up. With this under the tiles, such a tile shows blank paper instead. An
/// endless canvas needs none: it already lies on paper.
final class PaperUnderlay: UIView {
  private var rects: [CGRect] = []
  private var colour: UIColor?

  override init(frame: CGRect) {
    super.init(frame: frame)
    isOpaque = false
    backgroundColor = .clear
    isUserInteractionEnabled = false
  }

  required init?(coder: NSCoder) { fatalError() }

  /// The pages (world units) and the paper's own colour.
  func show(_ pages: [CGRect], _ paper: UIColor) {
    if pages == rects, colour.map({ $0.isEqual(paper) }) ?? false { return }
    rects = pages
    colour = paper
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    var sheets = layer.sublayers ?? []
    while sheets.count > pages.count { sheets.removeLast().removeFromSuperlayer() }
    while sheets.count < pages.count {
      let sheet = CALayer()
      layer.addSublayer(sheet)
      sheets.append(sheet)
    }
    for (sheet, page) in zip(sheets, pages) {
      sheet.frame = page
      sheet.backgroundColor = paper.cgColor
    }
    CATransaction.commit()
  }
}

extension NoteCanvas.Coordinator {
  /// The overlay layer that carries picked-up writing (PencilKit draws that; the page draws the rest).
  static let carriedInk = "carried-ink"
  /// The overlay layer that carries picked-up highlighter, which the notebook draws on the page too
  /// (HighlightInk.swift): it goes the moment the page shows it again.
  static let carriedMarks = "carried-marks"
  /// A picture of part of the page is never bigger than this many pixels: drawing more at once would stall the touch.
  private static let patchPixels: CGFloat = 6_000_000
  /// How long a picture of part of the page stays over its tiles: they're drawn again well within this.
  private static let patchStay: TimeInterval = 0.4

  /// Keeps the plain paper under the pages in step with them.
  func layoutUnderlay() {
    guard let canvas, let underlay, let pages else { return }
    let z = canvas.zoomScale
    underlay.layer.anchorPoint = .zero
    underlay.bounds = CGRect(origin: .zero, size: pages.worldSize)
    underlay.transform = CGAffineTransform(scaleX: z, y: z)
    underlay.layer.position = .zero
    underlay.show(paged ? layout.all(pageCount).map { CGRect($0) } : [], parent.settings.paper.paperColor)
  }

  /// The page's tiles under [area] (world units) have just been asked to draw themselves again,
  /// which takes them a moment in which they show nothing: until then a picture of that part of the
  /// page as it now is lies over them, drawn here and now. So when something is picked up or put
  /// down the page under it never blinks, and nothing see-through is seen twice (once on the page
  /// and once in the overlay that carries it), which looked like its opacity changing.
  /// False when the picture wasn't made (it would be too big to draw in one go, or a PDF's page is
  /// being drawn elsewhere just now): the tiles then catch up by themselves as before.
  @discardableResult
  func coverPage(_ area: CGRect?) -> Bool {
    guard let canvas, let pages, let area, canvas.zoomScale > 0 else { return true }
    let z = canvas.zoomScale
    let seen = CGRect(x: canvas.bounds.minX / z, y: canvas.bounds.minY / z, width: canvas.bounds.width / z, height: canvas.bounds.height / z)
    let r = area.insetBy(dx: -6, dy: -6).intersection(seen).intersection(CGRect(origin: .zero, size: pages.worldSize)).integral
    // Nothing of it is on screen: nothing to keep from blinking.
    guard !r.isNull, r.width >= 1, r.height >= 1 else { return true }
    let scale = canvas.traitCollection.displayScale
    let size = CGSize(width: r.width * z, height: r.height * z)
    guard size.width * scale * size.height * scale <= Self.patchPixels else { return false }
    if pages.pages.under.pdfPages.contains(where: { $0.rect.intersects(r) }), !SharpPage.free { return false }
    let format = UIGraphicsImageRendererFormat()
    format.scale = scale
    format.opaque = false
    let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
      let cg = ctx.cgContext
      cg.scaleBy(x: z, y: z)
      cg.translateBy(x: -r.minX, y: -r.minY)
      cg.clip(to: r)
      pages.pages.paint(in: cg, scale: z, pdf: .kept)
    }
    let patch = UIView(frame: CGRect(x: r.minX * z, y: r.minY * z, width: size.width, height: size.height))
    patch.isUserInteractionEnabled = false
    patch.isOpaque = false
    patch.layer.contents = image.cgImage
    patch.layer.contentsScale = scale
    // Over the tiles and any older picture, under the writing and everything else.
    canvas.insertSubview(patch, aboveSubview: patches.last ?? pages)
    patches.append(patch)
    DispatchQueue.main.asyncAfter(deadline: .now() + Self.patchStay) { [weak self, weak patch] in
      guard let patch else { return }
      patch.removeFromSuperview()
      self?.patches.removeAll { $0 === patch }
    }
    return true
  }

  /// The page changes under [area] (nil: anywhere): pictures of how it was there go at once.
  func dropPatches(over area: CGRect? = nil) {
    guard !patches.isEmpty, let canvas else { return }
    let z = canvas.zoomScale
    let gone = patches.filter { patch in area.map { CGRect(x: $0.minX * z, y: $0.minY * z, width: $0.width * z, height: $0.height * z).intersects(patch.frame) } ?? true }
    for patch in gone { patch.removeFromSuperview() }
    patches.removeAll { patch in gone.contains { $0 === patch } }
  }
}
