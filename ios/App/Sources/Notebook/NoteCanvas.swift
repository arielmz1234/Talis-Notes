import ImageIO
import PDFKit
import PencilKit
import SwiftUI
import TaliCore
import UIKit

/// The tool in use (the parts ported so far).
enum Tool: Equatable {
  case pen(String)  // a PenSlot id
  case eraser
  case lasso
  case shape
  case fill
  /// Points at things with a glowing trail that fades away; nothing is kept.
  case laser
}

/// What the page layer draws besides paper: shapes and pictures from the notebook (pictures first,
/// then shapes, all under the writing for now).
struct UnderItems: @unchecked Sendable {
  var shapes: [ShapeRow] = []
  var pictures: [PlacedPicture] = []
  /// Pages of an imported PDF, drawn from the PDF itself (sharp at any zoom) instead of their pictures.
  var pdfPages: [(id: String, rect: CGRect, page: SharpPage)] = []
  /// Typed text boxes (drawn as real text, sharp at any zoom).
  var texts: [TextRow] = []
  /// Filled areas: pictures behind the writing and the shapes (see FillTool.swift).
  var fills: [FillPicture] = []
  /// Locked writing, kept out of PencilKit's drawing (see PageItems.swift), and how it's drawn.
  private(set) var lockedInk: [InkStroke] = []
  private(set) var lockedStrokes: [PKStroke] = []
  /// Writing under a shape, text box or picture in front (sent back, or written before it): drawn
  /// here in its place in the stack, while PencilKit, which draws over the page, hides its own (see InkStack.swift).
  var buried: [(stroke: InkStroke, pk: PKStroke)] = []

  mutating func setLockedInk(_ strokes: [InkStroke], mask: (InkStroke) -> UIBezierPath?) {
    lockedInk = strokes
    lockedStrokes = strokes.map { $0.pkStroke(mask: mask($0)) }
  }
}

/// Paper, pictures and shapes for the page column, drawn in tiles off the main thread so they stay
/// sharp at any zoom. Everything it reads is set from the main thread before it's asked to draw.
final class PagesLayer: CATiledLayer, @unchecked Sendable {
  nonisolated(unsafe) var layout = Pages.portrait
  nonisolated(unsafe) var count = 1
  nonisolated(unsafe) var paper = PaperSpec(style: .GRID)
  /// A picture paper's image, once loaded.
  nonisolated(unsafe) var picture: CGImage?
  nonisolated(unsafe) var under = UnderItems()
  /// Off when the paper is already painted (a PDF export painting it over a whole long page).
  nonisolated(unsafe) var drawsPaper = true
  /// An endless canvas's size so far (paper all over it); nil for pages.
  nonisolated(unsafe) var endless: CGSize?

  override class func fadeDuration() -> CFTimeInterval { 0.08 }

  var worldSize: CGSize { endless ?? CGSize(width: CGFloat(layout.width), height: CGFloat(layout.rect(max(count, 1) - 1).bottom)) }

  // A tile: a PDF's page is drawn once as a picture and every tile of it is cut from that.
  override func draw(in cg: CGContext) { paint(in: cg, pdf: .picture) }

  /// Draws what's under [cg]'s clip at [scale] points (or pixels) per world unit; on a tile that's
  /// read from its transform. [print] draws the paper as printed paper (the PDF). [pdf]: how an
  /// imported PDF's pages are drawn (see `SharpPage.Way`).
  func paint(in cg: CGContext, scale given: CGFloat? = nil, print: Bool = false, pdf: SharpPage.Way = .lines) {
    let rect = cg.boundingBoxOfClipPath
    let scale = given ?? max(cg.ctm.a / max(contentsScale, 1), 0.01)
    if drawsPaper, let endless {
      let area = rect.intersection(CGRect(origin: .zero, size: endless))
      if !area.isNull { PaperPainter.draw(paper, area: area, endless: true, in: cg, scale: scale, print: print, picture: picture) }
    } else if drawsPaper {
      for i in 0..<max(count, 1) {
        let page = CGRect(layout.rect(i))
        guard page.intersects(rect) else { continue }
        PaperPainter.draw(paper, area: page, in: cg, scale: scale, print: print, picture: picture)
      }
    }
    for p in under.pdfPages where p.rect.intersects(rect) { p.page.draw(in: p.rect, cg, pdf) }
    func draw(_ pic: PlacedPicture) {
      guard pic.rect.intersects(rect) else { return }
      cg.saveGState()
      cg.translateBy(x: pic.rect.minX, y: pic.rect.maxY)
      cg.scaleBy(x: 1, y: -1)
      cg.interpolationQuality = .high
      cg.draw(pic.image, in: CGRect(origin: .zero, size: pic.rect.size))
      cg.restoreGState()
    }
    // Pictures sit behind everything like paper until they're brought to the front; then they're
    // drawn among the shapes and text by their place in the stack (Android's `drawAll`).
    // In their own order in the stack, so Send to back puts one behind another picture too.
    for pic in under.pictures.filter({ !$0.row.front }).sorted(by: { $0.row.z < $1.row.z }) { draw(pic) }
    for f in under.fills where CGRect(f.row.rect).intersects(rect) { f.draw(in: cg) }
    cg.setLineCap(.round)
    cg.setLineJoin(.round)
    func draw(_ s: ShapeRow) {
      let path = CGMutablePath()
      for line in s.strokes { path.addLines(between: line.map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) }) }
      guard path.boundingBox.insetBy(dx: -CGFloat(s.size) * 2, dy: -CGFloat(s.size) * 2).intersects(rect) else { return }
      if let fill = s.fill, s.geometry.closed, let outline = s.geometry.paths(s.size).first, outline.count > 2 {
        let f = CGMutablePath()
        f.addLines(between: outline.map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) })
        f.closeSubpath()
        cg.addPath(f)
        cg.setFillColor(UIColor(argb: fill).cgColor)
        cg.fillPath()
      }
      cg.addPath(path)
      cg.setStrokeColor(UIColor(argb: paper.dark ? DarkInk.onDarkPaper(s.inkColor) : s.inkColor).cgColor)
      cg.setLineWidth(CGFloat(s.size))
      cg.strokePath()
    }
    func draw(_ text: TextRow) {
      var t = text
      let box = TextLook.frame(t)
      guard box.intersects(rect) else { return }
      if paper.dark { t.color = DarkInk.onDarkPaper(t.color) }
      UIGraphicsPushContext(cg)
      TextLook.attributed(t).draw(with: CGRect(x: box.minX, y: box.minY, width: box.width, height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin, context: nil)
      UIGraphicsPopContext()
    }
    // Everything over the pictures behind goes by its place in the stack (Android's `drawAll`); equal
    // places keep shapes under text under pictures under writing, each in its own order.
    let front = under.pictures.filter(\.row.front)
    var ink: [(z: Int64, pk: PKStroke)] = zip(under.lockedInk, under.lockedStrokes).map { ($0.z, $1) }
    ink += under.buried.map { ($0.stroke.z, $0.pk) }
    ink = ink.filter { $0.pk.renderBounds.intersects(rect) }
    var stack: [(z: Int64, kind: Int, index: Int)] = under.shapes.indices.map { (under.shapes[$0].z, 0, $0) }
    stack += under.texts.indices.map { (under.texts[$0].z, 1, $0) }
    stack += front.indices.map { (front[$0].row.z, 2, $0) }
    stack += ink.indices.map { (ink[$0].z, 3, $0) }
    // PencilKit draws ink for the interface style in force: the paper's own, as on the canvas.
    let style = UITraitCollection(userInterfaceStyle: paper.dark ? .dark : .light)
    let inkScale = max(abs(cg.ctm.a), 0.05)
    // Writing next to other writing in the stack is drawn in one go.
    var run: [PKStroke] = []
    func flush() {
      guard !run.isEmpty else { return }
      let drawing = PKDrawing(strokes: run)
      run = []
      let area = drawing.bounds.intersection(rect)
      guard !area.isNull, !area.isEmpty else { return }
      var image: UIImage?
      style.performAsCurrent { image = drawing.image(from: area, scale: inkScale) }
      UIGraphicsPushContext(cg)
      image?.draw(in: area)
      UIGraphicsPopContext()
    }
    for item in stack.sorted(by: { ($0.z, $0.kind, $0.index) < ($1.z, $1.kind, $1.index) }) {
      if item.kind == 3 {
        run.append(ink[item.index].pk)
        continue
      }
      flush()
      switch item.kind {
      case 0: draw(under.shapes[item.index])
      case 1: draw(under.texts[item.index])
      default: draw(front[item.index])
      }
    }
    flush()
  }
}

