import MessageUI
import SwiftUI
import UIKit

/// After an unexpected close, under the heading (Android's `CrashBanner`): the notebooks are safe,
/// and the report can go to the developer in one tap.
struct CrashBanner: View {
  var onSend: () -> Void
  var onDismiss: () -> Void
  @Environment(\.tali) private var c

  var body: some View {
    HStack(spacing: 4) {
      VStack(alignment: .leading, spacing: 2) {
        Text(L("tali_s_notes_closed_unexpectedly_last")).font(TaliType.titleSmall).foregroundStyle(c.ink)
        Text(L("your_notebooks_are_saved_sending_the")).font(TaliType.bodySmall).foregroundStyle(c.inkMuted)
      }
      .frame(maxWidth: .infinity, alignment: .leading)
      Button(action: onDismiss) {
        Text(L("dismiss")).font(TaliType.labelLarge).foregroundStyle(c.inkMuted).padding(10).contentShape(Rectangle())
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("crash-dismiss")
      Button(L("send_report"), action: onSend).buttonStyle(PrimaryButtonStyle()).accessibilityIdentifier("crash-send")
    }
    .padding(.horizontal, 18).padding(.vertical, 12)
    .background(RoundedRectangle(cornerRadius: 16).fill(LinearGradient(colors: [c.accentSoft, c.blueSoft], startPoint: .leading, endPoint: .trailing)))
    .overlay(RoundedRectangle(cornerRadius: 16).stroke(c.lilac, lineWidth: 1))
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("crash-banner")
  }
}

/// Sends the report as Android does: straight into an email to the developer, filled in, one tap on
/// Send; with no mail account, the share sheet instead.
enum CrashReportSender {
  static let address = "arielmz12344@gmail.com"

  @MainActor static func send(_ report: String) {
    guard let top = topController() else { return }
    let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
    if MFMailComposeViewController.canSendMail() {
      let mail = MFMailComposeViewController()
      mail.setToRecipients([address])
      mail.setSubject(L("tali_s_notes_crash_report") + " · " + version)
      mail.setMessageBody(report, isHTML: false)
      mail.mailComposeDelegate = MailDone.shared
      top.present(mail, animated: true)
    } else {
      let share = UIActivityViewController(activityItems: [report], applicationActivities: nil)
      share.setValue(L("tali_s_notes_crash_report"), forKey: "subject")
      share.popoverPresentationController?.sourceView = top.view
      share.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.midX, y: top.view.bounds.midY, width: 0, height: 0)
      share.popoverPresentationController?.permittedArrowDirections = []
      top.present(share, animated: true)
    }
  }

  @MainActor private static func topController() -> UIViewController? {
    let window = UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.keyWindow }.first
    var top = window?.rootViewController
    while let next = top?.presentedViewController { top = next }
    return top
  }

  @MainActor private final class MailDone: NSObject, @preconcurrency MFMailComposeViewControllerDelegate {
    static let shared = MailDone()
    func mailComposeController(_ controller: MFMailComposeViewController, didFinishWith result: MFMailComposeResult, error: Error?) {
      controller.dismiss(animated: true)
    }
  }
}
