import SwiftUI

/// Which part of a wide notebook's top row a bar is.
enum TopBarRole { case title, tools, corner }

struct TopBarRoleKey: LayoutValueKey {
  static let defaultValue = TopBarRole.title
}

extension View {
  func topBarRole(_ role: TopBarRole) -> some View { layoutValue(key: TopBarRoleKey.self, value: role) }
}

/// The wide top row exactly as Android lays it out (`NotebookScreen`'s top `Layout`): back, quick
/// open and undo on the left; the notebook's name with the eye and ⋯ in the right corner; the tools
/// in the middle when there's room. Nothing changes size for the name: the corner is measured with
/// only the room the title and tools leave it (less two gaps), so the name shortens ("…") when
/// space is short, and the tools slide over between the title and the corner rather than overlap
/// either. Everything sits on the tools bar's middle line. Bars are found by role, since each one
/// comes and goes on its own while the toolbars hide or return.
struct TopBarLayout: SwiftUI.Layout {
  var gap: CGFloat = 12
  /// The tools bar's middle line, from its top (Android's `MAIN_TOOLBAR_HEIGHT / 2`).
  var mid: CGFloat = 30

  func sizeThatFits(proposal: ProposedViewSize, subviews: LayoutSubviews, cache: inout ()) -> CGSize {
    let width = proposal.width ?? 0
    let m = measure(width, subviews)
    let top = top(m)
    let height = max(top + (m.tools?.size.height ?? 0), top + mid + (m.title?.size.height ?? 0) / 2, top + mid + (m.corner?.size.height ?? 0) / 2)
    return CGSize(width: width, height: height)
  }

  func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: LayoutSubviews, cache: inout ()) {
    let m = measure(bounds.width, subviews)
    let titleWidth = m.title?.size.width ?? 0, toolsWidth = m.tools?.size.width ?? 0, cornerWidth = m.corner?.size.width ?? 0
    let from = titleWidth + gap
    let to = max(bounds.width - cornerWidth - gap - toolsWidth, from)
    let x = min(max((bounds.width - toolsWidth) / 2, from), to)
    let top = bounds.minY + top(m)
    if let t = m.title { t.view.place(at: CGPoint(x: bounds.minX, y: top + mid), anchor: .leading, proposal: ProposedViewSize(t.size)) }
    if let t = m.tools { t.view.place(at: CGPoint(x: bounds.minX + x, y: top), anchor: .topLeading, proposal: ProposedViewSize(t.size)) }
    if let c = m.corner { c.view.place(at: CGPoint(x: bounds.maxX, y: top + mid), anchor: .trailing, proposal: ProposedViewSize(c.size)) }
  }

  /// A title or corner taller than the tools' middle line pushes everything down (Android's `top`).
  private func top(_ m: Measured) -> CGFloat {
    max(0, (m.title?.size.height ?? 0) / 2 - mid, (m.corner?.size.height ?? 0) / 2 - mid)
  }

  private typealias Part = (view: LayoutSubview, size: CGSize)

  private struct Measured {
    var title: Part?, tools: Part?, corner: Part?
  }

  private func measure(_ width: CGFloat, _ subviews: LayoutSubviews) -> Measured {
    func part(_ role: TopBarRole, _ proposal: ProposedViewSize) -> Part? {
      subviews.first { $0[TopBarRoleKey.self] == role }.map { ($0, $0.sizeThatFits(proposal)) }
    }
    let title = part(.title, .unspecified)
    let tools = part(.tools, .unspecified)
    // The name in the corner gives way when space is short.
    let room = max(width - (title?.size.width ?? 0) - (tools?.size.width ?? 0) - 2 * gap, 0)
    let corner = part(.corner, ProposedViewSize(width: room, height: nil))
    return Measured(title: title, tools: tools, corner: corner)
  }
}