/// How a text box looks (Android's `TextItem.layoutFor`): the sans-serif system font at its size, lines
/// 1.3 times the size apart, each paragraph running the way its first letters do (Hebrew from the right).
enum TextLook {
  static func attributed(_ t: TextRow) -> NSAttributedString {
    let s = NSMutableAttributedString(string: t.text, attributes: attributes(size: CGFloat(t.fontSize), color: t.color))
    setDirections(s)
    return s
  }

  static func attributes(size: CGFloat, color: Int32) -> [NSAttributedString.Key: Any] {
    [.font: UIFont.systemFont(ofSize: size), .foregroundColor: UIColor(argb: color), .paragraphStyle: paragraph(size: size, rightToLeft: false)]
  }

  /// Each paragraph runs the way its first letters do (Android's FIRSTSTRONG_LTR), not the way the
  /// app's language does: English stays on the left in Hebrew, Hebrew on the right in English.
  static func setDirections(_ s: NSMutableAttributedString) {
    let text = s.string
    guard let size = (s.length > 0 ? s.attribute(.font, at: 0, effectiveRange: nil) as? UIFont : nil)?.pointSize else { return }
    for p in TextDirection.paragraphs(text) {
      let r = NSRange(p.range.lowerBound..<(p.range.upperBound < text.endIndex ? text.index(after: p.range.upperBound) : p.range.upperBound), in: text)
      s.addAttribute(.paragraphStyle, value: paragraph(size: size, rightToLeft: p.rightToLeft), range: r)
    }
  }

  private static func paragraph(size: CGFloat, rightToLeft: Bool) -> NSParagraphStyle {
    let p = NSMutableParagraphStyle()
    p.minimumLineHeight = size * CGFloat(TextRow.line)
    p.maximumLineHeight = size * CGFloat(TextRow.line)
    p.baseWritingDirection = rightToLeft ? .rightToLeft : .leftToRight
    p.alignment = rightToLeft ? .right : .left
    return p
  }

  /// How tall the laid-out text is in world units.
  static func textHeight(_ t: TextRow) -> Float {
    Float(ceil(attributed(t).boundingRect(with: CGSize(width: CGFloat(t.width), height: .greatestFiniteMagnitude), options: .usesLineFragmentOrigin, context: nil).height))
  }

  /// The box it fills: its width, and the taller of the text, a line and its own height.
  static func frame(_ t: TextRow) -> CGRect {
    let h = max(textHeight(t), t.fontSize * TextRow.line, t.height)
    return CGRect(x: CGFloat(t.x), y: CGFloat(t.y), width: CGFloat(t.width), height: CGFloat(h))
  }
}

/// PencilKit's canvas, telling the notebook when its size changes (so the page is fitted as soon as
/// it has one) and showing VoiceOver the text box being typed in, which PencilKit hides like all of its
/// subviews.
final class NoteCanvasView: PKCanvasView {
  /// The canvas got a new size (it appeared, the notebook slid in, the device turned).
  var onResize: () -> Void = {}
  private var laidOutSize: CGSize = .zero
  /// Whether the scroll view's own pinch may zoom (pages); an endless canvas zooms with its own
  /// pinch (Endless.swift). Turning the scroll view's pinch off isn't enough: the scroll view turns it
  /// back on whenever its zoom limits are set, and then it zoomed straight past the resistance.
  var ownPinchAllowed: () -> Bool = { true }

  override func gestureRecognizerShouldBegin(_ g: UIGestureRecognizer) -> Bool {
    if g === pinchGestureRecognizer, !ownPinchAllowed() { return false }
    return super.gestureRecognizerShouldBegin(g)
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    if bounds.size != laidOutSize {
      laidOutSize = bounds.size
      onResize()
    }
    quietSystemMenus()
  }

  /// The notebook's own hold (Paste) and menus only. PencilKit's canvas has a hold of its own that
  /// brings up the system's "Select All" and "Insert Space" menu, which covered the app's Paste
  /// bubble and couldn't be used (the owner, 25 Nov 2026). Its holds and menus are switched off,
  /// here and in its own views, as they come.
  var ownHolds: [UIGestureRecognizer] = []

  private func quietSystemMenus() {
    func quiet(_ v: UIView) {
      for i in v.interactions where i is UIEditMenuInteraction || i is UIContextMenuInteraction { v.removeInteraction(i) }
      for g in v.gestureRecognizers ?? [] where g is UILongPressGestureRecognizer && g.isEnabled && !ownHolds.contains(where: { $0 === g }) { g.isEnabled = false }
    }
    quiet(self)
    for v in subviews where !(v is UITextView) { quiet(v) }
  }

  override func canPerformAction(_ action: Selector, withSender sender: Any?) -> Bool { false }

  var typing: [UIView] = [] {
    didSet { UIAccessibility.post(notification: .layoutChanged, argument: typing.first) }
  }

  override var accessibilityElements: [Any]? {
    get { typing.isEmpty ? super.accessibilityElements : typing }
    set { super.accessibilityElements = newValue }
  }

  override var isAccessibilityElement: Bool {
    get { typing.isEmpty && super.isAccessibilityElement }
    set { super.isAccessibilityElement = newValue }
  }
}

/// The page column under the writing.
final class PagesView: UIView {
  override class var layerClass: AnyClass { PagesLayer.self }
  var pages: PagesLayer { layer as! PagesLayer }

  override init(frame: CGRect) {
    super.init(frame: frame)
    // Sharp from 16× down to ⅛×, so zooming all the way out uses tiles already drawn at a level near it.
    pages.levelsOfDetail = 8
    pages.levelsOfDetailBias = 4
    pages.tileSize = CGSize(width: 512, height: 512)
    isOpaque = false
    backgroundColor = .clear
    isUserInteractionEnabled = false
  }

  required init?(coder: NSCoder) { fatalError() }

  var worldSize: CGSize { pages.worldSize }
}

