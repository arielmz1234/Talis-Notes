package com.tali.notes

import android.content.Context
import android.os.Build
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
  }

  /** Records an error the app survived; only shown if the app later crashes or on request. */
  fun recovered(context: Context, error: Throwable) {
    runCatching { record(error, fatal = false, versionName = versionOf(context)) }
  }

  private fun versionOf(context: Context) = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"

  private fun record(error: Throwable, fatal: Boolean, versionName: String) {
    val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
    val header = buildString {
      append(if (fatal) "CRASH" else "RECOVERED")
      append(" · ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
      append(" · Tali's Notes ").append(versionName)
      append(" · ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
      append(" · Android ").append(Build.VERSION.RELEASE)
    }
    val entry = "$header\n$trace\n"
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
