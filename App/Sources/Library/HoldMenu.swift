import SwiftUI
import TaliCore

/// A notebook's hold menu (Android's card menu): the app's own small card beside the notebook,
/// instead of the system's, which dimmed the screen and lifted a second copy of the card over the first.
struct HoldMenu<Content: View>: View {
  @ViewBuilder var content: Content
  @Environment(\.tali) private var c

  var body: some View {
    VStack(alignment: .leading, spacing: 0) { content }
      .padding(.horizontal, 8).padding(.vertical, 8)
      .frame(width: 280)
      .environment(\.tali, c)
      .presentationCompactAdaptation(.popover)
      .presentationBackground(c.surface)
  }
}

/// One action in a hold menu: an icon and its name; a warning one in red.
struct HoldMenuRow: View {
  var icon: String
  var label: String
  var warning = false
  var action: () -> Void
  @Environment(\.tali) private var c

  var body: some View {
    Button(action: action) {
      HStack(spacing: 12) {
        Image(systemName: icon).font(.system(size: 16)).foregroundStyle(warning ? c.danger : c.inkMuted).frame(width: 20)
        Text(label).font(TaliType.bodyMedium).foregroundStyle(warning ? c.danger : c.ink).lineLimit(1)
        Spacer(minLength: 0)
      }
      .padding(.horizontal, 8).frame(minHeight: 42).contentShape(Rectangle())
    }
    .buttonStyle(HoldRowStyle())
    .accessibilityLabel(label)
  }
}

private struct HoldRowStyle: ButtonStyle {
  @Environment(\.tali) private var c
  func makeBody(configuration: Configuration) -> some View {
    configuration.label
      .background(RoundedRectangle(cornerRadius: 10).fill(configuration.isPressed ? c.accentSoft.opacity(0.7) : .clear))
  }
}

/// The cover colours in a hold menu: a row of dots, the current one ringed.
struct HoldMenuColours: View {
  var current: UInt32
  var onPick: (UInt32) -> Void
  @Environment(\.tali) private var c

  var body: some View {
    let columns = Array(repeating: GridItem(.fixed(26), spacing: 6), count: 8)
    LazyVGrid(columns: columns, alignment: .leading, spacing: 6) {
      ForEach(Covers.all, id: \.self) { col in
        let on = col | 0xFF00_0000 == current | 0xFF00_0000
        Circle().fill(Color(argb: col)).frame(width: 22, height: 22)
          .overlay(Circle().stroke(on ? c.accent : .clear, lineWidth: 2).padding(-3))
          .frame(width: 26, height: 26)
          .contentShape(Circle())
          .onTapGesture { onPick(col) }
          .accessibilityLabel(L("colour"))
          .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
      }
    }
    .padding(.horizontal, 8).padding(.vertical, 6)
  }
}

/// Pressing and holding a card (or a page): it sinks a touch while held and comes back up as its
/// menu opens; nothing else on the screen moves or darkens.
struct HoldPress: ViewModifier {
  var enabled = true
  var onHold: () -> Void
  @GestureState private var pressing = false

  func body(content: Content) -> some View {
    content
      .scaleEffect(pressing ? 0.96 : 1)
      .animation(.easeOut(duration: pressing ? 0.35 : 0.2), value: pressing)
      .simultaneousGesture(
        LongPressGesture(minimumDuration: 0.45)
          .updating($pressing) { value, state, _ in state = value }
          .onEnded { _ in
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            onHold()
          },
        including: enabled ? .all : .subviews)
  }
}
