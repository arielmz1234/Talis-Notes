import SwiftUI
import TaliCore

/// A notebook, laid out like Android's `NotebookScreen`: the canvas with its pages; at the top, back
/// and undo/redo on the left, the main toolbar in the middle (under them when narrow), the name and
/// the eye on the right; the active tool's panel hanging below; the page and zoom chips at the bottom.
struct NotebookView: View {
  var notebook: NotebookMeta
  var onBack: () -> Void
  /// Quick open: another notebook in place of this one (Back still goes to the library).
  var onSwitch: (NotebookMeta) -> Void

  init(notebook: NotebookMeta, onBack: @escaping () -> Void, onSwitch: @escaping (NotebookMeta) -> Void = { _ in }) {
    self.notebook = notebook
    self.onBack = onBack
    self.onSwitch = onSwitch
    _viewing = State(initialValue: notebook.draft || PhoneWriting.opensViewOnly(notebook.id, kind: notebook.kind))
    _draft = State(initialValue: notebook.draft)
    _title = State(initialValue: notebook.title)
    _paper = State(initialValue: notebook.paper)
    _customPaper = State(initialValue: notebook.customPaper)
    _dark = State(initialValue: notebook.dark)
    _endless = State(initialValue: notebook.pages == nil)
  }
  @Environment(Library.self) private var lib
  @Environment(\.tali) private var c
  @Environment(\.colorScheme) private var scheme
  @State private var tool: Tool = .pen(PenStore.activePen)
  @State private var pens: [PenSlot] = PenStore.load()
  @State private var panel: PanelKind?
  /// What the Shapes tool draws (Android's `lastShape`), and the standard shape last chosen.
  @AppStorage(ShapeSettings.Key.lastShape) private var lastShape = ShapeKind.LINE.rawValue
  @State private var lastKind = ShapeChoice.kind(UserDefaults.standard.string(forKey: ShapeSettings.Key.lastShape) ?? "")
  @AppStorage(ShapeSettings.Key.customShapes) private var customShapesJSON = "[]"
  /// The pen last written with, which the Shapes tool can follow.
  @State private var lastPenId: String?
  @State private var browsingShapes = false
  /// What's picked up, as the shape being named before it joins My shapes.
  @State private var savingShape: ShapeGeometry?
  /// The tool in use before this one, for Apple Pencil's double-tap to go back to.
  @State private var toolBefore: Tool?
  @AppStorage("eraserPrecise") private var eraserPrecise = false
  @AppStorage("eraserSize") private var eraserSize: Double = 16
  @AppStorage("scribbleDelete") private var scribbleDelete = false
  @State private var eraserAffects = PenFilters.load("eraserAffects")
  @State private var canUndo = false
  @State private var canRedo = false
  @State private var pageCount = 1
  @State private var page = 0
  @State private var zoom: CGFloat = 1
  @State private var command: CanvasCommand?
  /// On a phone a notebook opens just for looking until Write is tapped (remembered per notebook, on this phone only).
  @State private var viewing: Bool
  /// Toolbars hidden with the eye (Android's focus mode).
  @State private var hidden = false
  /// Where each bar last sat, and the mist playing where they are or were.
  @State private var bars = BarFrames()
  @State private var puff: BarPuff?
  /// The notebook's name as shown (tapping the name pill renames it, as on Android).
  @State private var title: String
  @State private var renaming = false
  /// The notebook's paper and dark paper, changed from the ⋯ menu.
  @State private var paper: PaperStyle
  /// One of her own papers, drawn instead of [paper] while it exists.
  @State private var customPaper: String?
  @State private var paperCard: PaperCard?
  @State private var dark: Bool
  /// One endless page instead of A4 pages, switched from the ⋯ menu.
  @State private var endless: Bool
  @State private var moreOpen = false
  /// What the lasso picked up (window coordinates), a held spot offering Paste, and a hint line.
  @State private var selection: SelectionInfo?
  @State private var pasteOffer: CGPoint?
  /// The text box being typed in, for the small bar above it.
  @State private var textBar: TextBarInfo?
  /// A picture just taken with the screenshot lasso, waiting to be saved, shared or copied.
  @State private var screenshot: URL?
  @AppStorage(LassoSettings.Key.free) private var lassoFree = false
  @AppStorage(LassoSettings.Key.shot) private var lassoShot = false
  @State private var hint: String?
  @State private var hintTask: Task<Void, Never>?
  @State private var bridge = CanvasBridge()
  @State private var showPages = false
  @State private var exporting = false
  /// Insert's choice, being run (the photo picker, camera or file picker).
  @State private var inserting: InsertChoice?
  @State private var memos = MemoModel()
  @State private var crop = CropModel()
  /// Opened from another app and not saved yet: just for looking, with Save (Android's draft).
  @State private var draft: Bool
  @State private var savingDraft = false
  @State private var leavingDraft = false
  @Environment(\.scenePhase) private var scenePhase
  /// Draw with finger: -1 Auto, 0 off, 1 on (app-wide, as on Android).
  @AppStorage("fingerDraws") private var fingerDrawsRaw = -1
  @AppStorage("smartUndo") private var smartUndo = true
  @AppStorage("inkBetweenPages") private var between = false
  @AppStorage("hideNavBar") private var fullScreen = false

  /// How wide the top row's parts are, to tell whether they fit on one line (see `oneRow`).
  @State private var rowWidths = RowWidths()

