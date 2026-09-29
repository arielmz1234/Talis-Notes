package com.tali.notes.backup

import android.content.Context
import com.tali.notes.data.ItemCodec
import com.tali.notes.data.NotesDb
import com.tali.notes.data.NotesRepository
import com.tali.notes.data.SettingsStore
import com.tali.notes.talisApp
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * One backup file with everything in it: the notebooks database, inserted images, notebook
 * previews and her settings (pens, colours, saved shapes). The same file is used for "Save a
 * backup" to Files and for Google Drive.
 */
object BackupArchive {
  const val MIME = "application/zip"
  private const val DB_ENTRY = "notes.db"
  private const val SETTINGS_ENTRY = "settings.json"
  private const val MANIFEST_ENTRY = "manifest.json"
  private const val FORMAT = 1

  @Serializable private class Manifest(val format: Int, val created: Long, val notebooks: Int, val app: String = "Tali's Notes")

  class InvalidBackup(message: String) : Exception(message)

  fun fileName(now: Long = System.currentTimeMillis()): String {
    val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now))
    return "Tali's Notes backup $date.zip"
  }

  /** Writes a complete backup to [out]. */
  suspend fun write(context: Context, out: OutputStream) =
    withContext(Dispatchers.IO) {
      val app = context.talisApp
      val repo = app.repository
      val tmp = File(context.cacheDir, "backup-snapshot.db")
      try {
        repo.snapshotDatabase(tmp)
        ZipOutputStream(out.buffered()).use { zip ->
          zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
          zip.write(ItemCodec.json.encodeToString(Manifest.serializer(), Manifest(FORMAT, System.currentTimeMillis(), repo.notebooks.value.size)).encodeToByteArray())
          zip.closeEntry()
          zip.putNextEntry(ZipEntry(DB_ENTRY))
          tmp.inputStream().use { it.copyTo(zip) }
          zip.closeEntry()
          zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
          zip.write(app.settings.exportJson().encodeToByteArray())
          zip.closeEntry()
          addFolder(zip, repo.imagesDir, "images/")
          addFolder(zip, repo.thumbsDir, "thumbs/")
        }
      } finally {
        tmp.delete()
      }
    }

  private fun addFolder(zip: ZipOutputStream, dir: File, prefix: String) {
    dir.listFiles()?.filter { it.isFile }?.forEach { f ->
      zip.putNextEntry(ZipEntry(prefix + f.name))
      f.inputStream().use { it.copyTo(zip) }
      zip.closeEntry()
    }
  }

  /**
   * Restores from a backup by merging it into what is already on the tablet (see
   * [NotesRepository.mergeFrom]): nothing here is deleted or overwritten with an older copy.
   */
  suspend fun restore(context: Context, input: InputStream): NotesRepository.MergeResult =
    withContext(Dispatchers.IO) {
      val app = context.talisApp
      val repo = app.repository
      val work = File(context.cacheDir, "restore").apply {
        deleteRecursively()
        mkdirs()
      }
      try {
        var sawManifest = false
        var settingsJson: String? = null
        ZipInputStream(input.buffered()).use { zip ->
          while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            // Only ever write plain file names into our own folders.
            val base = name.substringAfterLast('/')
            if (entry.isDirectory || base.isEmpty() || base.startsWith(".") || name.contains("..")) continue
            when {
              name == MANIFEST_ENTRY -> {
                val m = runCatching { ItemCodec.json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString()) }.getOrNull() ?: throw InvalidBackup("This file isn't a Tali's Notes backup.")
                if (m.format > FORMAT) throw InvalidBackup("This backup was made by a newer version of Tali's Notes. Update the app first.")
                sawManifest = true
              }
              name == DB_ENTRY -> File(work, DB_ENTRY).outputStream().use { zip.copyTo(it) }
              name == SETTINGS_ENTRY -> settingsJson = zip.readBytes().decodeToString()
              name.startsWith("images/") -> File(repo.imagesDir, base).let { if (!it.exists()) it.outputStream().use { o -> zip.copyTo(o) } }
              name.startsWith("thumbs/") -> File(work, "thumb-$base").outputStream().use { zip.copyTo(it) }
            }
          }
        }
        val dbFile = File(work, DB_ENTRY)
        if (!sawManifest || !dbFile.exists()) throw InvalidBackup("This file isn't a Tali's Notes backup.")
        // Bring an older backup's tables up to date before merging.
        runCatching { NotesDb(context, dbFile.path).use { it.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() } } }
          .onFailure { throw InvalidBackup("This backup was made by a newer version of Tali's Notes. Update the app first.") }
        val result = repo.mergeFrom(dbFile)
        // Previews for notebooks that came from the backup.
        work.listFiles()?.filter { it.name.startsWith("thumb-") }?.forEach { t ->
          val name = t.name.removePrefix("thumb-")
          if (name.removeSuffix(".png") in result.changed) t.copyTo(File(repo.thumbsDir, name), overwrite = true)
        }
        settingsJson?.let { mergeSettings(app.settings, it) }
        result
      } finally {
        work.deleteRecursively()
      }
    }

  /** Adds the backup's saved colours and shapes to hers; everything else stays as she has it. */
  private fun mergeSettings(store: SettingsStore, json: String) {
    val incoming = runCatching { ItemCodec.json.decodeFromString(com.tali.notes.data.AppSettings.serializer(), json) }.getOrNull() ?: return
    store.update { s ->
      s.copy(
        savedColors = (s.savedColors + incoming.savedColors).distinct(),
        customShapes = s.customShapes + incoming.customShapes.filter { c -> s.customShapes.none { it.id == c.id } },
      )
    }
  }
}
