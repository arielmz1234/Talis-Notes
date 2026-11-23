package com.tali.notes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Files that come to the app from elsewhere: a PDF or picture opened with Tali's Notes or shared to
 * it (from Files, Drive, Gmail, WhatsApp, the gallery), or her backup file tapped in Files or Drive.
 * [offer] holds them until the app has opened them (`MainNavigation`) or asked to restore a backup.
 */
object Incoming {
  enum class Kind { PDF, IMAGE, BACKUP }

  data class Files(val kind: Kind, val uris: List<Uri>, val name: String?)

  val offer = MutableStateFlow<Files?>(null)

  /** Reads an "open with" or "share" intent; false when it isn't one with files the app takes. */
  fun receive(context: Context, intent: Intent?): Boolean {
    intent ?: return false
    val uris =
      when (intent.action) {
        Intent.ACTION_VIEW -> listOfNotNull(intent.data)
        Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java) ?: intent.clipData?.uris()?.firstOrNull())
        Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.toList() ?: intent.clipData?.uris().orEmpty()
        else -> emptyList()
      }
    val files = classify(context, uris, intent.type) ?: return false
    offer.value = files
    return true
  }

  /** Sorts [uris] into one kind: PDFs first, then pictures, else a backup zip. Others are left out. */
  fun classify(context: Context, uris: List<Uri>, fallbackType: String? = null): Files? {
    if (uris.isEmpty()) return null
    val typed = uris.map { it to kindOf(context, it, fallbackType) }
    val kind = listOf(Kind.PDF, Kind.IMAGE, Kind.BACKUP).firstOrNull { k -> typed.any { it.second == k } } ?: return null
    val picked = typed.filter { it.second == kind }.map { it.first }.let { if (kind == Kind.BACKUP) it.take(1) else it }
    return Files(kind, picked, nameOf(context, picked.first()))
  }

  fun kindOf(context: Context, uri: Uri, fallbackType: String? = null): Kind? {
    val type = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: fallbackType.orEmpty()
    val name = nameOf(context, uri).orEmpty().lowercase()
    return when {
      type == "application/pdf" || name.endsWith(".pdf") -> Kind.PDF
      type.startsWith("image/") && !type.contains("svg") -> Kind.IMAGE
      type.contains("zip") || name.endsWith(".zip") -> Kind.BACKUP
      else -> null
    }
  }

  fun nameOf(context: Context, uri: Uri): String? =
    runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } }
      .getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.contains('.') }

  private fun ClipData.uris(): List<Uri> = (0 until itemCount).mapNotNull { getItemAt(it).uri }
}