  var body: some View {
    GeometryReader { geo in
      // The bars are laid out at the notebook's own scale (NotebookScale): w × h of its units.
      let k = NotebookScale.factor
      let w = geo.size.width / k, h = geo.size.height / k
      let narrow = NotebookScale.twoRows(width: geo.size.width)
      ZStack(alignment: .top) {
        // An endless canvas is paper all the way to the screen's edges; pages lie on the desk.
        Color(uiColor: endless ? paperSpec.paperColor : dark ? UIColor(argb: Int32(bitPattern: 0xFF0E_0C13)) : UIColor(c.desk)).ignoresSafeArea()
        NoteCanvas(
          notebook: canvasNotebook, db: lib.db, tool: tool, pens: pens, viewOnly: viewing,
          topClearance: (narrow ? 164 : 104) * k,
          settings: CanvasSettings(
            paper: paperSpec, fingerDraws: fingerDraws, smartUndo: smartUndo, between: between, pageGuides: PaperSettings.shared.pageGuides),
          bridge: bridge,
          eraser: EraserSettings(precise: eraserPrecise, size: Float(eraserSize), affects: eraserAffects, scribbleDelete: scribbleDelete),
          shape: shapeSource,
          onUndoState: { u, r in
            canUndo = u
            canRedo = r
          },
          onPageCount: { pageCount = $0 },
          onSelection: { r in withAnimation(.easeOut(duration: 0.14)) { selection = r } },
          onHint: { show($0) },
          onTextBar: { textBar = $0 },
          onPasteOffer: { p in withAnimation(.easeOut(duration: 0.16)) { pasteOffer = p } },
          onPencil: pencilGesture,
          onScreenshot: { url in withAnimation(Ease.outStrong(duration: 0.24)) { screenshot = url } },
          onView: { p, z in
            // Only what changed: this comes every frame the page scrolls.
            if page != p { page = p }
            if zoom != z { zoom = z }
            memos.cameraMoved()
            crop.cameraMoved()
          },
          command: $command
        )
        .ignoresSafeArea(edges: .bottom)
        // The keyboard (typing in a text box) doesn't push the page up: the canvas scrolls the box into view.
        .ignoresSafeArea(.keyboard)
        // A tap on the page closes an open panel, as on Android.
        .simultaneousGesture(TapGesture().onEnded {
          if panel != nil { withAnimation(.easeOut(duration: 0.1)) { panel = nil } }
          if memos.openMemo != nil { memos.openMemo = nil }
        })
        .modifier(DropOnPage(enabled: !viewing, bridge: bridge, hint: show))
        .overlay { MemoLayer(memos: memos, hint: show) }
        .overlay { CropLayer(crop: crop) }
        // Under the bars and their panels, as on Android, where it belongs to the page.
        if let sel = selection, !hidden, !viewing {
          GeometryReader { safe in
            SelectionMenuLayer(
              info: sel, narrow: narrow, bottomInset: safe.safeAreaInsets.bottom, onSaveShape: saveShape,
              onCrop: { if let p = bridge.coordinator?.pickedItems.first?.picture { crop.start(p) } }
            ) { command = $0 }
            .ignoresSafeArea()
          }
        }
        VStack(spacing: narrow ? 18 : 0) {
          if viewing && draft {
            DraftBar(
              title: title, pages: notebook.pages != nil ? pageCount : nil, dark: $dark,
              onBack: { leavingDraft = true }, onSave: { savingDraft = true },
              onPages: notebook.pages != nil ? { withAnimation(Ease.outStrong(duration: 0.24)) { showPages = true } } : nil,
              onFit: { command = .fitZoom })
          } else if viewing {
            HStack(spacing: 10) {
              leftBars
              Spacer(minLength: 10)
              Button {
                withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) {
                  viewing = false
                  PhoneWriting.startWriting(notebook.id)
                  ViewOnly.set(notebook.id, false)
                }
              } label: {
                Label(L("write"), systemImage: "pencil").font(TaliType.labelLarge)
              }
              .buttonStyle(PrimaryButtonStyle())
              .frame(height: 52)
              .accessibilityIdentifier("write")
            }
          } else if narrow {
            HStack(spacing: 10) {
              if !hidden { leftBars.bar("title", 0, bars).measured { rowWidths.title = $0 } }
              Spacer(minLength: 10)
              if !hidden { rightBars(showName: !PhoneWriting.onPhone).bar("undo", 2, bars) }
            }
            if !hidden { mainToolbar(showColors: !PhoneWriting.onPhone, toolsRoom: PhoneWriting.onPhone ? w - 44 : rowWidths.stripRoomAlone(w)).bar("tools", 1, bars).measured { rowWidths.tools = $0 } }
          } else {
            // One row, as on Android: the name and the eye and ⋯ share the corner, and the name
            // shortens when the tools need the room (see TopBarLayout).
            TopBarLayout {
              if !hidden { leftBars.bar("title", 0, bars).measured { rowWidths.title = $0 }.topBarRole(.title) }
              // The tools fold into pages (the arrow) rather than leave the name less than its room.
              if !hidden { mainToolbar(showColors: true, toolsRoom: rowWidths.stripRoom(w)).bar("tools", 1, bars).measured { rowWidths.tools = $0 }.topBarRole(.tools).zIndex(1) }
              if !hidden { rightBars(showName: true).bar("undo", 2, bars).topBarRole(.corner) }
            }
            .frame(minHeight: 60, alignment: .top)
          }
          if !hidden, !viewing, let shown = panel {
            Spacer().frame(height: 10)
            Group {
              switch shown {
              case .pen:
                if let i = activePenIndex { PenPanel(pen: $pens[i], onAddPen: addPen, onRemovePen: pens.count > 1 ? { removePen(pens[i].id) } : nil) }
              case .eraser:
                EraserPanel(precise: $eraserPrecise, size: Binding(get: { Float(eraserSize) }, set: { eraserSize = Double($0) }), affects: $eraserAffects,
                  scribbleDelete: $scribbleDelete)
              case .shapes:
                ShapesPanel(picker: shapePicker, pen: shapePen, compact: w < 660) {
                  withAnimation(Ease.outStrong(duration: 0.24)) { browsingShapes = true }
                }
              case .fill:
                FillPanel()
              case .lasso:
                LassoPanel()
              }
            }
            .environment(\.panelRoom, h - (narrow ? 292 : 196))
            .transition(
              .asymmetric(
                insertion: .opacity.combined(with: .scale(scale: 0.97, anchor: .top)).combined(with: .offset(y: -6)),
                removal: .opacity.combined(with: .scale(scale: 0.98, anchor: .top))))
            // Clear of the page and zoom chips, so a tall panel scrolls instead of running under them.
            .padding(.bottom, 56)
          }
        }
        .padding(.horizontal, 16)
        .padding(.top, 20)
        // The name as wide as it would be, measured out of sight (on one row it may be shortened).
        .background(alignment: .topLeading) {
          nameBar.fixedSize().hidden().allowsHitTesting(false).accessibilityHidden(true)
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { rowWidths.name = $0 }
        }
        // Drawn at the notebook's scale: as wide as the screen, everything in it k times its size.
        .modifier(NotebookScaled(scale: k, width: geo.size.width))
        // Over the toolbars, as on Android: the box may be scrolled up under them.
        if let bar = textBar, !viewing {
          TextBarLayer(
            info: bar, topLimit: (narrow ? 164 : 104) * k - 10,
            onSize: { bridge.coordinator?.resizeText($0) }, onColour: { bridge.coordinator?.recolourText($0) },
            onDelete: { bridge.coordinator?.deleteText() }, onDone: { bridge.coordinator?.finishText() })
        }
        RecorderPanel(memos: memos, hint: show)
        if crop.edit != nil {
          VStack {
            Spacer()
            CropBar(crop: crop)
          }
        }
        if let puff {
          // Over dark paper the mist is the dark theme's pale, see-through one, as on Android.
          BarPuffs(puff: puff).ignoresSafeArea().environment(\.tali, dark ? ColorTheme.current.dark : barColors)
        }
        if hidden, !viewing {
          ShowToolbarsButton { setHidden(false) }
            .padding(.top, 26).padding(.trailing, 22)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
            .transition(
              .asymmetric(
                insertion: .opacity.combined(with: .scale(scale: 0.9)).animation(Ease.outStrong(duration: 0.24).delay(0.08)),
                removal: .opacity.combined(with: .scale(scale: 0.95)).animation(Ease.outStrong(duration: 0.14))))
        }
        // The page and zoom chips, bottom right, under any card that opens over the notebook.
        if !hidden {
          VStack {
            Spacer()
            HStack(spacing: 8) {
              Spacer()
              if !endless {
                PageChip(page: page, count: pageCount, onGo: { command = .goToPage($0) }) {
                  withAnimation(Ease.outStrong(duration: 0.24)) { showPages = true }
                }
              }
              ZoomChip(zoom: zoom) { command = .fitZoom }
            }
          }
          .padding(.horizontal, 16)
          .padding(.bottom, 12)
          .transition(.opacity)
        }
        if let at = pasteOffer {
          // A tap anywhere else takes the offer away.
          Color.clear.contentShape(Rectangle()).ignoresSafeArea()
            .onTapGesture { withAnimation(.easeOut(duration: 0.12)) { pasteOffer = nil } }
          PasteBubble(at: at) { command = .paste }.ignoresSafeArea()
        }
        if showPages {
          ZStack {
            c.scrim.ignoresSafeArea().onTapGesture { withAnimation(.easeOut(duration: 0.16)) { showPages = false } }
            PagesOverview(
              count: pageCount, current: page, aspect: CGFloat(notebook.pageLayout.width / notebook.pageLayout.height), bridge: bridge,
              onGo: { command = .goToPage($0) },
              onAdd: { command = .addPage },
              onRearrange: { order, message in
                command = .rearrangePages(order)
                show(message)
              },
              onClose: { withAnimation(.easeOut(duration: 0.16)) { showPages = false } }
            )
          }
          .transition(.opacity)
        }
        if let card = paperCard {
          DialogOverlay(onClose: closePaperCard) {
            switch card {
            case .builtIn(let style): BuiltInPaperCard(style: style, onDone: closePaperCard)
            case .new, .edit:
              NewPaperCard(
                editing: card.editing,
                onSave: { p in
                  if card.editing != nil {
                    PaperSettings.shared.update(p)
                  } else {
                    PaperSettings.shared.add(p)
                    paperChoice.wrappedValue = .custom(p.id)
                  }
                  closePaperCard()
                }, onCancel: closePaperCard)
            }
          }
          .transition(.opacity)
        }
        if browsingShapes {
          DialogOverlay(onClose: { withAnimation(.easeOut(duration: 0.16)) { browsingShapes = false } }) {
            ShapePacksCard(picker: shapePicker, size: geo.size) { withAnimation(.easeOut(duration: 0.16)) { browsingShapes = false } }
          }
          .transition(.opacity)
        }
        if let geometry = savingShape {
          DialogOverlay(onClose: { withAnimation(.easeOut(duration: 0.16)) { savingShape = nil } }) {
            SaveShapeCard(geometry: geometry, onClose: { withAnimation(.easeOut(duration: 0.16)) { savingShape = nil } }) { name in
              let name = name.trimmingCharacters(in: .whitespacesAndNewlines)
              let shape = CustomShape(id: String(UUID().uuidString.lowercased().prefix(8)), name: name.isEmpty ? L("my_shape") : name, geometry: geometry)
              customShapesJSON = ShapeSettings.json(of: ShapeSettings.shapes(from: customShapesJSON) + [shape])
              withAnimation(.easeOut(duration: 0.16)) { savingShape = nil }
              bridge.coordinator?.clearSelection()
              show(L("saved_to_my_shapes_hold_the"))
            }
          }
          .transition(.opacity)
        }
        if let shot = screenshot {
          let close = { withAnimation(.easeOut(duration: 0.16)) { screenshot = nil } }
          DialogOverlay(onClose: close) {
            ScreenshotSheet(url: shot, onCopy: { bridge.coordinator?.copyScreenshot(shot) }, onHint: show, onDone: close)
          }
          .transition(.opacity)
        }
        if exporting {
          DialogOverlay(onClose: { withAnimation(.easeOut(duration: 0.16)) { exporting = false } }) {
            ExportCard(bridge: bridge, paged: !endless) { withAnimation(.easeOut(duration: 0.16)) { exporting = false } }
          }
          .transition(.opacity)
        }
        if savingDraft {
          DialogOverlay(onClose: { savingDraft = false }) {
            SaveDraftCard(title: title, onCancel: { savingDraft = false }) { name, folder in saveDraft(name, folder) }
          }
          .transition(.opacity)
        } else if leavingDraft {
          DialogOverlay(onClose: { leavingDraft = false }) {
            LeaveDraftCard(
              title: title, onStay: { leavingDraft = false },
              onLeave: {
                leavingDraft = false
                onBack()
                let id = notebook.id
                // After the canvas has closed (it saves its view and preview as it goes).
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) { lib.discardDraft(id) }
              },
              onSave: {
                leavingDraft = false
                savingDraft = true
              })
          }
          .transition(.opacity)
        }
        if let hint {
          VStack {
            Spacer()
            HintPill(text: hint).padding(.bottom, 64)
          }
          .allowsHitTesting(false)
        }
      }
    }
    .environment(\.tali, barColors)
    .modifier(InsertFlow(request: $inserting, bridge: bridge, hint: show))
    .onAppear {
      memos.bridge = bridge
      crop.bridge = bridge
      bridge.memosMoved = { [memos] in memos.pagesRearranged() }
      memos.load(lib.db, notebook: notebook.id)
    }
    .onDisappear { memos.leave() }
    .onChange(of: scenePhase) { _, phase in if phase == .background { memos.leave() } }
    .alert(L("rename"), isPresented: $renaming) {
      RenameField(initial: title) { name in
        lib.rename(notebook.id, to: name)
        title = lib.notebooks.first { $0.id == notebook.id }?.title ?? name
      }
    }
    .onChange(of: pens) { _, new in PenStore.save(new) }
    .onChange(of: paperColumn) { _, p in try? lib.db.run("UPDATE notebooks SET paper=? WHERE id=?", [.text(p), .text(notebook.id)]) }
    .onChange(of: tool) { old, new in
      toolBefore = old
      if case .pen(let id) = old { lastPenId = id }
      if case .pen(let id) = new { PenStore.activePen = id }
    }
    .onChange(of: eraserAffects) { _, new in PenFilters.save(new, "eraserAffects") }
    .onChange(of: dark) { _, d in try? lib.db.run("UPDATE notebooks SET dark=? WHERE id=?", [.int(d ? 1 : 0), .text(notebook.id)]) }
    .statusBarHidden(fullScreen)
    .persistentSystemOverlays(fullScreen ? .hidden : .automatic)
  }

  /// The draft becomes one of hers: the viewer leaves and the toolbars come in.
  private func saveDraft(_ name: String, _ folder: String?) {
    title = lib.saveDraft(notebook.id, title: name, folder: folder)
    savingDraft = false
    draft = false
    PhoneWriting.startWriting(notebook.id)
    withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) { viewing = false }
  }

  /// The notebook as the canvas should lay it out: A4 pages or one endless page.
  private var canvasNotebook: NotebookMeta {
    var n = notebook
    n.pages = endless ? nil : max(notebook.pages ?? 1, 1)
    return n
  }

  private var paperSpec: PaperSpec {
    let papers = PaperSettings.shared
    return papers.spec(paper, custom: papers.custom(customPaper), dark: dark)
  }

  /// What the notebook's paper column holds (Android's "custom:<id>" for one of hers).
  private var paperColumn: String { customPaper.map { CustomPaper.prefix + $0 } ?? paper.rawValue }

  /// Choosing a paper also makes it the one new notebooks start with, as on Android.
  private var paperChoice: Binding<PaperChoice> {
    Binding(
      get: { customPaper.map(PaperChoice.custom) ?? .style(paper) },
      set: { choice in
        switch choice {
        case .style(let s):
          paper = s
          customPaper = nil
          UserDefaults.standard.set(s.rawValue, forKey: "defaultPaper")
          PaperSettings.shared.defaultCustomPaper = nil
        case .custom(let id):
          paper = .BLANK
          customPaper = id
          PaperSettings.shared.defaultCustomPaper = id
        }
      })
  }

  private func openPaperCard(_ card: PaperCard) {
    moreOpen = false
    withAnimation(Ease.outStrong(duration: 0.24)) { paperCard = card }
  }

  private func closePaperCard() { withAnimation(.easeOut(duration: 0.16)) { paperCard = nil } }

  /// Hides or brings back the toolbars, with a puff of mist where each bar is: fuller when they go.
  private func setHidden(_ h: Bool) {
    let shot = BarPuff(spots: Array(bars.frames.values), strength: h ? 0.85 : 0.5)
    puff = shot
    withAnimation(Ease.outStrong(duration: 0.26)) {
      panel = nil
      hidden = h
    }
    Task { @MainActor in
      try? await Task.sleep(for: .seconds(PuffTiming.subtlePuff))
      if puff?.id == shot.id { puff = nil }
    }
  }

  /// Shows [text] for a few seconds.
  private func show(_ text: String) {
    hintTask?.cancel()
    withAnimation(Ease.outStrong(duration: 0.22)) { hint = text }
    hintTask = Task { @MainActor in
      try? await Task.sleep(for: .seconds(2.8))
      guard !Task.isCancelled else { return }
      withAnimation(.easeOut(duration: 0.2)) { hint = nil }
    }
  }

  private func pencilGesture(_ preference: PencilGesture.Preference) {
    switch PencilGesture.action(for: preference, erasing: tool == .eraser) {
    case .none: break
    case .eraser:
      panel = nil
      withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) { tool = .eraser }
    case .previousTool:
      panel = nil
      withAnimation(.spring(response: 0.3, dampingFraction: 0.82)) { tool = toolBefore ?? .pen(PenStore.activePen) }
    case .penSettings:
      if activePenIndex == nil { tool = .pen(PenStore.activePen) }
      withAnimation(Ease.outStrong(duration: 0.18)) { panel = panel == .pen ? nil : .pen }
    }
  }

  private func addPen() {
    let id = String(UUID().uuidString.prefix(8))
    guard let more = PenSlot.adding(id: id, firstColor: Palettes.shared.saved.first, to: pens) else { return }
    pens = more
    tool = .pen(id)
  }

  private func removePen(_ id: String) {
    guard let fewer = PenSlot.removing(id, from: pens), let first = fewer.first else { return }
    tool = .pen(first.id)
    pens = fewer
  }

  private var activePenIndex: Int? {
    if case .pen(let id) = tool { return pens.firstIndex { $0.id == id } }
    return nil
  }

  /// [toolsRoom]: how wide the phone's folded tools may be (the screen less the bar's margins and padding).
  private func mainToolbar(showColors: Bool, toolsRoom: CGFloat = .infinity) -> some View {
    MainToolbar(
      pens: $pens, tool: $tool, panel: $panel, shape: shapeSource, lasso: lassoShot ? .screenshot : lassoFree ? .freeForm : .lasso,
      onKeyboard: { panel = nil; command = .newText }, onInsert: { panel = nil; inserting = $0 }, memos: memos,
      onVoice: { panel = nil; memos.mic(hint: show) }, showColors: showColors, compactTools: PhoneWriting.onPhone,
      toolsRoom: PhoneWriting.narrowerForTests ?? toolsRoom, darkPaper: dark, onStripWidth: { rowWidths.strip = $0 })
  }

  private func saveShape() {
    guard let geometry = bridge.coordinator?.selectionGeometry() else { return }
    withAnimation(Ease.outStrong(duration: 0.24)) { savingShape = geometry }
  }

  private var shapeSource: ShapeSource {
    ShapeChoice(stored: lastShape).source(custom: ShapeSettings.shapes(from: customShapesJSON), kind: lastKind)
  }

  /// Choosing a shape switches to the Shapes tool and closes what it was chosen from, so she can draw it straight away.
  private var shapePicker: ShapePicker {
    ShapePicker(stored: $lastShape, kind: $lastKind, toolOn: tool == .shape) {
      tool = .shape
      withAnimation(.easeOut(duration: 0.12)) {
        panel = nil
        browsingShapes = false
      }
    }
  }

  private var shapePen: PenSlot {
    ShapeSettings.shapeSlot(active: activePenIndex.map { pens[$0] } ?? pens.first { $0.id == lastPenId }, pens: pens)
  }

  private var fingerDraws: Bool? { fingerDrawsRaw < 0 ? nil : fingerDrawsRaw == 1 }

  /// In dark mode the bars over a white page are the soft mist, as on Android.
  private var barColors: TaliColors {
    let f = ColorTheme.current
    return scheme == .dark && !dark ? f.mist : (scheme == .dark ? f.dark : f.light)
  }

  /// Back on a little cloud of its own, then undo and redo (Android's `TitlePill`).
  private var leftBars: some View {
    HStack(spacing: 10) {
      FloatingBar(crown: .little) {
        ToolIcon(material: .back, label: L("back"), action: onBack).accessibilityIdentifier("back")
      }
      if !PhoneWriting.onPhone || !viewing {
        QuickOpen(current: notebook.id, onOpen: onSwitch)
      }
      if !viewing {
        FloatingBar(crown: .softEdge) {
          ToolIcon(material: .undo, label: L("undo"), enabled: canUndo) { command = .undo }.accessibilityIdentifier("undo")
          ToolIcon(material: .redo, label: L("redo"), enabled: canRedo) { command = .redo }.accessibilityIdentifier("redo")
        }
      }
    }
  }

  /// The notebook's name with a pencil (no crown), then the eye, in the top-right corner.
  private func rightBars(showName: Bool) -> some View {
    HStack(spacing: 10) {
      if showName { nameBar }
      // Only the name gives way when the row is short (Android's `NamePill` shortens first).
      cornerBar.fixedSize().measured { rowWidths.corner = $0 }
    }
  }

  /// The notebook's name with a pencil to rename it.
  private var nameBar: some View {
        FloatingBar(crown: .none, height: 44) {
          HStack(spacing: 8) {
            Text(title).font(TaliType.titleSmall).foregroundStyle(barColors.ink).lineLimit(1).frame(maxWidth: 200, alignment: .leading)
            Image(systemName: "pencil").font(.system(size: 14)).foregroundStyle(barColors.inkFaint).fixedSize()
          }
          .padding(.leading, 10).padding(.trailing, 8).padding(.vertical, 6)
          .contentShape(Rectangle())
          .onTapGesture { renaming = true }
          .accessibilityElement(children: .combine)
          .accessibilityAddTraits(.isButton)
          .accessibilityHint(L("rename"))
          .accessibilityIdentifier("notebook-title")
        }
  }

  /// The eye and ⋯, always in the top-right corner.
  private var cornerBar: some View {
      FloatingBar(crown: .softEdge) {
        ToolIcon(system: "eye.slash", label: L("hide_toolbars")) { setHidden(true) }
          .accessibilityIdentifier("hide-toolbars")
        // ⋯ always last, in the top-right corner.
        ToolIcon(system: "ellipsis", label: L("more")) { moreOpen = true }
          .accessibilityIdentifier("more")
          .popover(isPresented: $moreOpen, arrowEdge: .top) {
            MoreMenu(
              paged: !endless,
              locked: bridge.coordinator?.lockedCount ?? 0,
              onUnlockAll: {
                moreOpen = false
                command = .unlockAll
              },
              papers: {
                PaperPicker(
                  choice: paperChoice, customs: PaperSettings.shared.customPapers, dark: dark || barColors.isDark,
                  onAdd: { openPaperCard(.new) },
                  onRemove: { PaperSettings.shared.remove($0.id) },
                  onEditCurrent: {
                    if let id = customPaper, let p = PaperSettings.shared.custom(id) { openPaperCard(.edit(p)) } else { openPaperCard(.builtIn(paper)) }
                  })
              },
              dark: $dark,
              fingerDraws: Binding(get: { fingerDraws }, set: { fingerDrawsRaw = $0 == nil ? -1 : ($0! ? 1 : 0) }),
              smartUndo: $smartUndo, between: $between,
              pageGuides: Binding(get: { PaperSettings.shared.pageGuides }, set: { PaperSettings.shared.pageGuides = $0 }), fullScreen: $fullScreen,
              onEndless: { on in
                endless = on
                command = .setEndless(on)
                show(on ? L("endless_canvas_write_anywhere") : L("a4_pages"))
              },
              onPages: {
                moreOpen = false
                withAnimation(Ease.outStrong(duration: 0.24)) { showPages = true }
              },
              onExport: {
                moreOpen = false
                withAnimation(Ease.outStrong(duration: 0.24)) { exporting = true }
              },
              onFit: {
                moreOpen = false
                command = .fitZoom
              },
              onViewOnly: {
                moreOpen = false
                ViewOnly.set(notebook.id, true)
                withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) {
                  panel = nil
                  viewing = true
                }
              }
            )
            .environment(\.tali, barColors)
            .presentationCompactAdaptation(.popover)
            // The menu's own surface (Android's white or mist menu), not the system's popover colour.
            .presentationBackground(barColors.surface)
          }
      }
  }
}

