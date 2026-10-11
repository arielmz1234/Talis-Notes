package com.tali.notes.backup

import com.tali.notes.tr
import com.tali.notes.R
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.tali.notes.CrashLog
import com.tali.notes.data.NotesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Restoring from several backup files at once: the files she picked, or every backup in a folder
 * she picked (the "Tali's Notes" folder of one file per notebook). Each is merged in like a single
 * backup; the settings file goes first so folders exist before the notebooks that sit in them.
 */
object RestoreFiles {
  /** Every backup file in [tree] (a folder picked with the system's folder picker) and the folders inside it. */
  suspend fun inFolder(context: Context, tree: Uri): List<Uri> =
    withContext(Dispatchers.IO) {
      val out = mutableListOf<Uri>()
      fun walk(docId: String, depth: Int) {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val cols = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
          while (c.moveToNext()) {
            val id = c.getString(0) ?: continue
            val name = c.getString(1).orEmpty()
            val mime = c.getString(2).orEmpty()
            if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
              if (depth < 2) walk(id, depth + 1)
            } else if (isBackupName(name, mime)) {
              out += DocumentsContract.buildDocumentUriUsingTree(tree, id)
            }
          }
        }
      }
      walk(DocumentsContract.getTreeDocumentId(tree), 0)
      out
    }

  /**
   * Restores each of [uris] in turn (settings first, anything that isn't a backup skipped) and adds
   * up what came back. Throws [BackupArchive.InvalidBackup] only when none of them was a backup.
   */
  suspend fun restore(context: Context, uris: List<Uri>): NotesRepository.MergeResult {
    val named = withContext(Dispatchers.IO) { uris.map { it to nameOf(context, it) } }
    val ordered = named.filterNot { (_, n) -> n.endsWith(".pdf", ignoreCase = true) }.sortedBy { (_, n) -> if (n.contains("settings", ignoreCase = true)) 0 else 1 }
    if (ordered.isEmpty()) throw BackupArchive.InvalidBackup(tr(R.string.there_s_no_backup_file_there))
    var total = NotesRepository.MergeResult(0, 0, 0, emptySet())
    var restored = 0
    var lastProblem: BackupArchive.InvalidBackup? = null
    for ((uri, _) in ordered) {
      try {
        val r = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri) }?.use { BackupArchive.restore(context, it) } ?: continue
        total = NotesRepository.MergeResult(total.added + r.added, total.updated + r.updated, total.kept + r.kept, total.changed + r.changed)
        restored++
      } catch (e: BackupArchive.InvalidBackup) {
        // Another file that happened to be in the folder: skip it and carry on with the rest.
        lastProblem = e
      } catch (t: Throwable) {
        CrashLog.recovered(context, t)
      }
    }
    if (restored == 0) throw lastProblem ?: BackupArchive.InvalidBackup(tr(R.string.those_files_couldn_t_be_restored))
    return total
  }

  private fun isBackupName(name: String, mime: String) =
    name.endsWith(".zip", ignoreCase = true) || mime == BackupArchive.MIME || mime == "application/x-zip-compressed"

  private fun nameOf(context: Context, uri: Uri): String =
    runCatching {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull().orEmpty()
}