/// The notebook's canvas: PencilKit draws the live stroke with Apple Pencil's low latency, and every
/// finished stroke is stored the Android way (`InkCodec`) so backups move between platforms. The
/// drawing is kept in world units (zoom 1 = 100%); pages sit underneath.
struct NoteCanvas: UIViewRepresentable {
  let notebook: NotebookMeta
  let db: NotesDb
  var tool: Tool
  var pens: [PenSlot]
  var viewOnly = false
  /// How far down the toolbars reach over the canvas (Android's `topClearance`: two rows when narrow).
  var topClearance: CGFloat = 104
  var settings: CanvasSettings
  /// Lets the notebook screen ask for page pictures.
  var bridge: CanvasBridge? = nil
  var eraser = EraserSettings()
  /// The shape the Shapes tool draws.
  var shape: ShapeSource = .kind(.LINE)
  var onUndoState: (Bool, Bool) -> Void
  var onPageCount: (Int) -> Void
  /// Where what's picked up is on screen, or nil; for the selection menu.
  var onSelection: (SelectionInfo?) -> Void = { _ in }
  /// A short message at the bottom ("Copied — hold anywhere to paste it").
  var onHint: (String) -> Void = { _ in }
  /// The text box being typed in, for the bar above it, or nil.
  var onTextBar: (TextBarInfo?) -> Void = { _ in }
  /// A hold on the page offering Paste there (window coordinates), or nil.
  var onPasteOffer: (CGPoint?) -> Void = { _ in }
  /// Apple Pencil was double-tapped or squeezed, with what the system's settings say it should do.
  var onPencil: (PencilGesture.Preference) -> Void = { _ in }
  /// A picture just taken with the screenshot lasso, to save, share or copy.
  var onScreenshot: (URL) -> Void = { _ in }
  /// The page in the middle of the screen and the zoom, as they change (for the page and zoom chips).
  var onView: (Int, CGFloat) -> Void = { _, _ in }
  @Binding var command: CanvasCommand?

  func makeCoordinator() -> Coordinator { Coordinator(self) }