/// A floating bar (Android's `FloatingBar`): a cloud surface with 22 pt corners, 52 pt tall.
struct FloatingBar<Content: View>: View {
  var crown: CloudCrown = .none
  var height: CGFloat = 52
  @ViewBuilder var content: Content

  var body: some View {
    HStack(spacing: 0) { content }
      .padding(.horizontal, 6).padding(.vertical, 4)
      .frame(height: height)
      .cloudSurface(radius: 22, crown: crown)
  }
}

/// A 44 pt icon button with 14 pt corners and a 22 pt icon (Android's `ToolButton`).
struct ToolIcon: View {
  var system: String = ""
  /// Android's own icon (Material's), drawn 22 pt as its `ToolButton` draws it, instead of [system].
  var material: MaterialIcon? = nil
  var label: String
  var enabled = true
  var action: () -> Void
  @Environment(\.tali) private var c

  var body: some View {
    Button(action: action) {
      // A plain rectangle: with a rounded one, taps on the right half of the button before this one
      // came here instead (seen on iOS 27: half of Undo fired Redo).
      Group {
        if let material {
          MaterialIconView(icon: material, color: enabled ? c.ink : c.inkFaint.opacity(0.5), size: 22)
        } else {
          Image(systemName: system).font(.system(size: 19)).foregroundStyle(enabled ? c.ink : c.inkFaint.opacity(0.5))
        }
      }
      .frame(width: 44, height: 44).contentShape(Rectangle())
    }
    .buttonStyle(PressStyle(scale: 0.9))
    .disabled(!enabled)
    .accessibilityLabel(label)
  }
}

