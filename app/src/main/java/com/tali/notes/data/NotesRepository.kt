package com.tali.notes.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.tali.notes.canvas.Change
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.CustomPaper
import com.tali.notes.model.PaperStyle
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Where the camera was looking: world point at the screen's top-left, and zoom (1 = 100%). */
@Serializable data class Viewport(val x: Float = 0f, val y: Float = 0f, val zoom: Float = 1f)

data class NotebookMeta(
  val id: String,
  val title: String,
  val created: Long,
  val updated: Long,
  val cover: Int,
  val paper: PaperStyle,
  val viewport: Viewport?,
  /** Bumped whenever the preview image is regenerated, so the library reloads it. */
  val thumbVersion: Long,
  val folderId: String?,
  /** When she deleted it; deleted notebooks wait in Recently deleted for [TRASH_DAYS] days. */
  val deleted: Long? = null,
  /** How many A4 pages it has, or null for an endless canvas. */
  val pages: Int? = null,
  /** Dark paper: a black page with the ink colours flipped to light ones. */
  val dark: Boolean = false,
  /** Its A4 pages lie on their side (chosen when it was made). */
  val landscape: Boolean = false,
  /** One of her own paper backgrounds ([CustomPaper.id]), used instead of [paper] when set. */
  val customPaper: String? = null,
)

/** A folder; [parentId] is the folder it sits in, or null at the top level. */
data class Folder(val id: String, val name: String, val color: Int, val position: Int, val parentId: String? = null)

/** Cover colours that sit with the lilac and sky theme: lavender, sky, orchid, blue, rose, mint, plum. */
/**
 * Every colour offered for covers and folders: the soft theme ones first, then bold ones that
 * stand out on a shelf (red, orange, yellow, green, teal, navy, brown, grey, black).
 */
val PICK_COLORS: List<Int> by lazy {
  COVER_COLORS + listOf(0xFFE53935, 0xFFFB8C00, 0xFFFDD835, 0xFF43A047, 0xFF00897B, 0xFF00ACC1, 0xFF1E3A8A, 0xFF795548, 0xFF757575, 0xFF212121).map { it.toInt() }
}

val COVER_COLORS =
  listOf(0xFF9B7BE0, 0xFF7FB5F0, 0xFFC78FD9, 0xFF4F7BE8, 0xFFE59BB8, 0xFF7DC4B2, 0xFF5E4B8B).map { it.toInt() }

@OptIn(ExperimentalCoroutinesApi::class)
class NotesRepository(private val context: Context) {
  private val db = NotesDb(context)

  /** Every database write goes through one thread so edits land in order. */
  private val writer = Dispatchers.IO.limitedParallelism(1)
  private val scope = CoroutineScope(SupervisorJob() + writer)

  private val _notebooks = MutableStateFlow<List<NotebookMeta>>(emptyList())
  val notebooks: StateFlow<List<NotebookMeta>> = _notebooks.asStateFlow()

  private val _trash = MutableStateFlow<List<NotebookMeta>>(emptyList())

  /** Recently deleted notebooks, newest first. */
  val trash: StateFlow<List<NotebookMeta>> = _trash.asStateFlow()

  private val _folders = MutableStateFlow<List<Folder>>(emptyList())
  val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

  /** Increments on every saved edit; the backup worker watches it. */
  private val _revision = MutableStateFlow(0L)
  val revision: StateFlow<Long> = _revision.asStateFlow()