  func makeUIView(context: Context) -> PKCanvasView {
    let c = context.coordinator
    let v = NoteCanvasView()
    v.delegate = c
    v.drawingPolicy = .pencilOnly
    if ProcessInfo.processInfo.arguments.contains("-uitest") { v.drawingPolicy = .anyInput }
    v.backgroundColor = .clear
    v.isOpaque = false
    // PencilKit lightens dark ink in dark mode; the page is white unless the notebook has dark paper,
    // so the canvas keeps the paper's own style (ink stays as written, as on Android).
    v.overrideUserInterfaceStyle = settings.dark ? .dark : .light
    // Tap 2 fingers to undo, 3 to redo (Android's smart undo), when it's on.
    for n in [2, 3] {
      let tap = UITapGestureRecognizer(target: c, action: n == 2 ? #selector(Coordinator.twoFingerTap) : #selector(Coordinator.threeFingerTap))
      tap.numberOfTouchesRequired = n
      tap.cancelsTouchesInView = false
      v.addGestureRecognizer(tap)
    }
    c.applied = settings
    c.installSelection(on: v)
    c.installHoldToShape(on: v)
    c.installEraser(on: v)
    c.installShapeTool(on: v)
    c.installFillTool(on: v)
    c.installTextBoxes(on: v)
    c.installOwnInk(on: v)
    c.installPencilGestures(on: v)
    c.installEndlessPinch(on: v)
    PencilSeen.changed = { [weak c] in c?.applyTool() }
    bridge?.coordinator = c
    v.minimumZoomScale = Coordinator.pagesLeastZoom
    v.maximumZoomScale = 6
    v.showsVerticalScrollIndicator = false
    v.showsHorizontalScrollIndicator = false
    v.contentInsetAdjustmentBehavior = .never
    v.alwaysBounceVertical = true
    let pages = PagesView(frame: .zero)
    pages.pages.layout = notebook.pageLayout
    pages.pages.paper = settings.paper
    pages.pages.picture = settings.paper.picture
    v.insertSubview(pages, at: 0)
    let paper = PaperUnderlay(frame: .zero)
    v.insertSubview(paper, at: 0)
    c.canvas = v
    c.pages = pages
    c.underlay = paper
    c.installPageChrome(on: v, above: pages)
    let marks = HighlightView(frame: .zero)
    v.insertSubview(marks, aboveSubview: pages)
    c.highlights = marks
    v.onResize = { [weak c] in
      c?.updateContent()
      c?.drawChrome()
    }
    c.load()
    return v
  }

  func updateUIView(_ v: PKCanvasView, context: Context) {
    let c = context.coordinator
    c.parent = self
    c.applySettings()
    c.applyTool()
    if let cmd = command {
      DispatchQueue.main.async {
        // Once only: the view can be updated again before this runs (two undos came of one tap), so
        // the command is taken before it's carried out.
        guard command == cmd else { return }
        command = nil
        c.run(cmd)
      }
    }
  }

  static func dismantleUIView(_ v: PKCanvasView, coordinator: Coordinator) {
    coordinator.saveNow()
    coordinator.history?.removeAllActions()
  }

  @MainActor
  final class Coordinator: NSObject, PKCanvasViewDelegate {
    var parent: NoteCanvas
    weak var canvas: PKCanvasView?
    weak var pages: PagesView?
    /// The highlighter, drawn here rather than by PencilKit (see HighlightInk.swift).
    weak var highlights: HighlightView?
    var highlightInk = HighlightInk()
    /// Plain paper under the tiles, and pictures of parts of the page lying over tiles that are
    /// being drawn again (see PagePatch.swift).
    weak var underlay: PaperUnderlay?
    var patches: [UIView] = []
    /// The endless canvas's own pinch (see Endless.swift).
    var endlessPinch = EndlessPinchState()
    /// What's stored, in drawing order, with the key that recognises each stroke in the PKDrawing.
    var stored: [(key: StrokeKey, stroke: InkStroke)] = []
    var pageCount = 1
    /// The highest z among shapes, so new items go on top of them.
    var shapeZ: Int64 = 0
    /// On while the canvas's drawing is set from here, so its change isn't taken for her writing.
    var loading = false
    /// The drawing last set from here (`setDrawing`). PencilKit can report a drawing set from code
    /// after `loading` is off again (seen on CI's iOS): taken for writing, a selection's live move
    /// was stored part way and its outline stayed behind while the fill went on.
    private var ownDrawing: PKDrawing?

    /// Sets the canvas's drawing from here (inside `loading`), remembering it so PencilKit's report
    /// of it, however late, isn't taken for her writing.
    func setDrawing(_ d: PKDrawing) {
      guard let canvas else { return }
      canvas.drawing = d
      ownDrawing = canvas.drawing
      refreshHighlights(d)
    }
    /// The window's undo manager outlives this screen, so its steps are dropped on leaving: kept,
    /// Undo stayed lit after reopening with nothing real behind it (Android starts each visit fresh).
    weak var history: UndoManager?
    private var didFit = false
    private var saveWork: DispatchWorkItem?

    init(_ parent: NoteCanvas) {
      self.parent = parent
      pageCount = max(parent.notebook.pages ?? 1, 1)
    }

    /// Recognises a PencilKit stroke across changes: its untransformed path, so a lasso move
    /// (which only changes the transform) keeps the same item.
    struct StrokeKey: Hashable {
      let created: TimeInterval
      let count: Int
      let first: CGPoint
      let last: CGPoint
      let color: Int32

      init(_ s: PKStroke) {
        created = s.path.creationDate.timeIntervalSinceReferenceDate
        count = s.path.count
        first = s.path.first?.location ?? .zero
        last = s.path.last?.location ?? .zero
        color = s.ink.color.argb
      }

      static func == (a: StrokeKey, b: StrokeKey) -> Bool {
        a.created == b.created && a.count == b.count && a.first == b.first && a.last == b.last && a.color == b.color
      }
      func hash(into h: inout Hasher) {
        h.combine(created)
        h.combine(count)
        h.combine(first.x)
        h.combine(first.y)
      }
    }

    var layout: Pages { parent.notebook.pageLayout }
    var paged: Bool { parent.notebook.pages != nil }

    /// Nil when a stroke that began at [y] isn't cut off anywhere.
    func pageClip(startingAt y: Float) -> CGRect? {
      guard paged, let r = layout.clip(anchorY: y, count: pageCount, between: parent.settings.between) else { return nil }
      return CGRect(r)
    }

    /// The page a stroke belongs to (where it began), as a PencilKit mask.
    func mask(for s: InkStroke) -> UIBezierPath? {
      guard let first = s.inputs.points.first, let page = pageClip(startingAt: first.y) else { return nil }
      return UIBezierPath(rect: page)
    }

    /// Highlighter under the writing, as Android draws it; each keeps its own order.
    static func highlighterFirst<T>(_ items: [T], _ stroke: (T) -> InkStroke) -> [T] {
      items.filter { stroke($0).pen.isHighlighter } + items.filter { !stroke($0).pen.isHighlighter }
    }

    func load() {
      guard let canvas else { return }
      loading = true
      let rows = (try? parent.db.items(of: parent.notebook.id)) ?? []
      var inks: [InkStroke] = []
      shapeZ = 0
      var under = UnderItems()
      var lockedStrokes: [InkStroke] = []
      // Pictures and fills already decoded are kept; new ones are decoded off the main thread, so
      // opening a notebook with photos doesn't stall its opening (they come in a moment later).
      let had = pages?.pages.under
      var pictureSlots: [Slot<PlacedPicture, PictureRow>] = []
      var fillSlots: [Slot<FillPicture, ItemRow>] = []
      for row in rows {
        if let text = TextRow(row) {
          under.texts.append(text)
          continue
        }
        if let shape = ShapeRow(row) {
          under.shapes.append(shape)
          shapeZ = max(shapeZ, row.z)
          continue
        }
        if let fillRow = FillRow(row) {
          if let known = had?.fills.first(where: { $0.row == fillRow }) { fillSlots.append(.ready(known)) } else { fillSlots.append(.later(row)) }
          shapeZ = max(shapeZ, row.z)
          continue
        }
        if row.kind == 2, let sharp = SharpPage.of(row) {
          under.pdfPages.append(sharp)
          continue
        }
        if row.kind == 2, let p = PictureRow(row) {
          if let known = had?.pictures.first(where: { $0.row == p }) { pictureSlots.append(.ready(known)) } else { pictureSlots.append(.later(p)) }
          continue
        }
        guard let ink = InkStroke(row: row) else { continue }
        if ink.locked { lockedStrokes.append(ink) } else { inks.append(ink) }
      }
      under.setLockedInk(lockedStrokes) { self.mask(for: $0) }
      under.pictures = pictureSlots.compactMap(\.ready)
      under.fills = fillSlots.compactMap(\.ready)
      pages?.pages.under = under
      pages?.layer.setNeedsDisplay()
      dropPatches()
      decodeLater(pictureSlots, fillSlots)
      let drawn = Self.highlighterFirst(inks) { $0 }.map { (canvasStroke($0), $0) }
      stored = drawn.map { (StrokeKey($0.0), $0.1) }
      setDrawing(PKDrawing(strokes: drawn.map(\.0)))
      loading = false
      restack()
      applyPinchMode()
      updateContent()
      applyTool()
      reportUndo()
    }

    /// Bumped by each load: decoded pictures from an older one are dropped.
    var loadTicket = 0

    /// Decodes the pictures and fills [load] hadn't yet, off the main thread, then puts every one in
    /// its place in the stack and redraws where they are.
    private func decodeLater(_ pictures: [Slot<PlacedPicture, PictureRow>], _ fills: [Slot<FillPicture, ItemRow>]) {
      loadTicket += 1
      guard pictures.contains(where: { $0.ready == nil }) || fills.contains(where: { $0.ready == nil }) else { return }
      let ticket = loadTicket
      nonisolated(unsafe) let pictures = pictures, fills = fills
      DispatchQueue.global(qos: .userInitiated).async {
        let placedPictures = pictures.compactMap { slot -> PlacedPicture? in
          switch slot {
          case .ready(let p): p
          case .later(let row): placed(row)
          }
        }
        let placedFills = fills.compactMap { slot -> FillPicture? in
          switch slot {
          case .ready(let f): f
          case .later(let row): FillPicture(row)
          }
        }
        nonisolated(unsafe) let done = (placedPictures, placedFills)
        DispatchQueue.main.async { [weak self] in
          guard let self, self.loadTicket == ticket, let pages = self.pages else { return }
          pages.pages.under.pictures = done.0
          pages.pages.under.fills = done.1
          let fresh = done.0.filter { p in !pictures.contains { $0.ready?.row == p.row } }.map(\.rect)
            + done.1.filter { f in !fills.contains { $0.ready?.row == f.row } }.map { CGRect($0.row.rect) }
          self.redrawPage(fresh.reduce(nil) { joined($0, $1) })
          self.restack()
        }
      }
    }

    /// What the eraser left of the strokes it cut, waiting to be stored with their own pen.
    var pending: [StrokeKey: InkStroke] = [:]
    /// Strokes that were in the drawing at some point this session, by their key.
    var archive: [StrokeKey: InkStroke] = [:]
    /// The lasso and what it picked up (see Selection.swift).
    var lasso = LassoState()
    /// Hold-to-shape (see HoldToShape.swift).
    var hold = HoldState()
    /// New strokes swapped for the stored form (smoothed, kept to their page), by PencilKit's key:
    /// after an undo or redo PencilKit puts its own form back, and it's swapped again.
    var swappedForm: [StrokeKey: PKStroke] = [:]
    /// PencilKit checks the drawing it made before it undoes: each drawing it handed in that was
    /// swapped, by the drawing it became.
    var pkSwaps: [(ours: PKDrawing, theirs: PKDrawing)] = []
    var swappedNew = false
    /// Page numbers and the pull past the last page (see PageChrome.swift).
    var pageChrome = PageChromeState()
    /// The eraser (see Eraser.swift).
    var erasing = EraserState()
    /// The Shapes tool (see ShapeTool.swift), and the pen last used (shapes take its look).
    var shapeTool = ShapeToolState()
    /// The Fill tool (see FillTool.swift).
    var fillTool = FillToolState()
    /// Typing in a text box (see TextBoxes.swift).
    var textEdit = TextEditState()
    /// The laser and the ink the notebook draws itself (see OwnInk.swift).
    var own = OwnInkState()
    /// Auto arrow: the straight line that may get its head next (see AutoArrow.swift).
    var arrows = AutoArrowState()
    var lastTool: Tool?
    var lastPenId: String?

    /// The settings the canvas was last given, to change only what changed.
    var applied: CanvasSettings?

    func applySettings() {
      guard let canvas, let pages, applied != parent.settings else { return }
      let old = applied
      applied = parent.settings
      let s = parent.settings
      if old?.paper != s.paper {
        pages.pages.paper = s.paper
        pages.pages.picture = s.paper.picture
        pages.layer.setNeedsDisplay()
        dropPatches()
        layoutUnderlay()
        canvas.overrideUserInterfaceStyle = s.dark ? .dark : .light
        updateChrome()
        refreshHighlights()
      }
      if old?.pageGuides != s.pageGuides { updateChrome() }
      if old?.between != s.between, old != nil {
        // Strokes are hidden past their page or the page column: their masks change.
        saveNow()
        load()
      }
    }

    @objc func twoFingerTap() {
      guard parent.settings.smartUndo, !parent.viewOnly else { return }
      clearSelection()
      undoKeepingSwaps(redo: false)
      reportUndo()
    }

    @objc func threeFingerTap() {
      guard parent.settings.smartUndo, !parent.viewOnly else { return }
      clearSelection()
      undoKeepingSwaps(redo: true)
      reportUndo()
    }

    func applyTool() {
      guard let canvas else { return }
      // Choosing another tool puts down the text box being typed in.
      if let last = lastTool, last != parent.tool, textEdit.view != nil {
        DispatchQueue.main.async { self.finishText() }
      }
      // Another tool, or just looking, puts down what's picked up (after this update: it changes the screen's state).
      if lastTool.map({ $0 != parent.tool }) ?? false || parent.viewOnly, lasso.bounds != nil || !lasso.loop.isEmpty {
        DispatchQueue.main.async { self.clearSelection() }
      }
      if lastTool.map({ $0 != parent.tool }) ?? false || parent.viewOnly { forgetArrowLine() }
      lastTool = parent.tool
      // Draw with finger: Auto lets fingers write only on an iPad without a pen, as Android's Auto
      // (`!deviceHasStylus`). An iPad can't tell a paired Apple Pencil is there until it touches the
      // screen, so fingers write until the first Pencil touch ever seen here (`PencilSeen`), or
      // never when iOS's own "Only Draw with Apple Pencil" is on. (`.default` followed only that
      // setting, which is off unless she turns it on, so with a first-generation Pencil fingers wrote.)
      // Fingers write in UI tests, which have no pencil.
      switch parent.settings.fingerDraws {
      case nil:
        let pen = PencilSeen.yes || UIPencilInteraction.prefersPencilOnlyDrawing
        canvas.drawingPolicy = ProcessInfo.processInfo.arguments.contains("-uitest") || !pen ? .anyInput : .pencilOnly
      case true?: canvas.drawingPolicy = .anyInput
      case false?: canvas.drawingPolicy = .pencilOnly
      }
      applyFillTool()
      lasso.pan?.isEnabled = !parent.viewOnly
      shapeTool.watcher?.isEnabled = !parent.viewOnly && parent.tool == .shape
      switch parent.tool {
      case .pen(let id):
        lastPenId = id
        let pen = parent.pens.first { $0.id == id } ?? PenSlot.defaults[0]
        canvas.tool = PKInkingTool(pen.type.ink, color: UIColor(argb: pen.inkColor), width: InkWidth.toolWidth(pen.size, pen.type.ink))
      case .eraser, .lasso, .shape, .fill, .laser:
        // Our own eraser, lasso, Shapes, Fill and laser (Android's): the canvas doesn't draw while they're in use.
        canvas.tool = PKInkingTool(.monoline, color: .clear, width: 1)
      }
      // After the tool is set: setting it switches PencilKit's drawing back on (it then drew its own
      // stroke under ours until the pen lifted). A touch editing the selection doesn't write.
      canvas.drawingGestureRecognizer.isEnabled = inkOn && lasso.drag == nil
    }

    /// Whether PencilKit draws: a pen, not just looking, and not a pen whose ink the notebook draws
    /// itself (dashed lines, the highlighter). The other tools, and just looking, never write.
    var inkOn: Bool {
      guard !parent.viewOnly, case .pen = parent.tool else { return false }
      return ownInk == nil
    }

    func run(_ cmd: CanvasCommand) {
      guard let canvas else { return }
      switch cmd {
      case .undo:
        clearSelection()
        undoKeepingSwaps(redo: false)
      case .redo:
        clearSelection()
        undoKeepingSwaps(redo: true)
      case .addPage:
        pageCount += 1
        updateContent()
        persistPages()
      case .newText: startNewText()
      case .copySelection: copySelection()
      case .cutSelection: cutSelection()
      case .duplicateSelection: duplicateSelection()
      case .deleteSelection: deleteSelection()
      case .boxSelection: boxSelection()
      case .paste: pasteAtOffer()
      case .recolourSelection(let c): recolourSelection(c)
      case .thickenSelection(let v): restyleSelection(size: v, line: nil)
      case .mirrorSelection(let h): mirrorSelection(horizontal: h)
      case .arrangeSelection(let f): arrangeSelection(toFront: f)
      case .previewThickness(let v): previewThickness(v)
      case .lineSelection(let line): restyleSelection(size: nil, line: line)
      case .lockSelection: lockSelection()
      case .unlockAll: unlockAll()
      case .editText(let id): editPickedText(id)
      case .deletePage(let i): deletePage(i)
      case .rearrangePages(let order): rearrangePages(order)
      case .setEndless(let on): setEndless(on)
      case .goToPage(let i):
        let target = min(max(i, 0), pageCount - 1)
        let r = layout.rect(target)
        let y = CGFloat(r.top) * canvas.zoomScale - canvas.contentInset.top
        canvas.setContentOffset(CGPoint(x: canvas.contentOffset.x, y: y), animated: true)
      case .fitZoom:
        if !paged {
          fitEverything()
          break
        }
        guard let pages else { break }
        let fit = min((canvas.bounds.width - 48) / pages.worldSize.width, 1.2)
        canvas.setZoomScale(fit, animated: true)
      }
      reportUndo()
    }

    func reportUndo() {
      // After the current view update: this is also called while the canvas is being made (loading),
      // where a change to the screen's state would be dropped (a 2-page PDF showed "1 / 1").
      DispatchQueue.main.async { [weak self] in
        guard let self else { return }
        let u = self.canvas?.undoManager
        self.history = u
        self.parent.onUndoState(u?.canUndo ?? false, u?.canRedo ?? false)
        self.parent.onPageCount(self.pageCount)
      }
    }

    // MARK: Size and zoom

    func updateContent() {
      guard let canvas, let pages else { return }
      pages.pages.count = pageCount
      pages.pages.endless = paged ? nil : endlessSize()
      let world = pages.worldSize
      let z = canvas.zoomScale
      pages.layer.anchorPoint = .zero
      // Only a new size redraws the tiles: a zoom is the tiled layer's own (its levels of detail),
      // and redrawing every tile for each step of a pinch made the page visibly load again and again.
      let resized = pages.bounds.size != world
      pages.bounds = CGRect(origin: .zero, size: world)
      pages.transform = CGAffineTransform(scaleX: z, y: z)
      pages.layer.position = .zero
      if let marks = highlights {
        marks.bounds = CGRect(origin: .zero, size: world)
        marks.transform = CGAffineTransform(scaleX: z, y: z)
        marks.layer.position = .zero
        marks.setZoom(z, screen: canvas.traitCollection.displayScale)
      }
      if resized { pages.setNeedsDisplay() }
      layoutUnderlay()
      canvas.contentSize = CGSize(width: world.width * z, height: world.height * z)
      updateChrome()
      // Until she zooms by hand, the page keeps fitting the canvas as its size settles (the screen
      // slides in, the device turns): set once early, the fit was lost.
      if didFit, !zoomedByHand, canvas.bounds.size != fittedSize, canvas.bounds.width > 0 {
        fittedSize = canvas.bounds.size
        DispatchQueue.main.async { [weak self] in self?.fitWidth() }
      }
      if !didFit, canvas.bounds.width > 0 {
        didFit = true
        fittedSize = canvas.bounds.size
        // Hidden until it's where she left off, so it doesn't show at the top first and then jump.
        firstPlacing = true
        canvas.alpha = 0
        // The page's whole width on screen (a slide-shaped PDF page is far wider than A4), once the
        // canvas has its size.
        DispatchQueue.main.async { [weak self] in self?.fitWidth() }
        return
      }
      centre()
    }

    /// She pinched: the first fit doesn't come back over it.
    var zoomedByHand = false
    var fittedSize: CGSize = .zero
    /// The first fit is still being made (the canvas is hidden until then).
    var firstPlacing = false

    func scrollViewWillBeginZooming(_ scrollView: UIScrollView, with view: UIView?) {
      if scrollView.isTracking || scrollView.isZoomBouncing || scrollView.pinchGestureRecognizer?.state == .began { zoomedByHand = true }
    }

    /// The first page's whole width on screen, from the top of the first page.
    private func fitWidth() {
      guard let canvas, let pages else { return }
      // It opens where she left off (Android keeps the view per notebook); the first time, the page's width.
      let saved = parent.notebook.viewport.flatMap { v -> Viewport? in v.zoom > 0.05 ? v : nil }
      let fit = min((canvas.bounds.width - 48) / (paged ? pages.worldSize.width : CGFloat(layout.width)), 1.2)
      let zoom = saved.map { min(max(CGFloat($0.zoom), canvas.minimumZoomScale), canvas.maximumZoomScale) } ?? fit
      func place() {
        guard let canvas = self.canvas else { return }
        self.updateContent()
        let z = canvas.zoomScale
        let at = saved.map { CGPoint(x: CGFloat($0.x) * z - canvas.contentInset.left, y: CGFloat($0.y) * z - canvas.contentInset.top) }
          ?? CGPoint(x: -canvas.contentInset.left, y: -canvas.contentInset.top)
        canvas.setContentOffset(at, animated: false)
        self.reportView()
        if self.firstPlacing {
          self.firstPlacing = false
          UIView.animate(withDuration: 0.12) { canvas.alpha = 1 }
        }
      }
      canvas.setZoomScale(zoom, animated: false)
      if abs(canvas.zoomScale - zoom) < 0.0005 { return place() }
      // PKCanvasView can ignore a plain zoom change this early (the animated one works); the page
      // stays hidden while it zooms.
      canvas.setZoomScale(zoom, animated: true)
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { place() }
    }

    private func centre() {
      guard let canvas, let pages else { return }
      let w = pages.worldSize.width * canvas.zoomScale
      let side = max((canvas.bounds.width - w) / 2, 16)
      // A little room is always left under the last page (Android's `REST_DP`); pulling opens more.
      // While a text box is typed in, as much again as the keyboard covers, so a box at the very
      // end of the notebook can still be scrolled up clear of it.
      let typing = textEdit.view != nil && textEdit.typing ? keyboardOverlap : 0
      let insets = UIEdgeInsets(top: parent.topClearance, left: side, bottom: CGFloat(PagePull.rest) + typing, right: side)
      if canvas.contentInset != insets { canvas.contentInset = insets }
    }

    func scrollViewDidZoom(_ scrollView: UIScrollView) {
      // Pictures of the page were made for the zoom as it was.
      dropPatches()
      zoomEndsPull()
      updateContent()
      layoutTextEditor()
      redrawSelection()
      reportView()
    }

    /// Which page is in the middle of the screen, and the zoom.
    private func reportView() {
      guard let canvas else { return }
      let z = max(canvas.zoomScale, 0.01)
      let midY = (canvas.contentOffset.y + canvas.bounds.height / 2) / z
      let p = min(max(layout.indexAt(Float(midY)), 0), max(pageCount - 1, 0))
      parent.onView(p, z)
    }

    func scrollViewDidEndZooming(_ scrollView: UIScrollView, with view: UIView?, atScale scale: CGFloat) {
      updateContent()
      makeRoom()
    }

    func scrollViewDidEndDecelerating(_ scrollView: UIScrollView) { makeRoom() }

    func scrollViewDidEndDragging(_ scrollView: UIScrollView, willDecelerate decelerate: Bool) {
      if !decelerate { makeRoom() }
    }

    func scrollViewDidScroll(_ scrollView: UIScrollView) {
      growEndless()
      updateChrome()
      reportView()
      if lasso.bounds != nil { drawChrome() }
      reportSelection()
      if textEdit.view != nil { reportTextBar() }
    }

    // MARK: Keeping the store in step

    /// While a carried selection is put down: the writing as it was before it was lifted, which the
    /// change is made from, though the canvas still shows it without what was carried.
    var inkBase: PKDrawing?
    /// Waiting for the canvas to show what was just set (see `afterRender`).
    var renderWaiters: [(ticket: Int, run: () -> Void)] = []
    var renderTicket = 0
    /// A picture of the canvas held over it while a change is drawn (`holdTheLook`).
    var heldLook: UIView?
    var lookTicket = 0

    /// Runs [f] once the canvas has drawn its drawing as it is now (or after half a second at most),
    /// so a live picture of something just kept can stay over it until then without a blink.
    func afterRender(_ f: @escaping () -> Void) {
      renderTicket += 1
      let ticket = renderTicket
      renderWaiters.append((ticket, f))
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { [weak self] in
        guard let self, let i = self.renderWaiters.firstIndex(where: { $0.ticket == ticket }) else { return }
        self.renderWaiters.remove(at: i).run()
      }
    }

    func canvasViewDidFinishRendering(_ canvasView: PKCanvasView) {
      let due = renderWaiters
      renderWaiters = []
      for w in due { w.run() }
    }

    func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) {
      guard !loading, canvasView.drawing != ownDrawing else { return }
      sync(canvasView.drawing)
      reportUndo()
    }

    /// Matches the drawing's strokes to what's stored: new ones are stored, moved ones rewritten,
    /// missing ones removed. Only ink rows are touched; shapes, pictures and text stay as they are.
    /// Undo or redo with PencilKit's own strokes in place for the moment it looks for them; `sync`
    /// swaps them back straight after.
    func undoKeepingSwaps(redo: Bool) {
      guard let canvas, let undo = canvas.undoManager else { return }
      forgetArrowLine()
      if let swap = pkSwaps.last(where: { $0.ours == canvas.drawing }) {
        loading = true
        undo.disableUndoRegistration()
        setDrawing(swap.theirs)
        undo.enableUndoRegistration()
        loading = false
      }
      if redo { undo.redo() } else { undo.undo() }
      // Nothing changed (the step wasn't PencilKit's): the swapped forms go straight back.
      if canvas.drawing.strokes.contains(where: { pk in swappedForm[StrokeKey(pk)].map { !Self.sameForm($0, pk) } ?? false }) { sync(canvas.drawing) }
    }

    func sync(_ given: PKDrawing) {
      // PencilKit's own form of a swapped stroke (back for an undo) is the swapped one.
      var swappedBack = false
      let drawing = PKDrawing(strokes: given.strokes.map { pk in
        guard let swapped = swappedForm[StrokeKey(pk)], !Self.sameForm(swapped, pk) else { return pk }
        swappedBack = true
        return swapped
      })
      var byKey: [StrokeKey: [Int]] = [:]
      for (i, s) in stored.enumerated() { byKey[s.key, default: []].append(i) }
      var next: [(key: StrokeKey, stroke: InkStroke)] = []
      var used = Set<Int>()
      var changed: [InkStroke] = []
      var rebuilt: [PKStroke] = []
      var needsRebuild = swappedBack
      swappedNew = false
      let pen = currentPen
      var z = max(stored.map(\.stroke.z).max() ?? 0, shapeZ) + 1
      for pk in drawing.strokes {
        let key = StrokeKey(pk)
        if let i = byKey[key]?.first(where: { !used.contains($0) }) {
          used.insert(i)
          var s = stored[i].stroke
          if pk.transform != .identity {
            // Moved or resized with the lasso: store the moved samples, and draw it untransformed.
            // The stored samples are carried (not read back from PencilKit, whose points are the
            // drawn ones, filled in by InkModel), so moving a stroke never smooths it again.
            let t = pk.transform
            let locked = s.locked
            s = s.transformed(Affine(a: Float(t.a), b: Float(t.b), c: Float(t.c), d: Float(t.d), tx: Float(t.tx), ty: Float(t.ty)))
            s.locked = locked
            changed.append(s)
            let fresh = canvasStroke(s)
            rebuilt.append(fresh)
            next.append((StrokeKey(fresh), s))
            needsRebuild = true
          } else {
            rebuilt.append(pk)
            next.append((key, s))
          }
        } else if var s = pending.removeValue(forKey: key) ?? archive[key] {
          // An eraser's piece, or brought back by PencilKit's undo: it keeps its own pen, and its
          // place in the stack when it has one (a new stroke goes on top).
          if s.z == 0 {
            s.z = z
            z += 1
          }
          changed.append(s)
          if let box = s.inputs.bounds { growPages(toHold: box) }
          rebuilt.append(pk)
          next.append((key, s))
        } else if isSnappedStroke(pk) || parent.tool == .lasso || parent.tool == .shape || parent.tool == .fill {
          // The stroke that became a shape (hold-to-shape), or one PencilKit drew while the lasso was
          // on (in clear ink): neither is kept.
          needsRebuild = true
        } else {
          // A new stroke PencilKit drew (pens are drawn by the notebook now; see OwnInk).
          let s = InkStroke.from(pk, pen: pen, id: newId(), z: z)
          z += 1
          changed.append(s)
          if let box = s.inputs.bounds { growPages(toHold: box) }
          if let m = mask(for: s), !m.bounds.contains(CGRect(s.bounds)) {
            // It runs off its page: draw it hidden past the edge, as it will be from now on.
            var masked = pk
            masked.mask = m
            rebuilt.append(masked)
            remember(pk, swappedFor: masked)
            next.append((StrokeKey(masked), s))
            needsRebuild = true
          } else {
            rebuilt.append(pk)
            next.append((key, s))
          }
        }
      }
      // A highlight added late still goes under the writing.
      let order = Self.highlighterFirst(Array(next.indices)) { next[$0].stroke }
      if order != Array(next.indices) {
        next = order.map { next[$0] }
        rebuilt = order.map { rebuilt[$0] }
        needsRebuild = true
      }
      // A stroke replaced under the same id (recoloured, mirrored) isn't removed.
      let keptIds = Set(next.map(\.stroke.id))
      let removed = stored.indices.filter { !used.contains($0) }.map { stored[$0].stroke.id }.filter { !keptIds.contains($0) }
      // Every stroke seen, so one brought back by undo is stored exactly as it was (not with the pen in use).
      for (k, st) in stored { archive[k] = st }
      stored = next
      do {
        for s in changed { try parent.db.put(s.row, in: parent.notebook.id) }
        for id in removed { try parent.db.removeItem(id) }
      } catch {
        CrashNote.log("saving strokes: \(error)")
      }
      if !changed.isEmpty || !removed.isEmpty { touch() }
      if needsRebuild, let canvas {
        // Swap in the stored form (page mask, untransformed) without making an undo step of it.
        loading = true
        canvas.undoManager?.disableUndoRegistration()
        setDrawing(PKDrawing(strokes: rebuilt))
        canvas.undoManager?.enableUndoRegistration()
        loading = false
        if swappedNew {
          pkSwaps.append((ours: canvas.drawing, theirs: given))
          if pkSwaps.count > 200 { pkSwaps.removeFirst(pkSwaps.count - 200) }
        }
      }
      restack()
      // PencilKit's own undo changes its drawing without it being set from here.
      refreshHighlights()
    }

    /// Whether two forms of one stroke (same key) are drawn the same: both kept to a page or not,
    /// and the same points where smoothing moves them.
    static func sameForm(_ a: PKStroke, _ b: PKStroke) -> Bool {
      guard (a.mask == nil) == (b.mask == nil), a.path.count == b.path.count, a.path.count > 0 else { return false }
      let mid = a.path.count / 2, end = a.path.count - 1
      return a.path[mid].location == b.path[mid].location && a.path[end].location == b.path[end].location
    }

    private func remember(_ drawn: PKStroke, swappedFor swapped: PKStroke) {
      swappedForm[StrokeKey(drawn)] = swapped
      swappedNew = true
    }

    var currentPen: PenSlot {
      if case .pen(let id) = parent.tool, let p = parent.pens.first(where: { $0.id == id }) { return p }
      return PenSlot.defaults[0]
    }

    func persistPages() {
      try? parent.db.run("UPDATE notebooks SET pages=? WHERE id=?", [.int(Int64(pageCount)), .text(parent.notebook.id)])
    }

    func touch() {
      saveWork?.cancel()
      let id = parent.notebook.id, db = parent.db
      let w = DispatchWorkItem { try? db.run("UPDATE notebooks SET updated=? WHERE id=?", [.int(nowMillis()), .text(id)]) }
      saveWork = w
      DispatchQueue.main.asyncAfter(deadline: .now() + 0.4, execute: w)
    }

    func saveNow() {
      saveWork?.perform()
      saveWork = nil
      guard let canvas else { return }
      let vp = Viewport(
        x: Float((canvas.contentOffset.x + canvas.contentInset.left) / canvas.zoomScale),
        y: Float((canvas.contentOffset.y + canvas.contentInset.top) / canvas.zoomScale), zoom: Float(canvas.zoomScale))
      if let json = try? String(data: JSONEncoder().encode(vp), encoding: .utf8) {
        try? parent.db.run("UPDATE notebooks SET viewport=? WHERE id=?", [.text(json), .text(parent.notebook.id)])
      }
      saveThumbnail()
    }

    /// The library card's preview: the top of page 1, or the writing last seen on an endless canvas.
    private func saveThumbnail() {
      guard let canvas, let pages else { return }
      let region: CGRect
      if paged {
        region = Thumbnail.previewRegion(CGRect(layout.rect(0)))
      } else {
        let z = max(canvas.zoomScale, 0.01)
        let visible = CGRect(x: canvas.contentOffset.x / z, y: canvas.contentOffset.y / z, width: canvas.bounds.width / z, height: canvas.bounds.height / z)
        let seen = canvas.drawing.bounds.intersection(visible)
        let around = seen.isNull || seen.isEmpty ? visible : seen.insetBy(dx: -48, dy: -48)
        var w = max(around.width, around.height * Thumbnail.cardAspect, 560)
        w = min(w, visible.width * 1.5)
        let h = w / Thumbnail.cardAspect
        region = CGRect(x: around.midX - w / 2, y: around.midY - h / 2, width: w, height: h)
      }
      Thumbnail.save(id: parent.notebook.id, region: region, pages: pages.pages, drawing: visibleDrawing(canvas.drawing), db: parent.db)
    }
  }
}