func penName(_ t: PenType) -> String {
  switch t {
  case .FINELINER: L("pen_fineliner")
  case .FOUNTAIN: L("pen_fountain")
  case .PENCIL: L("pen_pencil")
  case .CALLIGRAPHY: L("pen_calligraphy")
  case .HIGHLIGHTER: L("pen_highlighter")
  case .DASHED: L("pen_fineliner")
  }
}

/// Which notebooks this phone writes in straight away (Android's `phone_view` prefs): kept on the
/// phone only, never synced, so the tablet isn't affected.
@MainActor
enum PhoneWriting {
  static var onPhone: Bool { UIDevice.current.userInterfaceIdiom == .phone }

  /// On a phone everything opens for looking until Write; on a tablet a PDF, picture or scan does
  /// until she first taps Write there (Android's `writeOn`), and anything set to View only.
  static func opensViewOnly(_ id: String, kind: DocKind = .notebook) -> Bool {
    if onPhone { return !(UserDefaults.standard.stringArray(forKey: "phoneWriting") ?? []).contains(id) }
    if kind != .notebook && !(UserDefaults.standard.stringArray(forKey: "writeOn") ?? []).contains(id) { return true }
    return ViewOnly.contains(id)
  }

  /// A UI test can ask for a narrower phone than the simulator's (`-toolsRoom 300`), to see the tools page.
  static let narrowerForTests: CGFloat? = {
    guard ProcessInfo.processInfo.arguments.contains("-uitest"), let room = UserDefaults.standard.string(forKey: "toolsRoom").flatMap(Double.init) else { return nil }
    return CGFloat(room)
  }()

