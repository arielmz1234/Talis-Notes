package com.tali.notes

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps a small on-device record of crashes (and of errors the canvas recovered from), so a
 * problem on the tablet can be shared from the library instead of disappearing.
 */
object CrashLog {
  private lateinit var file: File

  fun install(context: Context) {
    file = File(context.filesDir, "crash-report.txt")
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      runCatching { record(error, fatal = true, versionName = versionOf(context)) }
      previous?.uncaughtException(thread, error)
    }
    runCatching { recordExits(context) }
  }

  /**
   * Closes the handler above never sees: a crash inside native code (Ink's drawing), the app
   * frozen until Android closed it, or the system killing it while on screen. Android keeps its
   * own record of why the app ended; those since the last start go into the report.
   */
  private fun recordExits(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val am = context.getSystemService(ActivityManager::class.java) ?: return
    val seenFile = File(context.filesDir, "exits-seen")
    val now = System.currentTimeMillis()
    // First start with this: the last three days (so crashes from before an update still show).
    val seen = seenFile.takeIf { it.exists() }?.readText()?.trim()?.toLongOrNull() ?: (now - 3L * 24 * 3600 * 1000)
    val exits = am.getHistoricalProcessExitReasons(context.packageName, 0, 8).filter { it.timestamp > seen }
    seenFile.writeText(now.toString())
    val versionName = versionOf(context)
    for (e in exits.sortedBy { it.timestamp }) {
      val onScreen = e.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
      val kind =
        when (e.reason) {
          ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
          ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
          ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
          ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> if (onScreen) "closed for using too much" else null
          ApplicationExitInfo.REASON_LOW_MEMORY -> if (onScreen) "closed for low memory" else null
          ApplicationExitInfo.REASON_SIGNALED -> if (onScreen) "killed by signal ${e.status}" else null
          else -> null
        } ?: continue
      val body = buildString {
        append("Exit: ").append(kind)
        append(" · status ").append(e.status)
        append(" · pss ").append(e.pss / 1024).append(" MB · rss ").append(e.rss / 1024).append(" MB")
        e.description?.let { append("\nDescription: ").append(it) }
        // The canvas's last downs and ups: action/tool types/buttons/mode/strokes waiting for Ink.
        e.processStateSummary?.let { append("\nLast touches: ").append(String(it)) }
        trace(e)?.let { append("\n").append(it) }
      }
      write(header(true, versionName, e.timestamp) + "\n" + body + "\n", fatal = true)
    }
  }

  /** What Android kept of how it ended: the ANR's thread dump, or the readable text of a native tombstone. */
  @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
  private fun trace(e: ApplicationExitInfo): String? {
    val bytes = runCatching { e.traceInputStream?.use { it.readUpTo(256 * 1024) } }.getOrNull() ?: return null
    return if (e.reason == ApplicationExitInfo.REASON_ANR) {
      // The main thread first: that's what froze.
      val text = String(bytes, Charsets.UTF_8)
      val main = text.indexOf("\"main\"").takeIf { it >= 0 } ?: 0
      text.substring(main).take(6000)
    } else {
      // A tombstone is a protobuf: its words (the abort message, libraries, function names) are
      // plain text inside it.
      val words = ArrayList<String>()
      val run = StringBuilder()
      fun flush() {
        if (run.length >= 5 && (words.isEmpty() || words.last() != run.toString())) words += run.toString()
        run.setLength(0)
      }
      for (b in bytes) {
        val c = b.toInt() and 0xff
        if (c in 0x20..0x7e) run.append(c.toChar()) else flush()
      }
      flush()
      words.joinToString("\n").take(8000)
    }
  }

  private fun java.io.InputStream.readUpTo(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < max) {
      val n = read(buf, 0, minOf(buf.size, max - out.size()))
      if (n < 0) break
      out.write(buf, 0, n)
    }
    return out.toByteArray()
  }

  /** Records an error the app survived; only shown if the app later crashes or on request. */
  fun recovered(context: Context, error: Throwable) {
    Log.e("TaliNotes", "Recovered from an error", error)
    runCatching { record(error, fatal = false, versionName = versionOf(context)) }
  }

  private fun versionOf(context: Context) = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

  private fun header(fatal: Boolean, versionName: String, time: Long = System.currentTimeMillis()) = buildString {
    append(if (fatal) "CRASH" else "RECOVERED")
    append(" · ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time)))
    append(" · Tali's Notes ").append(versionName)
    append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
    append(" · Android ").append(Build.VERSION.RELEASE)
    if (desktop) append(" · DeX")
  }

  /** Set while the app runs in Samsung DeX (or another desktop mode), for the report's header. */
  @Volatile var desktop = false

  private fun record(error: Throwable, fatal: Boolean, versionName: String) {
    val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
    write("${header(fatal, versionName)}\n$trace\n", fatal)
  }

  private fun write(entry: String, fatal: Boolean) {
    val existing = if (file.exists()) file.readText() else ""
    // Keep the newest few entries only.
    val entries = (entry + existing).split("\n(?=CRASH|RECOVERED)".toRegex()).take(6)
    file.writeText(entries.joinToString("\n"))
    if (fatal) File(file.parentFile, "crash-pending").writeText("1")
  }

  /** The report to offer after an unexpected close, or null when the last run ended normally. */
  fun pendingReport(context: Context): String? {
    val flag = File(context.filesDir, "crash-pending")
    if (!flag.exists()) return null
    return File(context.filesDir, "crash-report.txt").takeIf { it.exists() }?.readText()
  }

  fun dismiss(context: Context) {
    File(context.filesDir, "crash-pending").delete()
  }
}