enum CanvasCommand: Equatable {
  case undo, redo, addPage, fitZoom, newText
  case copySelection, cutSelection, duplicateSelection, deleteSelection, boxSelection, paste
  case recolourSelection(UInt32), thickenSelection(Float), mirrorSelection(Bool), arrangeSelection(Bool)
  /// The thickness slider moving (nothing saved yet), a line style, lock or unlock, and Edit text.
  case previewThickness(Float), lineSelection(String), lockSelection, unlockAll, editText(String)
  case deletePage(Int)
  /// The pages laid out again: old page numbers, top to bottom.
  case rearrangePages([Int])
  case goToPage(Int)
  /// One endless page, or back to A4 pages.
  case setEndless(Bool)
}

/// A plain log of things that went wrong, kept on the device (Android's CrashLog, simplified).
enum CrashNote {
  static func log(_ text: String) {
    let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("crash.log")
    let line = "\(Date()) \(text)\n"
    if let h = try? FileHandle(forWritingTo: url) {
      h.seekToEndOfFile()
      h.write(Data(line.utf8))
      try? h.close()
    } else {
      try? Data(line.utf8).write(to: url)
    }
  }
}

/// An imported PDF's page drawn from the PDF kept beside its picture (Android's `SharpPdf` draws
/// patches at screen resolution; here the tiled page layer simply draws the page as vectors at every
/// zoom). The picture's name says which PDF and page it is: `pdf-<pdf>-p<page>-<uuid>.jpg`.
final class SharpPage: @unchecked Sendable {
  /// How a page is drawn. On screen it's cut into many tiles, and drawing the PDF's own lines for
  /// each tile meant reading the whole page once per tile, one tile at a time (PDFKit can't draw two
  /// at once): the page loaded square by square. So for tiles the page is drawn once as a picture at
  /// the tiles' sharpness, and each tile is cut from it.
  enum Way: Sendable {
    /// Its own lines, every time: PDF export, page pictures, anything that isn't a tile.
    case lines
    /// A picture of the whole page at the sharpness asked for, made once and kept (tiles). Zoomed in
    /// so far that the picture would be too big, its lines are drawn after all.
    case picture
    /// A picture already kept, at whatever sharpness, else its lines: for drawing now, on the main
    /// thread, without stopping to make one.
    case kept
  }