  val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
  val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }

  /** Pictures and PDF pages, loaded as they come into view. */
  val images = ImageStore(imagesDir)
  val databaseFile: File
    get() = context.getDatabasePath("notes.db")

  init {
    scope.launch {
      reload()
      purgeTrash()
    }
  }

  private fun reload() {
    val list = mutableListOf<NotebookMeta>()
    db.readableDatabase.rawQuery("SELECT id,title,created,updated,cover,paper,viewport,thumb,folder_id,deleted,pages,dark,landscape FROM notebooks ORDER BY updated DESC", null).use { c ->
      while (c.moveToNext()) {
        list +=
          NotebookMeta(
            id = c.getString(0),
            title = c.getString(1),
            created = c.getLong(2),
            updated = c.getLong(3),
            cover = c.getInt(4),
            paper = c.getString(5).let { p -> if (p.startsWith(CustomPaper.PREFIX)) PaperStyle.BLANK else runCatching { PaperStyle.valueOf(p) }.getOrDefault(PaperStyle.DOTS) },
            viewport = c.getString(6)?.let { runCatching { ItemCodec.json.decodeFromString(Viewport.serializer(), it) }.getOrNull() },
            thumbVersion = c.getLong(7),
            folderId = if (c.isNull(8)) null else c.getString(8),
            deleted = if (c.isNull(9)) null else c.getLong(9),
            pages = if (c.isNull(10)) null else c.getInt(10),
            dark = c.getInt(11) != 0,
            landscape = c.getInt(12) != 0,
            customPaper = c.getString(5).takeIf { it.startsWith(CustomPaper.PREFIX) }?.removePrefix(CustomPaper.PREFIX),
          )
      }
    }
    val folders = mutableListOf<Folder>()
    db.readableDatabase.rawQuery("SELECT id,name,color,position,parent_id FROM folders ORDER BY position, created", null).use { c ->
      while (c.moveToNext()) folders += Folder(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3), if (c.isNull(4)) null else c.getString(4))
    }
    _folders.value = folders
    _notebooks.value = list.filter { it.deleted == null }
    _trash.value = list.filter { it.deleted != null }.sortedByDescending { it.deleted }
  }

  /** New notebooks start as one A4 page; scrolling past the last page adds another. */
  suspend fun create(
    title: String,
    paper: PaperStyle,
    folderId: String? = null,
    pages: Int? = 1,
    customPaper: String? = null,
    dark: Boolean = false,
    cover: Int? = null,
    landscape: Boolean = false,
  ): NotebookMeta =
    withContext(writer) {
      val now = System.currentTimeMillis()
      val id = UUID.randomUUID().toString()
      val cover = cover ?: COVER_COLORS[(_notebooks.value.size) % COVER_COLORS.size]
      db.writableDatabase.insertOrThrow(
        "notebooks",
        null,
        ContentValues().apply {
          put("id", id)
          put("title", title)
          put("created", now)
          put("updated", now)
          put("cover", cover)
          put("paper", customPaper?.let { CustomPaper.PREFIX + it } ?: paper.name)
          put("folder_id", folderId)
          put("pages", pages)
          put("dark", if (dark) 1 else 0)
          put("landscape", if (landscape) 1 else 0)
        },
      )
      reload()
      _notebooks.value.first { it.id == id }
    }

  fun rename(id: String, title: String) = update(id) { put("title", title) }

  fun moveToFolder(id: String, folderId: String?) = update(id, touch = false) { put("folder_id", folderId) }

  suspend fun createFolder(name: String, color: Int, parentId: String? = null): Folder =
    withContext(writer) {
      val id = UUID.randomUUID().toString()
      db.writableDatabase.insertOrThrow(
        "folders",
        null,
        ContentValues().apply {
          put("id", id)
          put("name", name)
          put("color", color)
          put("created", System.currentTimeMillis())
          put("position", _folders.value.size)
          put("parent_id", parentId)
        },
      )
      reload()
      _folders.value.first { it.id == id }
    }

  fun renameFolder(id: String, name: String) = updateFolder(id) { put("name", name) }

  fun recolorFolder(id: String, color: Int) = updateFolder(id) { put("color", color) }

  /** Moves a folder into another folder, or to the top level with null. */
  fun moveFolder(id: String, parentId: String?) = updateFolder(id) { put("parent_id", parentId) }

  /**
   * Deletes a folder. Nothing inside is lost: its notebooks and folders move up into the folder it
   * was in (or to the top level).
   */
  fun deleteFolder(id: String) {
    scope.launch {
      val parent = _folders.value.firstOrNull { it.id == id }?.parentId
      val w = db.writableDatabase
      w.beginTransaction()
      try {
        w.execSQL("UPDATE notebooks SET folder_id = ? WHERE folder_id = ?", arrayOf(parent, id))
        w.execSQL("UPDATE folders SET parent_id = ? WHERE parent_id = ?", arrayOf(parent, id))
        w.delete("folders", "id=?", arrayOf(id))
        w.setTransactionSuccessful()
      } finally {
        w.endTransaction()
      }
      reload()
      _revision.value++
    }
  }

  private fun updateFolder(id: String, fill: ContentValues.() -> Unit) {
    scope.launch {
      db.writableDatabase.update("folders", ContentValues().apply(fill), "id=?", arrayOf(id))
      reload()
      _revision.value++
    }
  }

  fun setCover(id: String, color: Int) = update(id) { put("cover", color) }

  fun setPaper(id: String, paper: PaperStyle) = update(id) { put("paper", paper.name) }

  /** Gives a notebook one of her own paper backgrounds. */
  fun setCustomPaper(id: String, paperId: String) = update(id) { put("paper", CustomPaper.PREFIX + paperId) }

  /** A number of A4 pages, or null for an endless canvas. */
  fun setPages(id: String, pages: Int?) = update(id, touch = false) { if (pages == null) putNull("pages") else put("pages", pages) }

  fun setDark(id: String, dark: Boolean) = update(id) { put("dark", if (dark) 1 else 0) }

  fun saveViewport(id: String, viewport: Viewport) =
    update(id, touch = false) { put("viewport", ItemCodec.json.encodeToString(Viewport.serializer(), viewport)) }

  private fun update(id: String, touch: Boolean = true, fill: ContentValues.() -> Unit) {
    scope.launch {
      val values = ContentValues().apply(fill)
      if (touch) values.put("updated", System.currentTimeMillis())
      db.writableDatabase.update("notebooks", values, "id=?", arrayOf(id))
      reload()
      _revision.value++
    }
  }

  /** Moves a notebook to Recently deleted. Nothing is erased until it has been there [TRASH_DAYS] days. */
  fun delete(id: String) = update(id, touch = false) { put("deleted", System.currentTimeMillis()) }

  fun restoreFromTrash(id: String) = update(id, touch = false) { putNull("deleted") }

  /** Erases a notebook for good, with its pictures (unless another notebook uses them) and preview. */
  fun deleteForever(id: String) {
    scope.launch { erase(id) }
  }

  fun emptyTrash() {
    scope.launch { _trash.value.forEach { erase(it.id) } }
  }

  private fun purgeTrash() {
    val cutoff = System.currentTimeMillis() - TRASH_DAYS * 24L * 3600_000L
    _trash.value.filter { (it.deleted ?: Long.MAX_VALUE) < cutoff }.forEach { erase(it.id) }
  }

  private fun erase(id: String) {
    val files = imageFilesOf(id)
    db.writableDatabase.delete("notebooks", "id=?", arrayOf(id))
    files.forEach { if (!isImageUsedElsewhere(it)) File(imagesDir, it).delete() }
    File(thumbsDir, "$id.png").delete()
    reload()
    _revision.value++
  }

  suspend fun duplicate(id: String): String =
    withContext(writer) {
      val src = _notebooks.value.first { it.id == id }
      val newId = UUID.randomUUID().toString()
      val now = System.currentTimeMillis()
      val w = db.writableDatabase
      w.beginTransaction()
      try {
        w.execSQL(
          "INSERT INTO notebooks(id,title,created,updated,cover,paper,viewport,thumb,folder_id,pages,dark,landscape) SELECT ?,?,?,?,cover,paper,viewport,0,folder_id,pages,dark,landscape FROM notebooks WHERE id=?",
          arrayOf<Any>(newId, src.title + " (copy)", now, now, id),
        )
        readRows(id).forEach { w.insertOrThrow("items", null, it.toValues(newId).apply { put("id", UUID.randomUUID().toString()) }) }
        w.setTransactionSuccessful()
      } finally {
        w.endTransaction()
      }
      File(thumbsDir, "$id.png").takeIf { it.exists() }?.copyTo(File(thumbsDir, "$newId.png"), overwrite = true)
      reload()
      _revision.value++
      newId
    }

  suspend fun loadItems(notebookId: String): List<CanvasItem> =
    withContext(Dispatchers.IO) { readRows(notebookId).mapNotNull(ItemCodec::decode) }

  fun readRows(notebookId: String): List<ItemRow> {
    val rows = mutableListOf<ItemRow>()
    db.readableDatabase.rawQuery("SELECT id,z,kind,pen,color,size,payload,l,t,r,b FROM items WHERE notebook_id=? ORDER BY z", arrayOf(notebookId)).use { c ->
      while (c.moveToNext()) {
        rows +=
          ItemRow(
            c.getString(0),
            c.getLong(1),
            c.getInt(2),
            if (c.isNull(3)) null else c.getString(3),
            c.getInt(4),
            c.getFloat(5),
            c.getBlob(6),
            c.getFloat(7),
            c.getFloat(8),
            c.getFloat(9),
            c.getFloat(10),
          )
      }
    }
    return rows
  }

  /** Saves an edit. Encoding happens on the caller's thread (fast); the write is queued in order. */
  fun applyChange(notebookId: String, change: Change) {
    val removed = change.removed.map { it.id }
    val added = change.added.map(ItemCodec::encode)
    scope.launch {
      val w = db.writableDatabase
      w.beginTransaction()
      try {
        removed.forEach { w.delete("items", "id=?", arrayOf(it)) }
        added.forEach { w.insertWithOnConflict("items", null, it.toValues(notebookId), SQLiteConflict.REPLACE) }
        w.update("notebooks", ContentValues().apply { put("updated", System.currentTimeMillis()) }, "id=?", arrayOf(notebookId))
        w.setTransactionSuccessful()
      } finally {
        w.endTransaction()
      }
      _revision.value++
    }
  }

  fun saveThumbnail(notebookId: String, bitmap: Bitmap) {
    scope.launch {
      File(thumbsDir, "$notebookId.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      db.writableDatabase.update("notebooks", ContentValues().apply { put("thumb", System.nanoTime()) }, "id=?", arrayOf(notebookId))
      reload()
    }
  }

  fun thumbnailFile(notebookId: String) = File(thumbsDir, "$notebookId.png")

  /** Copies a picked image into app storage, downscaled and upright. Returns the stored file name. */
  suspend fun importImage(uri: Uri): String? =
    withContext(Dispatchers.IO) {
      runCatching {
          val resolver = context.contentResolver
          val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
          resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
          var sample = 1
          while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
          val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return@runCatching null
          val rotation = resolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
          val upright = if (rotation != 0) Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation.toFloat()) }, true) else decoded
          val name = UUID.randomUUID().toString() + ".jpg"
          File(imagesDir, name).outputStream().use { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }
          name
        }
        .getOrNull()
    }


  /** One page of an imported PDF: its picture file and its size in PDF points. */
  data class ImportedPage(val file: String, val widthPt: Float, val heightPt: Float)

  class PdfImportException(message: String) : Exception(message)

  /**
   * Renders every page of a PDF (lecture slides, a problem sheet) into a picture she can write on.
   * Pages are drawn sharp enough for zooming in on formulas, and saved as JPEGs to stay small.
   */
  suspend fun importPdf(uri: Uri, onProgress: (done: Int, total: Int) -> Unit): List<ImportedPage> =
    withContext(Dispatchers.IO) {
      val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw PdfImportException("Couldn't open that file.")
      pfd.use { fd ->
        val renderer =
          try {
            PdfRenderer(fd)
          } catch (e: SecurityException) {
            throw PdfImportException("This PDF is password-protected. Remove the password and try again.")
          } catch (e: java.io.IOException) {
            throw PdfImportException("That file isn't a PDF this tablet can read.")
          }
        renderer.use { r ->
          val count = r.pageCount.coerceAtMost(MAX_PDF_PAGES)
          List(count) { i ->
            r.openPage(i).use { page ->
              // About 190 dpi for an A4 page; long sides are capped so each page stays light in memory.
              val scale = minOf(PDF_RENDER_WIDTH / page.width.toFloat(), PDF_RENDER_MAX_SIDE / maxOf(page.width, page.height).toFloat())
              val w = (page.width * scale).toInt().coerceAtLeast(1)
              val h = (page.height * scale).toInt().coerceAtLeast(1)
              val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
              bmp.eraseColor(android.graphics.Color.WHITE)
              page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
              val name = UUID.randomUUID().toString() + ".jpg"
              File(imagesDir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
              bmp.recycle()
              onProgress(i + 1, count)
              ImportedPage(name, page.width.toFloat(), page.height.toFloat())
            }
          }
        }
      }
    }

  private fun imageFilesOf(notebookId: String): List<String> =
    readRows(notebookId).filter { it.kind == ItemCodec.KIND_IMAGE }.mapNotNull { row ->
      runCatching { ItemCodec.json.parseToJsonElement(row.payload.decodeToString()).let { (it as kotlinx.serialization.json.JsonObject)["file"].toString().trim('"') } }.getOrNull()
    }

  private fun isImageUsedElsewhere(file: String): Boolean =
    db.readableDatabase.rawQuery("SELECT 1 FROM items WHERE kind=? AND payload LIKE ? LIMIT 1", arrayOf(ItemCodec.KIND_IMAGE.toString(), "%$file%")).use { it.moveToFirst() }

  /**
   * Copies the database to [target] as one consistent file. Runs on the writer thread, so no edit
   * can land halfway through; the write-ahead log is folded in first so the copy is complete.
   */
  suspend fun snapshotDatabase(target: File) =
    withContext(writer) {
      db.writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
      databaseFile.copyTo(target, overwrite = true)
    }

  /** What a restore did, for the message she sees. */
  data class MergeResult(val added: Int, val updated: Int, val kept: Int, val changed: Set<String>)

  /**
   * Brings notebooks and folders from a backup database into this one. Nothing on the tablet is
   * lost: a notebook only in the backup is added, one in both is replaced only if the backup copy
   * was edited more recently, and everything else stays as it is.
   */
  suspend fun mergeFrom(backup: File): MergeResult =
    withContext(writer) {
      val w = db.writableDatabase
      // ATTACH can't run inside a transaction, and needs a plain path.
      w.execSQL("ATTACH DATABASE ? AS bk", arrayOf(backup.path))
      try {
        var added = 0
        var updated = 0
        var kept = 0
        val incoming = mutableListOf<Pair<String, Boolean>>()
        w.beginTransaction()
        try {
          // Backups made before pages, dark paper and nested folders existed lack those columns.
          val bkNotebook = columns(w, "notebooks")
          val pagesCol = if ("pages" in bkNotebook) "pages" else "NULL"
          val darkCol = if ("dark" in bkNotebook) "dark" else "0"
          val landscapeCol = if ("landscape" in bkNotebook) "landscape" else "0"
          val parentCol = if ("parent_id" in columns(w, "folders")) "parent_id" else "NULL"
          w.execSQL("INSERT OR IGNORE INTO folders (id,name,color,created,position,parent_id) SELECT id,name,color,created,position,$parentCol FROM bk.folders")
          w.rawQuery("SELECT b.id, m.id IS NOT NULL, b.updated > IFNULL(m.updated, 0) FROM bk.notebooks b LEFT JOIN main.notebooks m ON m.id = b.id WHERE b.deleted IS NULL", null).use { c ->
            while (c.moveToNext()) {
              val exists = c.getInt(1) == 1
              val newer = c.getInt(2) == 1
              when {
                !exists -> { incoming += c.getString(0) to false; added++ }
                newer -> { incoming += c.getString(0) to true; updated++ }
                else -> kept++
              }
            }
          }
          for ((id, exists) in incoming) {
            if (exists) {
              w.execSQL("DELETE FROM main.items WHERE notebook_id = ?", arrayOf(id))
              w.execSQL("DELETE FROM main.notebooks WHERE id = ?", arrayOf(id))
            }
            w.execSQL(
              "INSERT INTO main.notebooks (id,title,created,updated,cover,paper,viewport,thumb,folder_id,deleted,pages,dark,landscape) SELECT id,title,created,updated,cover,paper,viewport,thumb+1,folder_id,NULL,$pagesCol,$darkCol,$landscapeCol FROM bk.notebooks WHERE id = ?",
              arrayOf(id),
            )
            w.execSQL("INSERT OR REPLACE INTO main.items SELECT * FROM bk.items WHERE notebook_id = ?", arrayOf(id))
          }
          w.setTransactionSuccessful()
        } finally {
          w.endTransaction()
        }
        reload()
        _revision.value++
        MergeResult(added, updated, kept, incoming.map { it.first }.toSet())
      } finally {
        w.execSQL("DETACH DATABASE bk")
      }
    }

  private fun columns(w: android.database.sqlite.SQLiteDatabase, table: String): Set<String> =
    w.rawQuery("PRAGMA bk.table_info($table)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }

  companion object {
    const val MAX_IMAGE_SIDE = 2048
    const val TRASH_DAYS = 30
    private const val PDF_RENDER_WIDTH = 1600f
    private const val PDF_RENDER_MAX_SIDE = 2400f
    private const val MAX_PDF_PAGES = 300
  }
}

private object SQLiteConflict {
  const val REPLACE = android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
}
