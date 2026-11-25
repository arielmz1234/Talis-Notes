import SwiftUI
import UIKit

/// A tap anywhere that isn't a text field puts the keyboard away. The tap still reaches what's
/// under it (a button, a card); a tap in another field moves the typing there. The notebook's own
/// text boxes keep their keyboard: the canvas ends their typing itself.
struct KeyboardDismiss: UIViewRepresentable {
  func makeUIView(context: Context) -> Host { Host() }
  func updateUIView(_ v: Host, context: Context) {}

  final class Host: UIView, UIGestureRecognizerDelegate {
    private weak var installed: UIWindow?
    private lazy var tap: UITapGestureRecognizer = {
      let t = UITapGestureRecognizer(target: self, action: #selector(tapped))
      t.cancelsTouchesInView = false
      t.delaysTouchesBegan = false
      t.delaysTouchesEnded = false
      t.delegate = self
      return t
    }()

    override init(frame: CGRect) {
      super.init(frame: frame)
      isUserInteractionEnabled = false
    }

    required init?(coder: NSCoder) { fatalError() }

    override func didMoveToWindow() {
      super.didMoveToWindow()
      guard let window, window !== installed else { return }
      installed?.removeGestureRecognizer(tap)
      window.addGestureRecognizer(tap)
      installed = window
    }

    @objc private func tapped() { installed?.endEditing(true) }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
      guard Self.editingField() != nil else { return false }
      var v = touch.view
      while let view = v {
        if view is UITextField || view is UITextView || view is UISearchBar { return false }
        v = view.superview
      }
      return true
    }

    func gestureRecognizer(_ g: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

    /// The field being typed in, if it's a one-line field (SwiftUI's TextField).
    private static func editingField() -> UITextField? {
      FirstResponder.found = nil
      UIApplication.shared.sendAction(#selector(UIResponder.taliFindFirstResponder), to: nil, from: nil, for: nil)
      return FirstResponder.found as? UITextField
    }
  }
}

@MainActor private enum FirstResponder {
  static weak var found: UIResponder?
}

extension UIResponder {
  @objc fileprivate func taliFindFirstResponder() { MainActor.assumeIsolated { FirstResponder.found = self } }
}
