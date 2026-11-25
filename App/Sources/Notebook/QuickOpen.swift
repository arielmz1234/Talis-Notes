import SwiftUI
import TaliCore

/// Quick open (Android's `QuickOpen`): a little cloud with two puffs beside Back (so the two little
/// clouds side by side don't match), opening the notebooks she opened last, topped up with the most
/// recently edited, up to eight. One tap swaps the open notebook; Back still goes to the library.
struct QuickOpen: View {
  var current: String
  var onOpen: (NotebookMeta) -> Void
  @Environment(Library.self) private var lib
  @Environment(\.tali) private var c
  @State private var open = false

  static let recentMax = 8

  private var recent: [NotebookMeta] {
    let byId = Dictionary(uniqueKeysWithValues: lib.notebooks.map { ($0.id, $0) })
    let opened = lib.opened.filter { $0 != current }.compactMap { byId[$0] }
    let openedIds = Set(opened.map(\.id))
    let rest = lib.notebooks.sorted { $0.updated > $1.updated }.filter { $0.id != current && !openedIds.contains($0.id) }
    return Array((opened + rest).prefix(QuickOpen.recentMax))
  }

  var body: some View {
    FloatingBar(crown: .littleTwin) {
      Button { open = true } label: {
        RecentNotebookIcon(color: c.ink).frame(width: 44, height: 44).contentShape(Rectangle())
      }
      .buttonStyle(PressStyle(scale: 0.9))
      .accessibilityLabel(L("recent_notebooks"))
      .accessibilityIdentifier("quick-open")
    }
    .popover(isPresented: $open, arrowEdge: .top) {
      VStack(alignment: .leading, spacing: 0) {
        Text(L("recent_notebooks").uppercased()).font(TaliType.labelSmall).tracking(0.66).foregroundStyle(c.inkFaint)
          .padding(.horizontal, 16).padding(.top, 10).padding(.bottom, 6)
        let list = recent
        if list.isEmpty {
          Text(L("notebooks_you_open_show_up_here")).font(TaliType.bodySmall).foregroundStyle(c.inkMuted)
            .frame(maxWidth: 260, alignment: .leading).padding(.horizontal, 16).padding(.bottom, 10)
        }
        ForEach(Array(list.enumerated()), id: \.element.id) { i, nb in
          QuickOpenRow(notebook: nb, order: i) {
            open = false
            onOpen(nb)
          }
        }
      }
      .padding(.bottom, 6)
      .frame(minWidth: 260, maxWidth: 340)
      .environment(\.tali, c)
      .presentationCompactAdaptation(.popover)
      .presentationBackground(c.surface)
    }
  }
}

/// One recent notebook: its page with the cover-colour spine, its name and when it was edited.
/// Rows drift in 30 ms apart, as on Android.
private struct QuickOpenRow: View {
  var notebook: NotebookMeta
  var order: Int
  var action: () -> Void
  @Environment(\.tali) private var c
  @State private var shown = false

  var body: some View {
    Button(action: action) {
      HStack(spacing: 12) {
        ZStack(alignment: .leading) {
          if let thumb = ThumbnailCache.shared.image(id: notebook.id, version: notebook.thumbVersion) {
            // Its first page with what's on it, from the top, as the library's card.
            Color.clear.overlay(alignment: .top) { Image(uiImage: thumb).resizable().scaledToFill() }.clipped()
          } else {
            PaperPreview(
              style: notebook.paper, landscape: notebook.landscape, dark: notebook.dark, zoom: 1.6,
              custom: PaperSettings.shared.custom(notebook.customPaper))
          }
          if notebook.kind == .notebook { Rectangle().fill(Color(argb: notebook.cover)).frame(width: 4) }
        }
        .frame(width: 52, height: 38)
        .clipShape(RoundedRectangle(cornerRadius: 7))
        .overlay(RoundedRectangle(cornerRadius: 7).stroke(c.line, lineWidth: 1))
        VStack(alignment: .leading, spacing: 0) {
          Text(notebook.title.isEmpty ? L("untitled") : notebook.title).font(TaliType.titleSmall).foregroundStyle(c.ink).lineLimit(1)
          let word = relativeTime(notebook.updated)
          Text(L("edited") + word.prefix(1).lowercased() + word.dropFirst()).font(TaliType.bodySmall).foregroundStyle(c.inkFaint).lineLimit(1)
        }
        Spacer(minLength: 0)
      }
      .padding(.horizontal, 12).padding(.vertical, 6)
      .contentShape(Rectangle())
    }
    .buttonStyle(.plain).contentShape(Rectangle())
    .accessibilityLabel(L("open_n", notebook.title))
    .opacity(shown ? 1 : 0)
    .offset(y: shown ? 0 : -10)
    .onAppear {
      withAnimation(Ease.outStrong(duration: 0.24).delay(Double(min(order, 6)) * 0.03)) { shown = true }
    }
  }
}

/// A notebook with a clock beside it (Android's `TaliIcons.RecentNotebook`), on its 24-unit grid with
/// the same 1.8 stroke and round ends.
struct RecentNotebookIcon: View {
  var color: Color
  var size: CGFloat = 22

  var body: some View {
    Canvas { ctx, canvasSize in
      let k = canvasSize.width / 24
      func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * k, y: y * k) }
      var p = Path()
      // The notebook: its outline with rounded corners, open at the bottom right where the clock sits.
      p.move(to: pt(10.4, 19))
      p.addLine(to: pt(5, 19))
      p.addArc(center: pt(5, 17.5), radius: 1.5 * k, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
      p.addLine(to: pt(3.5, 4))
      p.addArc(center: pt(5, 4), radius: 1.5 * k, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
      p.addLine(to: pt(13, 2.5))
      p.addArc(center: pt(13, 4), radius: 1.5 * k, startAngle: .degrees(270), endAngle: .degrees(360), clockwise: false)
      p.addLine(to: pt(14.5, 10.9))
      // Its spine and two lines of writing.
      p.move(to: pt(6.6, 2.5)); p.addLine(to: pt(6.6, 19))
      p.move(to: pt(9, 6.6)); p.addLine(to: pt(11.9, 6.6))
      p.move(to: pt(9, 9.6)); p.addLine(to: pt(11, 9.6))
      // The clock: most of a circle round (17, 17) with an arrowhead at its start, and its hands.
      p.move(to: pt(12.47, 14.89))
      p.addArc(center: pt(17, 17), radius: 5 * k, startAngle: .degrees(205), endAngle: .degrees(155), clockwise: false)
      p.move(to: pt(14.35, 14.21)); p.addLine(to: pt(12.47, 14.89)); p.addLine(to: pt(11.79, 13.01))
      p.move(to: pt(17, 14.6)); p.addLine(to: pt(17, 17)); p.addLine(to: pt(18.7, 18.1))
      ctx.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: 1.8 * k, lineCap: .round, lineJoin: .round))
    }
    .frame(width: size, height: size)
  }
}