  static func startWriting(_ id: String) {
    let key = onPhone ? "phoneWriting" : "writeOn"
    var list = UserDefaults.standard.stringArray(forKey: key) ?? []
    if !list.contains(id) { list.append(id) }
    UserDefaults.standard.set(list, forKey: key)
  }
}

/// Items set to View only from the ⋯ menu (Android's `AppSettings.viewOnly`), on a tablet.
enum ViewOnly {
  static func contains(_ id: String) -> Bool { (UserDefaults.standard.stringArray(forKey: "viewOnly") ?? []).contains(id) }
  static func set(_ id: String, _ on: Bool) {
    var list = UserDefaults.standard.stringArray(forKey: "viewOnly") ?? []
    list.removeAll { $0 == id }
    if on { list.append(id) }
    UserDefaults.standard.set(list, forKey: "viewOnly")
  }
}

/// The pens, kept between launches.
enum PenStore {
  private static let reset: Void = {
    if ProcessInfo.processInfo.arguments.contains("-uitest"), !ProcessInfo.processInfo.arguments.contains("-keep-pens") {
      for key in ["pens", "activePen", "savedColors", "highlighterColors", "eraserAffects", "eraserPrecise", "eraserSize", "scribbleDelete", ShapeSettings.Key.autoArrow] + LassoSettings.Key.all {
        UserDefaults.standard.removeObject(forKey: key)
      }
    }
  }()

