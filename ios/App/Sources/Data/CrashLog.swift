import Darwin
import Foundation
import MetricKit
import UIKit

/// Keeps a small on-device record of crashes (and of errors the app recovered from), so a problem on
/// the tablet can be sent from the library instead of disappearing (Android's `CrashLog`).
///
/// What it catches, as Android's uncaught-exception handler and exit reasons do:
/// - an Objective-C exception nobody caught;
/// - a fatal signal (a Swift trap, a bad memory access): written with only async-signal-safe calls
///   to a file opened beforehand, and turned into a report on the next start;
/// - the app closed while on screen with neither of those seen (frozen until iOS closed it, or out
///   of memory): a marker kept while it's in front is still there on the next start;
/// - Apple's own crash and hang reports (MetricKit), which arrive on a later start.
final class CrashLog: NSObject, MXMetricManagerSubscriber, @unchecked Sendable {
  static let shared = CrashLog()

  private let lock = NSLock()
  private var folder: URL { FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("CrashLog", isDirectory: true) }
  private var reportFile: URL { folder.appendingPathComponent("crash-report.txt") }
  private var pendingFile: URL { folder.appendingPathComponent("crash-pending") }
  private var signalFile: URL { folder.appendingPathComponent("crash-signal.txt") }
  private var onScreenFile: URL { folder.appendingPathComponent("on-screen") }

  func install() {
    try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    let args = ProcessInfo.processInfo.arguments
    if args.contains("-uitest") {
      // Each UI test ends by killing the app on screen: those runs start with a clean record.
      for f in [reportFile, pendingFile, signalFile, onScreenFile] { try? FileManager.default.removeItem(at: f) }
      if args.contains("-crashReport") { write(header(fatal: true) + "\nExit: a test report (-crashReport)\n", fatal: true) }
    } else {
      collectLastRun()
    }
    NSSetUncaughtExceptionHandler { e in
      CrashLog.shared.record("\(e.name.rawValue): \(e.reason ?? "")\n" + e.callStackSymbols.joined(separator: "\n"), fatal: true)
      CrashLog.shared.leftTheScreen()
    }
    installSignalHandlers()
    MXMetricManager.shared.add(self)
    let center = NotificationCenter.default
    center.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { _ in CrashLog.shared.cameOnScreen() }
    for name in [UIApplication.didEnterBackgroundNotification, UIApplication.willTerminateNotification] {
      center.addObserver(forName: name, object: nil, queue: .main) { _ in CrashLog.shared.leftTheScreen() }
    }
  }

  /// Records an error the app survived; only shown if the app later crashes.
  func recovered(_ error: Error) {
    NSLog("Tali's Notes recovered from an error: %@", String(describing: error))
    record(String(describing: error), fatal: false)
  }

  /// The report to offer after an unexpected close, or nil when the last run ended normally.
  func pendingReport() -> String? {
    guard FileManager.default.fileExists(atPath: pendingFile.path) else { return nil }
    return try? String(contentsOf: reportFile, encoding: .utf8)
  }

  func dismiss() {
    try? FileManager.default.removeItem(at: pendingFile)
  }

  // MARK: The last run

  /// What the last run left behind: a signal's record, or the on-screen marker of a run that never
  /// left the screen properly.
  private func collectLastRun() {
    let fm = FileManager.default
    var caught = false
    if let text = try? String(contentsOf: signalFile, encoding: .utf8), !text.isEmpty {
      caught = true
      // "SIGNAL <number> <seconds since 1970>" and then the backtrace.
      let lines = text.split(separator: "\n", omittingEmptySubsequences: false)
      let parts = lines.first?.split(separator: " ") ?? []
      let number = parts.count > 1 ? Int32(parts[1]) ?? 0 : 0
      let when = parts.count > 2 ? Double(parts[2]).map { Date(timeIntervalSince1970: $0) } : nil
      let name = String(cString: strsignal(number))
      write(header(fatal: true, at: when ?? Date()) + "\nExit: signal \(number) (\(name))\n" + lines.dropFirst().joined(separator: "\n").prefix(8000) + "\n", fatal: true)
    }
    try? fm.removeItem(at: signalFile)
    if fm.fileExists(atPath: onScreenFile.path) {
      let when = (try? fm.attributesOfItem(atPath: onScreenFile.path)[.modificationDate] as? Date) ?? Date()
      if !caught {
        write(header(fatal: true, at: when) + "\nExit: closed while on screen (frozen until iOS closed it, out of memory, or a crash nothing else saw)\n", fatal: true)
      }
      try? fm.removeItem(at: onScreenFile)
    }
  }

  fileprivate func cameOnScreen() {
    FileManager.default.createFile(atPath: onScreenFile.path, contents: Data())
  }

  fileprivate func leftTheScreen() {
    try? FileManager.default.removeItem(at: onScreenFile)
  }

  // MARK: Signals

  private func installSignalHandlers() {
    signalFile.path.withCString { CrashLog.signalPath = strdup($0) }
    onScreenFile.path.withCString { CrashLog.onScreenPath = strdup($0) }
    CrashLog.lineBuffer = .allocate(capacity: 64)
    CrashLog.frameBuffer = .allocate(capacity: Int(CrashLog.frameCount))
    for s in CrashLog.signals {
      var action = sigaction()
      action.__sigaction_u.__sa_handler = crashLogSignal
      sigemptyset(&action.sa_mask)
      action.sa_flags = 0
      sigaction(s, &action, nil)
    }
  }

