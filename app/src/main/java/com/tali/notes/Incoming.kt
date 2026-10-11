package com.tali.notes

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Files that come to the app from elsewhere: a PDF or picture opened with Tali's Notes or shared to
 * it (from Files, Drive, Gmail, WhatsApp, the gallery), or her backup file tapped in Files or Drive.
 * [offer] waits for her to choose where it goes (the card in [IncomingCard]); [forNotebook] holds
 * what a notebook should add once it's open.
 */
object Incoming {
  enum class Kind { PDF, IMAGE, BACKUP }

  data class Files(val kind: Kind, val uris: List<Uri>, val name: String?)

  /** Waiting for her to say where it goes. */
  val offer = MutableStateFlow<Files?>(null)

  /** To add to a notebook once it's open, by notebook id. */
  val forNotebook = MutableStateFlow<Map<String, Files>>(emptyMap())

  fun sendTo(notebookId: String, files: Files) = forNotebook.update { it + (notebookId to files) }

  fun take(notebookId: String): Files? {
    var taken: Files? = null
    forNotebook.update {
      taken = it[notebookId]
      it - notebookId
    }
    return taken
  }

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

  /**
   * What she'd call it: "Lecture 3" for "Lecture 3.pdf"; "3 pictures" for several. Pictures are
   * never named after their file ("IMG_20261011_094112" isn't a notebook name).
   */
  fun title(files: Files): String =
    when {
      files.kind == Kind.IMAGE -> if (files.uris.size > 1) tr(R.string.n_pictures, files.uris.size) else tr(R.string.picture)
      files.uris.size > 1 -> tr(R.string.n_pdfs, files.uris.size)
      else -> files.name?.takeIf { it.contains('.') }?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "PDF"
    }
}