  private let page: PDFPage
  /// Which PDF and page it is, for the pictures kept of it.
  private let name: String
  /// PDFKit isn't safe to draw from several tile threads at once.
  private static let lock = NSLock()
  nonisolated(unsafe) private static var docs: [String: PDFDocument] = [:]
  /// Pages drawn as pictures, by page and size; and each page's latest one. A few screens' worth:
  /// the least recently used go first, and all of them when memory runs short.
  nonisolated(unsafe) private static let pictures: NSCache<NSString, CGImage> = {
    let cache = NSCache<NSString, CGImage>()
    cache.totalCostLimit = 192 << 20
    return cache
  }()
  private static let latestLock = NSLock()
  nonisolated(unsafe) private static var latest: [String: NSString] = [:]
  /// A page's picture is never bigger than this many pixels (an A4 page at twice the screen's sharpness).
  private static let mostPixels: CGFloat = 12_000_000

  private init(_ page: PDFPage, name: String) {
    self.page = page
    self.name = name
  }

  /// Whether a page could be drawn right now without waiting for another to finish.
  static var free: Bool {
    guard lock.`try`() else { return false }
    lock.unlock()
    return true
  }

  /// The page a picture item shows, when it's an imported PDF page whose PDF is here.
  static func of(_ row: ItemRow) -> (id: String, rect: CGRect, page: SharpPage)? {
    guard let p = PictureRow(row), p.crop == nil, let (id, index) = p.pdfSource, p.rect.width > 0, p.rect.height > 0 else { return nil }
    lock.lock()
    defer { lock.unlock() }
    let doc: PDFDocument
    if let d = docs[id] {
      doc = d
    } else {
      guard let d = PDFDocument(url: AppFiles.folders.images.appendingPathComponent("pdf-\(id).pdf")) else { return nil }
      docs[id] = d
      doc = d
    }
    guard let page = doc.page(at: index) else { return nil }
    return (row.id, CGRect(p.rect), SharpPage(page, name: "\(id)#\(index)"))
  }

