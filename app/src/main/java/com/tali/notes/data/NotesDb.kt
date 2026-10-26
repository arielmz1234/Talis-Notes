package com.tali.notes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Plain SQLite: notebooks, folders and items; no code generation, fast on older tablets. */
class NotesDb(context: Context, name: String = "notes.db") : SQLiteOpenHelper(context, name, null, VERSION) {
  companion object {
    const val VERSION = 8
  }

  override fun onConfigure(db: SQLiteDatabase) {
    db.enableWriteAheadLogging()
    db.setForeignKeyConstraintsEnabled(true)
  }

  override fun onCreate(db: SQLiteDatabase) {
    db.execSQL(
      """
      CREATE TABLE notebooks (
        id TEXT PRIMARY KEY,
        title TEXT NOT NULL,
        created INTEGER NOT NULL,
        updated INTEGER NOT NULL,
        cover INTEGER NOT NULL,
        paper TEXT NOT NULL,
        viewport TEXT,
        thumb INTEGER NOT NULL DEFAULT 0,
        folder_id TEXT,
        deleted INTEGER,
        pages INTEGER,
        dark INTEGER NOT NULL DEFAULT 0,
        landscape INTEGER NOT NULL DEFAULT 0,
        draft INTEGER NOT NULL DEFAULT 0,
        kind TEXT,
        page_aspect REAL
      )
      """
    )
    createFolders(db)
    db.execSQL(
      """
      CREATE TABLE items (
        id TEXT PRIMARY KEY,
        notebook_id TEXT NOT NULL REFERENCES notebooks(id) ON DELETE CASCADE,
        z INTEGER NOT NULL,
        kind INTEGER NOT NULL,
        pen TEXT,
        color INTEGER NOT NULL,
        size REAL NOT NULL,
        payload BLOB NOT NULL,
        l REAL NOT NULL, t REAL NOT NULL, r REAL NOT NULL, b REAL NOT NULL
      )
      """
    )
    db.execSQL("CREATE INDEX items_by_notebook ON items(notebook_id, z)")
  }

  override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    if (oldVersion < 2) {
      db.execSQL("ALTER TABLE notebooks ADD COLUMN folder_id TEXT")
      createFolders(db)
    }
    // Recently deleted: when the notebook was moved to the trash, or NULL.
    if (oldVersion < 3) db.execSQL("ALTER TABLE notebooks ADD COLUMN deleted INTEGER")
    if (oldVersion < 4) {
      // A4 page count (NULL keeps the endless canvas every older notebook was made with), dark
      // paper, and folders inside folders.
      db.execSQL("ALTER TABLE notebooks ADD COLUMN pages INTEGER")
      db.execSQL("ALTER TABLE notebooks ADD COLUMN dark INTEGER NOT NULL DEFAULT 0")
      if (oldVersion >= 2) db.execSQL("ALTER TABLE folders ADD COLUMN parent_id TEXT")
    }
    // Landscape A4 pages, chosen when a notebook is made; every older notebook is portrait.
    if (oldVersion < 5) db.execSQL("ALTER TABLE notebooks ADD COLUMN landscape INTEGER NOT NULL DEFAULT 0")
    // A PDF opened from another app, being looked at but not saved to her notebooks yet.
    if (oldVersion < 6) db.execSQL("ALTER TABLE notebooks ADD COLUMN draft INTEGER NOT NULL DEFAULT 0")
    // What it is: NULL for a notebook, or a PDF, picture or scan she added (see DocKind).
    if (oldVersion < 7) db.execSQL("ALTER TABLE notebooks ADD COLUMN kind TEXT")
    // A PDF or picture added on its own: its pages' shape (width over height); NULL is A4.
    if (oldVersion < 8) db.execSQL("ALTER TABLE notebooks ADD COLUMN page_aspect REAL")
  }

  private fun createFolders(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE folders (id TEXT PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL, created INTEGER NOT NULL, position INTEGER NOT NULL, parent_id TEXT)")
  }
}

/** A stored item row, independent of Android graphics types so it can be backed up as-is. */
class ItemRow(
  val id: String,
  val z: Long,
  val kind: Int,
  val pen: String?,
  val color: Int,
  val size: Float,
  val payload: ByteArray,
  val l: Float,
  val t: Float,
  val r: Float,
  val b: Float,
) {
  fun toValues(notebookId: String) =
    ContentValues().apply {
      put("id", id)
      put("notebook_id", notebookId)
      put("z", z)
      put("kind", kind)
      put("pen", pen)
      put("color", color)
      put("size", size)
      put("payload", payload)
      put("l", l)
      put("t", t)
      put("r", r)
      put("b", b)
    }
}