  /// A UI test starts from a new install's pens, colours, eraser and lasso unless it asks to keep them:
  /// what an earlier run left on the simulator (Freehand, another pen in use) changed what a test drew.
  static func resetForTests() { _ = reset }

  /// Android's `activePen`: the pen she last wrote with, or the first.
  static var activePen: String {
    get {
      let pens = load()
      let saved = UserDefaults.standard.string(forKey: "activePen")
      return pens.first { $0.id == saved }?.id ?? pens.first?.id ?? "p1"
    }
    set { UserDefaults.standard.set(newValue, forKey: "activePen") }
  }

  static func load() -> [PenSlot] {
    resetForTests()
    guard let d = UserDefaults.standard.data(forKey: "pens"), let p = try? JSONDecoder().decode([PenSlot].self, from: d), !p.isEmpty else {
      return PenSlot.defaults
    }
    return PenSlot.migrated(p)
  }
  static func save(_ p: [PenSlot]) {
    if let d = try? JSONEncoder().encode(p) { UserDefaults.standard.set(d, forKey: "pens") }
  }
}

/// The paper card open over the notebook: a built-in paper's, a new paper of hers, or one of hers.
enum PaperCard: Equatable {
  case builtIn(PaperStyle)
  case new
  case edit(CustomPaper)

