package com.tali.notes.backup

import com.tali.notes.later
import com.tali.notes.R
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
          addFolder(zip, repo.audioDir, "audio/")
        }
      } finally {
        tmp.delete()
      }
    }

  /**
   * A backup of one notebook (for the per-notebook Drive backup): a database holding only that
   * notebook and its writing (plus her folders, so it lands back in its folder), with only its own
   * pictures, recordings and preview. [snapshot] is a copy of the database made for this run.
   * Restoring it is the same merge as any backup.
   */
  suspend fun writeNotebook(context: Context, snapshot: File, notebookId: String, out: OutputStream) =
    withContext(Dispatchers.IO) {
      val repo = context.talisApp.repository
      val db = File(context.cacheDir, "backup-one.db")
      try {
        prune(context, snapshot, db, notebookId)
        val files = { kind: Int, dir: File -> repo.filesOf(notebookId, kind).distinct().map { File(dir, it) }.filter { it.isFile } }
        zipUp(out, db, settingsJson = null, notebooks = 1, images = files(ItemCodec.KIND_IMAGE, repo.imagesDir), audio = files(ItemCodec.KIND_AUDIO, repo.audioDir), thumbs = listOf(repo.thumbnailFile(notebookId)).filter { it.isFile })
      } finally {
        dropDb(db)
      }
    }

  /** Her settings (pens, colours, shapes, papers and the pictures they use) and folders, with no notebooks. */
  suspend fun writeSettings(context: Context, snapshot: File, out: OutputStream) =
    withContext(Dispatchers.IO) {
      val app = context.talisApp
      val db = File(context.cacheDir, "backup-settings.db")
      try {
        prune(context, snapshot, db, notebookId = null)
        val settings = app.settings.exportJson()
        // Her own papers' pictures live with the notebooks' pictures; only those named in the settings go here.
        val images = app.repository.imagesDir.listFiles()?.filter { it.isFile && settings.contains(it.name) }.orEmpty()
        zipUp(out, db, settings, notebooks = 0, images = images, audio = emptyList(), thumbs = emptyList())
      } finally {
        dropDb(db)
      }
    }

  /** A fresh database at [target] with every folder and only [notebookId]'s notebook and items (none for null). */
  private fun prune(context: Context, snapshot: File, target: File, notebookId: String?) {
    dropDb(target)
    NotesDb(context, target.path).use { helper ->
      val w = helper.writableDatabase
      w.execSQL("ATTACH DATABASE ? AS src", arrayOf(snapshot.path))
      try {
        // Columns by name: a database upgraded over time has them in a different order than a new one.
        fun copy(table: String, where: String, args: Array<String>) {
          val mine = w.rawQuery("PRAGMA main.table_info($table)", null).use { c -> buildList { while (c.moveToNext()) add(c.getString(1)) } }
          val theirs = w.rawQuery("PRAGMA src.table_info($table)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
          val cols = mine.filter { it in theirs }.joinToString(",")
          w.execSQL("INSERT INTO main.$table ($cols) SELECT $cols FROM src.$table $where", args)
        }
        copy("folders", "", emptyArray())
        if (notebookId != null) {
          copy("notebooks", "WHERE id = ?", arrayOf(notebookId))
          copy("items", "WHERE notebook_id = ?", arrayOf(notebookId))
        }
      } finally {
        w.execSQL("DETACH DATABASE src")
      }
      w.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
    }
  }

  private fun dropDb(db: File) {
    for (suffix in listOf("", "-wal", "-shm", "-journal")) File(db.path + suffix).delete()
  }

  private fun zipUp(out: OutputStream, db: File, settingsJson: String?, notebooks: Int, images: List<File>, audio: List<File>, thumbs: List<File>) {
    ZipOutputStream(out.buffered()).use { zip ->
      zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
      zip.write(ItemCodec.json.encodeToString(Manifest.serializer(), Manifest(FORMAT, System.currentTimeMillis(), notebooks)).encodeToByteArray())
      zip.closeEntry()
      zip.putNextEntry(ZipEntry(DB_ENTRY))
      db.inputStream().use { it.copyTo(zip) }
      zip.closeEntry()
      if (settingsJson != null) {
        zip.putNextEntry(ZipEntry(SETTINGS_ENTRY))
        zip.write(settingsJson.encodeToByteArray())
        zip.closeEntry()
      }
      for ((prefix, list) in listOf("images/" to images, "audio/" to audio, "thumbs/" to thumbs)) {
        list.forEach { f ->
          zip.putNextEntry(ZipEntry(prefix + f.name))
          f.inputStream().use { it.copyTo(zip) }
          zip.closeEntry()
        }
      }
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
                val m = runCatching { ItemCodec.json.decodeFromString(Manifest.serializer(), zip.readBytes().decodeToString()) }.getOrNull() ?: throw InvalidBackup(later(R.string.this_file_isn_t_a_tali))
                if (m.format > FORMAT) throw InvalidBackup(later(R.string.this_backup_was_made_by_a))
                sawManifest = true
              }
              name == DB_ENTRY -> File(work, DB_ENTRY).outputStream().use { zip.copyTo(it) }
              name == SETTINGS_ENTRY -> settingsJson = zip.readBytes().decodeToString()
              name.startsWith("images/") -> File(repo.imagesDir, base).let { if (!it.exists()) it.outputStream().use { o -> zip.copyTo(o) } }
              name.startsWith("audio/") -> File(repo.audioDir, base).let { if (!it.exists()) it.outputStream().use { o -> zip.copyTo(o) } }
              name.startsWith("thumbs/") -> File(work, "thumb-$base").outputStream().use { zip.copyTo(it) }
            }
          }
        }
        val dbFile = File(work, DB_ENTRY)
        if (!sawManifest || !dbFile.exists()) throw InvalidBackup(later(R.string.this_file_isn_t_a_tali))
        // Bring an older backup's tables up to date before merging.
        runCatching { NotesDb(context, dbFile.path).use { it.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() } } }
          .onFailure { throw InvalidBackup(later(R.string.this_backup_was_made_by_a)) }
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

  /** Adds the backup's saved colours, shapes and paper backgrounds to hers; everything else stays as she has it. */
  private fun mergeSettings(store: SettingsStore, json: String) {
    val incoming = runCatching { ItemCodec.json.decodeFromString(com.tali.notes.data.AppSettings.serializer(), json) }.getOrNull() ?: return
    store.update { s ->
      s.copy(
        savedColors = (s.savedColors + incoming.savedColors).distinct(),
        highlighterColors = (s.highlighterColors + incoming.highlighterColors).distinct(),
        customShapes = s.customShapes + incoming.customShapes.filter { c -> s.customShapes.none { it.id == c.id } },
        customPapers = s.customPapers + incoming.customPapers.filter { c -> s.customPapers.none { it.id == c.id } },
      )
    }
  }
}
