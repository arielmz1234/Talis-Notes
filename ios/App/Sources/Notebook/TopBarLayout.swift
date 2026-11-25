import SwiftUI

/// Which part of a wide notebook's top row a bar is.
enum TopBarRole { case title, tools, name, corner }

struct TopBarRoleKey: LayoutValueKey {
  static let defaultValue = TopBarRole.title
}

extension View {
  func topBarRole(_ role: TopBarRole) -> some View { layoutValue(key: TopBarRoleKey.self, value: role) }
}

/// The wide top row as Android lays it out (`NotebookScreen`): the tools in the middle when there's
/// room, shifting over rather than running into the title side when they grow, and the corner (the
/// notebook's name, the eye, ⋯) measured with only what's left, so the name shortens before anything
/// overlaps. The title and corner sit on the tools' middle line. Bars are found by role, since each
/// one comes and goes on its own while the toolbars hide or return.
struct TopBarLayout: SwiftUI.Layout {
  var gap: CGFloat = 12
  /// The tools bar's middle line, from its top.
  var mid: CGFloat = 30
  /// Narrower than this beside the tools, the name moves under the corner instead (it's always seen).
  var nameMin: CGFloat = 110

  func sizeThatFits(proposal: ProposedViewSize, subviews: LayoutSubviews, cache: inout ()) -> CGSize {
    let width = proposal.width ?? 0
    let m = measure(width, subviews)
    let top = top(m)
    let height = max(top + (m.tools?.size.height ?? 0), top + mid + (m.title?.size.height ?? 0) / 2, top + mid + m.cornerHeight / 2)
    return CGSize(width: width, height: height)
  }

  func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: LayoutSubviews, cache: inout ()) {
    let m = measure(bounds.width, subviews)
    let titleWidth = m.title?.size.width ?? 0, toolsWidth = m.tools?.size.width ?? 0
    let besideWidth = (m.corner?.size.width ?? 0) + (m.nameBeside ? (m.name?.size.width ?? 0) + gap : 0)
    let from = titleWidth + gap
    let to = max(bounds.width - besideWidth - gap - toolsWidth, from)
    let x = min(max((bounds.width - toolsWidth) / 2, from), to)
    let top = bounds.minY + top(m)
    if let t = m.title { t.view.place(at: CGPoint(x: bounds.minX, y: top + mid), anchor: .leading, proposal: ProposedViewSize(t.size)) }
    if let t = m.tools { t.view.place(at: CGPoint(x: bounds.minX + x, y: top), anchor: .topLeading, proposal: ProposedViewSize(t.size)) }
    if let c = m.corner { c.view.place(at: CGPoint(x: bounds.maxX, y: top + mid), anchor: .trailing, proposal: ProposedViewSize(c.size)) }
    if let n = m.name {
      if m.nameBeside {
        n.view.place(at: CGPoint(x: bounds.maxX - (m.corner?.size.width ?? 0) - gap, y: top + mid), anchor: .trailing, proposal: ProposedViewSize(n.size))
      } else {
        // Under the eye and ⋯, at the right edge, clear of the tools bar.
        let below = max(top + (m.tools?.size.height ?? 0), top + mid + (m.corner?.size.height ?? 0) / 2) + 8
        n.view.place(at: CGPoint(x: bounds.maxX, y: below), anchor: .topTrailing, proposal: ProposedViewSize(n.size))
      }
    }
  }

  /// A title or corner taller than the tools' middle line pushes everything down (Android's `top`).
  private func top(_ m: Measured) -> CGFloat {
    max(0, (m.title?.size.height ?? 0) / 2 - mid, m.cornerHeight / 2 - mid)
  }

  private typealias Part = (view: LayoutSubview, size: CGSize)

  private struct Measured {
    var title: Part?, tools: Part?, corner: Part?, name: Part?
    var nameBeside: Bool
    var cornerHeight: CGFloat { max(corner?.size.height ?? 0, nameBeside ? name?.size.height ?? 0 : 0) }
  }

  private func measure(_ width: CGFloat, _ subviews: LayoutSubviews) -> Measured {
    func part(_ role: TopBarRole, _ proposal: ProposedViewSize) -> Part? {
      subviews.first { $0[TopBarRoleKey.self] == role }.map { ($0, $0.sizeThatFits(proposal)) }
    }
    let title = part(.title, .unspecified)
    let tools = part(.tools, .unspecified)
    let corner = part(.corner, .unspecified)
    let room = max(width - (title?.size.width ?? 0) - (tools?.size.width ?? 0) - (corner?.size.width ?? 0) - 3 * gap, 0)
    let whole = part(.name, .unspecified)
    // The name shortens beside the tools while it can still be read; with less room it moves under the corner, whole.
    let beside = whole.map { $0.size.width <= room || room >= nameMin } ?? true
    let name = beside ? part(.name, ProposedViewSize(width: room, height: nil)) : part(.name, ProposedViewSize(width: min(whole?.size.width ?? 0, width * 0.4), height: nil))
    return Measured(title: title, tools: tools, corner: corner, name: name, nameBeside: beside)
  }
}