  var editing: CustomPaper? {
    if case .edit(let p) = self { return p }
    return nil
  }
}

/// Where each toolbar last sat on screen (Android's plain map: written while bars move, so it
/// mustn't redraw anything).
/// The notebook's bars at the size they are on the owner's Android tablet. Its screen (a Galaxy
/// Tab S6 Lite: 1333 dp across, about 149 dp an inch) is as wide as an 11-inch iPad's (1180 points,
/// 132 an inch: both about 22.6 cm), but an iPad point is 13% bigger than a dp there, so the same
/// bars at the same numbers took 13% more of the screen, and the name was left only "…" (in the
/// emulator Android does the same at 1180 dp). On an iPad the bars are drawn at 132/149 of their
/// size, so they're as big as on the tablet and the row has the room it has there. A phone keeps
/// its size. Fixed for the device: nothing is resized for the name or the tools.
@MainActor
enum NotebookScale {
  static let factor: CGFloat = {
    guard UIDevice.current.userInterfaceIdiom == .pad else { return 1 }
    // An iPad mini's points are smaller (163 an inch): its bars need less taking in.
    let native = max(UIScreen.main.nativeBounds.width, UIScreen.main.nativeBounds.height)
    return native == 2266 ? 0.95 : 132.0 / 149.0
  }()