  static let signals: [Int32] = [SIGABRT, SIGSEGV, SIGBUS, SIGILL, SIGTRAP, SIGFPE]
  nonisolated(unsafe) static var signalPath: UnsafeMutablePointer<CChar>?
  nonisolated(unsafe) static var onScreenPath: UnsafeMutablePointer<CChar>?
  static let frameCount: Int32 = 64
  nonisolated(unsafe) static var lineBuffer: UnsafeMutablePointer<UInt8>?
  nonisolated(unsafe) static var frameBuffer: UnsafeMutablePointer<UnsafeMutableRawPointer?>?

  // MARK: MetricKit

  func didReceive(_ payloads: [MXDiagnosticPayload]) {
    for payload in payloads {
      for d in payload.crashDiagnostics ?? [] {
        var kind = "crash"
        if let t = d.exceptionType { kind += " · exception type \(t)" }
        if let s = d.signal { kind += " · signal \(s)" }
        if let r = d.terminationReason { kind += " · \(r)" }
        if let m = d.exceptionReason?.composedMessage { kind += "\nReason: \(m)" }
        write(header(fatal: true, at: payload.timeStampEnd, version: d.metaData.applicationBuildVersion) + "\nExit (Apple's report): \(kind)\n" + stack(d.callStackTree) + "\n", fatal: true)
      }
      for d in payload.hangDiagnostics ?? [] {
        let seconds = d.hangDuration.converted(to: .seconds).value
        write(header(fatal: true, at: payload.timeStampEnd, version: d.metaData.applicationBuildVersion) + "\nExit (Apple's report): not responding for \(String(format: "%.1f", seconds)) s\n" + stack(d.callStackTree) + "\n", fatal: true)
      }
    }
  }

  private func stack(_ tree: MXCallStackTree) -> String {
    String(data: tree.jsonRepresentation(), encoding: .utf8).map { String($0.prefix(6000)) } ?? ""
  }

  // MARK: The file

  private func header(fatal: Bool, at time: Date = Date(), version: String? = nil) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "en_US_POSIX")
    f.dateFormat = "yyyy-MM-dd HH:mm:ss"
    let app = version ?? (Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?")
    var info = utsname()
    uname(&info)
    let model = withUnsafeBytes(of: &info.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
    return "\(fatal ? "CRASH" : "RECOVERED") · \(f.string(from: time)) · Tali's Notes \(app) · Apple \(model) · iOS \(UIDevice.current.systemVersion)"
  }

  private func record(_ trace: String, fatal: Bool) {
    write(header(fatal: fatal) + "\n" + trace + "\n", fatal: fatal)
  }

  private func write(_ entry: String, fatal: Bool) {
    lock.lock()
    defer { lock.unlock() }
    let existing = (try? String(contentsOf: reportFile, encoding: .utf8)) ?? ""
    // Keep the newest few entries only.
    let entries = (entry + existing).components(separatedBy: "\n").reduce(into: [String]()) { out, line in
      if out.isEmpty || line.hasPrefix("CRASH · ") || line.hasPrefix("RECOVERED · ") { out.append(line) } else { out[out.count - 1] += "\n" + line }
    }
    try? entries.prefix(6).joined(separator: "\n").write(to: reportFile, atomically: true, encoding: .utf8)
    if fatal { FileManager.default.createFile(atPath: pendingFile.path, contents: Data("1".utf8)) }
  }
}

/// A fatal signal: what it was, when, and the backtrace, written to the file opened by path with
/// async-signal-safe calls only (no allocating: the buffers are made at install); then the signal
/// goes on to end the app as it would have.
private func crashLogSignal(_ number: Int32) {
  if let path = CrashLog.signalPath, let line = CrashLog.lineBuffer, let frames = CrashLog.frameBuffer {
    let fd = open(path, O_WRONLY | O_CREAT | O_TRUNC, 0o644)
    if fd >= 0 {
      var n = 0
      for b in "SIGNAL ".utf8 {
        line[n] = b
        n += 1
      }
      appendDigits(Int(number), line, &n)
      line[n] = UInt8(ascii: " ")
      n += 1
      appendDigits(time(nil), line, &n)
      line[n] = UInt8(ascii: "\n")
      n += 1
      _ = Darwin.write(fd, line, n)
      backtrace_symbols_fd(frames, backtrace(frames, CrashLog.frameCount), fd)
      close(fd)
    }
  }
  // Caught: the on-screen marker isn't needed to tell it apart.
  if let marker = CrashLog.onScreenPath { unlink(marker) }
  signal(number, SIG_DFL)
  raise(number)
}

/// [value]'s digits into [out] from [n] (at most 20, all a 64-bit number has).
private func appendDigits(_ value: Int, _ out: UnsafeMutablePointer<UInt8>, _ n: inout Int) {
  var v = max(value, 0), count = 0, rest = v
  repeat {
    count += 1
    rest /= 10
  } while rest > 0
  for i in stride(from: count - 1, through: 0, by: -1) {
    out[n + i] = UInt8(ascii: "0") + UInt8(v % 10)
    v /= 10
  }
  n += count
}
