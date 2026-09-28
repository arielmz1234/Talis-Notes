package com.tali.notes.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.tali.notes.canvas.Change
import com.tali.notes.model.CanvasItem
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
)

data class Folder(val id: String, val name: String, val color: Int, val position: Int)

/** Cover colours that sit with the lilac and sky theme: lavender, sky, orchid, blue, rose, mint, plum. */
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

  private val _folders = MutableStateFlow<List<Folder>>(emptyList())
  val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

  /** Increments on every saved edit; the backup worker watches it. */
  private val _revision = MutableStateFlow(0L)
  val revision: StateFlow<Long> = _revision.asStateFlow()

  val imagesDir = File(context.filesDir, "images").apply { mkdirs() }
  val thumbsDir = File(context.filesDir, "thumbs").apply { mkdirs() }
  val databaseFile: File
    get() = context.getDatabasePath("notes.db")

  init {
    scope.launch { reload() }
  }

  private fun reload() {
    val list = mutableListOf<NotebookMeta>()
    db.readableDatabase.rawQuery("SELECT id,title,created,updated,cover,paper,viewport,thumb,folder_id FROM notebooks ORDER BY updated DESC", null).use { c ->
      while (c.moveToNext()) {
        list +=
          NotebookMeta(
            id = c.getString(0),
            title = c.getString(1),
            created = c.getLong(2),
            updated = c.getLong(3),
            cover = c.getInt(4),
            paper = runCatching { PaperStyle.valueOf(c.getString(5)) }.getOrDefault(PaperStyle.DOTS),
            viewport = c.getString(6)?.let { runCatching { ItemCodec.json.decodeFromString(Viewport.serializer(), it) }.getOrNull() },
            thumbVersion = c.getLong(7),
            folderId = if (c.isNull(8)) null else c.getString(8),
          )
      }
    }
    val folders = mutableListOf<Folder>()
    db.readableDatabase.rawQuery("SELECT id,name,color,position FROM folders ORDER BY position, created", null).use { c ->
      while (c.moveToNext()) folders += Folder(c.getString(0), c.getString(1), c.getInt(2), c.getInt(3))
    }
    _folders.value = folders
    _notebooks.value = list
  }

  suspend fun create(title: String, paper: PaperStyle, folderId: String? = null): NotebookMeta =
    withContext(writer) {
      val now = System.currentTimeMillis()
      val id = UUID.randomUUID().toString()
      val cover = COVER_COLORS[(_notebooks.value.size) % COVER_COLORS.size]
      db.writableDatabase.insertOrThrow(
        "notebooks",
        null,
        ContentValues().apply {
          put("id", id)
          put("title", title)
          put("created", now)
          put("updated", now)
          put("cover", cover)
          put("paper", paper.name)
          put("folder_id", folderId)
        },
      )
      reload()
      _notebooks.value.first { it.id == id }
    }

  fun rename(id: String, title: String) = update(id) { put("title", title) }

  fun moveToFolder(id: String, folderId: String?) = update(id, touch = false) { put("folder_id", folderId) }

  suspend fun createFolder(name: String, color: Int): Folder =
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
        },
      )
      reload()
      _folders.value.first { it.id == id }
    }

  fun renameFolder(id: String, name: String) = updateFolder(id) { put("name", name) }

  fun recolorFolder(id: String, color: Int) = updateFolder(id) { put("color", color) }

  /** Deletes a folder. Its notebooks are kept and move back to "All notebooks". */
  fun deleteFolder(id: String) {
    scope.launch {
      val w = db.writableDatabase
      w.beginTransaction()
      try {
        w.execSQL("UPDATE notebooks SET folder_id = NULL WHERE folder_id = ?", arrayOf(id))
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

  fun saveViewport(id: String, viewport: Viewport) =
    update(id, touch = false) { put("viewport", ItemCodec.json.encodeToString(Viewport.serializer(), viewport)) }

  private fun update(id: String, touch: Boolean = true, fill: ContentValues.() -> Unit) {
    scope.launch {
      val values = ContentValues().apply(fill)
      if (touch) values.put("updated", System.currentTimeMillis())
      db.writableDatabase.update("notebooks", values, "id=?", arrayOf(id))
      reload()
    }
  }

  fun delete(id: String) {
    scope.launch {
      val files = imageFilesOf(id)
      db.writableDatabase.delete("notebooks", "id=?", arrayOf(id))
      files.forEach { if (!isImageUsedElsewhere(it)) File(imagesDir, it).delete() }
      File(thumbsDir, "$id.png").delete()
      reload()
      _revision.value++
    }
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
          "INSERT INTO notebooks(id,title,created,updated,cover,paper,viewport,thumb,folder_id) SELECT ?,?,?,?,cover,paper,viewport,0,folder_id FROM notebooks WHERE id=?",
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
    withContext(Dispatchers.IO) { readRows(notebookId).mapNotNull { ItemCodec.decode(it, ::loadImage) } }

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

  fun loadImage(file: String): Bitmap? = runCatching { BitmapFactory.decodeFile(File(imagesDir, file).path) }.getOrNull()

  private fun imageFilesOf(notebookId: String): List<String> =
    readRows(notebookId).filter { it.kind == ItemCodec.KIND_IMAGE }.mapNotNull { row ->
      runCatching { ItemCodec.json.parseToJsonElement(row.payload.decodeToString()).let { (it as kotlinx.serialization.json.JsonObject)["file"].toString().trim('"') } }.getOrNull()
    }

  private fun isImageUsedElsewhere(file: String): Boolean =
    db.readableDatabase.rawQuery("SELECT 1 FROM items WHERE kind=? AND payload LIKE ? LIMIT 1", arrayOf(ItemCodec.KIND_IMAGE.toString(), "%$file%")).use { it.moveToFirst() }

  /** Closes the database so a restore can replace its file. */
  fun closeForRestore() = db.close()

  suspend fun reopenAfterRestore() = withContext(writer) {
    reload()
    _revision.value++
  }

  companion object {
    const val MAX_IMAGE_SIDE = 2048
  }
}

private object SQLiteConflict {
  const val REPLACE = android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
}