  /// One row from this many units across (any iPad held sideways, a 13-inch one upright; an
  /// 11-inch one upright has 925 and lays the row out as Android's upright tablet does, in two
  /// lines). Decided by the screen alone, so the bars' height never changes as she works.
  static let oneRowFrom: CGFloat = 1140

  static func twoRows(width: CGFloat) -> Bool { width / factor < oneRowFrom }
}

/// Lays the bars out [scale] times wider than the screen and draws them [scale] times their size, so
/// they fill it. Only where there's a scale (an iPad): on a phone the wrapper alone stopped a tap on
/// typed text with the lasso from picking it up (PhoneTests.testMemosAndText, 26 Nov 2026), so the
/// phone's bars are laid out exactly as before.
struct NotebookScaled: ViewModifier {
  let scale: CGFloat
  let width: CGFloat

  func body(content: Content) -> some View {
    if scale == 1 {
      content
    } else {
      content
        .frame(width: width / scale)
        .scaleEffect(scale, anchor: .top)
        .frame(width: width)
    }
  }
}

/// The widths of the one-row toolbar's parts (in the notebook's units), to give the name its room.
/// Android's rule, the name taking what the tools leave, gave "…" on a narrower screen; here the
/// name keeps at least what it shows on the owner's tablet ("Microeconomics Lec…") and the tools
/// fold into pages with the arrow (Android's ToolStrip) when the row is short. Nothing changes size.
struct RowWidths: Equatable {
  /// Back, quick open and undo; the whole toolbar; its tools strip as shown; the eye and ⋯; the
  /// name as wide as it would be.
  var title: CGFloat = 230
  var tools: CGFloat = 760
  var strip: CGFloat = 0
  var corner: CGFloat = 100
  var name: CGFloat = 0
  /// The name's bar (pencil included) is never shortened below this: about 130 of text,
  /// "Microeconomics L…" (Android's bar holds at most 200 of it).
  static let nameRoom: CGFloat = 170
  /// TopBarLayout's two gaps, the corner's own spacing, and the row's margins.
  static let gaps: CGFloat = 2 * 12 + 10 + 2 * 16

  /// How wide the tools strip may be on a row [width] across.
  func stripRoom(_ width: CGFloat) -> CGFloat {
    let fixed = max(tools - strip, 0)
    return max(width - Self.gaps - title - corner - min(name, Self.nameRoom) - fixed, 0)
  }

  /// How wide the tools strip may be when the toolbar has a line of its own.
  func stripRoomAlone(_ width: CGFloat) -> CGFloat { max(width - 2 * 16 - max(tools - strip, 0), 0) }
}

extension View {
  /// Reports this view's width when it changes (and isn't zero: a bar hiding keeps its last width).
  fileprivate func measured(_ width: @escaping (CGFloat) -> Void) -> some View {
    onGeometryChange(for: CGFloat.self) { $0.size.width } action: { w in if w > 0 { width(w) } }
  }
}

final class BarFrames {
  var frames: [String: CGRect] = [:]
}

/// The mist over the bars as they hide or come back.
struct BarPuff: Identifiable {
  let id = UUID()
  let start = Date()
  var spots: [CGRect]
  var strength: Double
}

struct BarPuffs: View {
  var puff: BarPuff

  var body: some View {
    GeometryReader { g in
      let origin = g.frame(in: .global).origin
      ForEach(Array(puff.spots.enumerated()), id: \.offset) { _, r in
        PuffPlayer(start: puff.start, duration: PuffTiming.subtlePuff, strength: puff.strength)
          .frame(width: r.width, height: r.height)
          .position(x: r.midX - origin.x, y: r.midY - origin.y)
      }
    }
    .allowsHitTesting(false)
  }
}

/// While the toolbars are hidden, the one thing left (Android's `ShowToolbarsButton`): a small
/// frosted circle in the corner with the eye, which brings them back.
struct ShowToolbarsButton: View {
  var action: () -> Void
  @Environment(\.tali) private var c

  var body: some View {
    Button(action: action) {
      Image(systemName: "eye").font(.system(size: 17)).foregroundStyle(c.inkMuted)
        .frame(width: 40, height: 40)
        .background(Circle().fill(c.surface.opacity(0.72)))
        .overlay(Circle().stroke(c.line, lineWidth: 1))
        .contentShape(Circle())
    }
    .buttonStyle(PressStyle(scale: 0.9))
    .accessibilityLabel(L("show_toolbars"))
    .accessibilityIdentifier("show-toolbars")
  }
}

/// A bar arriving lifts in from its own top edge, growing from 96%; the bars follow one another by
/// 40 ms and leave together, quicker (Android's `Modifier.bar`).
private struct BarLift: ViewModifier {
  var shown: Double

  func body(content: Content) -> some View {
    content.opacity(shown).scaleEffect(0.96 + 0.04 * shown, anchor: .top).offset(y: (1 - shown) * -10)
  }
}

extension View {
  fileprivate func bar(_ key: String, _ order: Int, _ frames: BarFrames) -> some View {
    onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: { frames.frames[key] = $0 }
      .transition(
        .asymmetric(
          insertion: .modifier(active: BarLift(shown: 0), identity: BarLift(shown: 1)).animation(Ease.outStrong(duration: 0.26).delay(0.04 * Double(order))),
          removal: .modifier(active: BarLift(shown: 0), identity: BarLift(shown: 1)).animation(Ease.outStrong(duration: 0.17))))
  }
}