  /// The whole page as a picture for [rect] at the sharpness [cg] draws at, kept once made. Nil
  /// when it would be too big, or with [make] off when none is kept.
  private func picture(for rect: CGRect, in cg: CGContext, make: Bool) -> CGImage? {
    if !make {
      Self.latestLock.lock()
      let key = Self.latest[name]
      Self.latestLock.unlock()
      return key.flatMap { Self.pictures.object(forKey: $0) }
    }
    let t = cg.ctm
    let px = (t.a * t.a + t.b * t.b).squareRoot()
    let w = (rect.width * px).rounded(.up), h = (rect.height * px).rounded(.up)
    guard w >= 1, h >= 1, w * h <= Self.mostPixels else { return nil }
    let key = "\(name)@\(Int(w))x\(Int(h))" as NSString
    if let hit = Self.pictures.object(forKey: key) { return hit }
    Self.lock.lock()
    defer { Self.lock.unlock() }
    // Another tile of this page may have made it while this one waited.
    if let hit = Self.pictures.object(forKey: key) { return hit }
    let box = page.bounds(for: .mediaBox)
    let across = page.rotation % 180 != 0 ? box.height : box.width
    guard across > 0,
      let ctx = CGContext(
        data: nil, width: Int(w), height: Int(h), bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpaceCreateDeviceRGB(),
        bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
    else { return nil }
    ctx.setFillColor(CGColor(gray: 1, alpha: 1))
    ctx.fill(CGRect(x: 0, y: 0, width: w, height: h))
    ctx.scaleBy(x: w / across, y: w / across)
    page.draw(with: .mediaBox, to: ctx)
    guard let image = ctx.makeImage() else { return nil }
    Self.pictures.setObject(image, forKey: key, cost: Int(w * h) * 4)
    Self.latestLock.lock()
    Self.latest[name] = key
    Self.latestLock.unlock()
    return image
  }

  /// Draws the page filling [rect] (world units, y down) on white.
  func draw(in rect: CGRect, _ cg: CGContext, _ way: Way = .lines) {
    if way != .lines, let image = picture(for: rect, in: cg, make: way == .picture) {
      cg.saveGState()
      cg.translateBy(x: rect.minX, y: rect.maxY)
      cg.scaleBy(x: 1, y: -1)
      cg.interpolationQuality = .high
      cg.draw(image, in: CGRect(origin: .zero, size: rect.size))
      cg.restoreGState()
      return
    }
    Self.lock.lock()
    defer { Self.lock.unlock() }
    let box = page.bounds(for: .mediaBox)
    let turned = page.rotation % 180 != 0
    let w = turned ? box.height : box.width
    guard w > 0 else { return }
    cg.saveGState()
    cg.setFillColor(UIColor.white.cgColor)
    cg.fill(rect)
    cg.clip(to: rect)
    let s = rect.width / w
    cg.translateBy(x: rect.minX, y: rect.maxY)
    cg.scaleBy(x: s, y: -s)
    page.draw(with: .mediaBox, to: cg)
    cg.restoreGState()
  }
}

/// Something [load] has already (ready) or will decode in a moment (later).
enum Slot<Ready, Source> {
  case ready(Ready)
  case later(Source)

  var ready: Ready? { if case .ready(let r) = self { r } else { nil } }
}
