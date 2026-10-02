package com.tali.notes.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.tali.notes.canvas.Pages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Her existing notes survive the upgrade that adds landscape pages, and stay portrait. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LandscapeUpgradeTest {
  @Test
  fun version4NotebooksUpgradeToPortrait() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val file = context.getDatabasePath("old.db").apply { parentFile?.mkdirs(); delete() }
    // The notebooks table exactly as schema version 4 made it, with one notebook in it.
    SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
      db.execSQL("CREATE TABLE notebooks (id TEXT PRIMARY KEY, title TEXT NOT NULL, created INTEGER NOT NULL, updated INTEGER NOT NULL, cover INTEGER NOT NULL, paper TEXT NOT NULL, viewport TEXT, thumb INTEGER NOT NULL DEFAULT 0, folder_id TEXT, deleted INTEGER, pages INTEGER, dark INTEGER NOT NULL DEFAULT 0)")
      db.execSQL("CREATE TABLE folders (id TEXT PRIMARY KEY, name TEXT NOT NULL, color INTEGER NOT NULL, created INTEGER NOT NULL, position INTEGER NOT NULL, parent_id TEXT)")
      db.execSQL("CREATE TABLE items (id TEXT PRIMARY KEY, notebook_id TEXT NOT NULL REFERENCES notebooks(id) ON DELETE CASCADE, z INTEGER NOT NULL, kind INTEGER NOT NULL, pen TEXT, color INTEGER NOT NULL, size REAL NOT NULL, payload BLOB NOT NULL, l REAL NOT NULL, t REAL NOT NULL, r REAL NOT NULL, b REAL NOT NULL)")
      db.execSQL("INSERT INTO notebooks (id,title,created,updated,cover,paper,pages,dark) VALUES ('n1','Statics',1,1,0,'LINES',3,0)")
      db.version = 4
    }
    val upgraded = NotesDb(context, "old.db")
    upgraded.readableDatabase.rawQuery("SELECT title, pages, landscape FROM notebooks WHERE id='n1'", null).use { c ->
      assertTrue(c.moveToFirst())
      assertEquals("Statics", c.getString(0))
      assertEquals(3, c.getInt(1))
      assertEquals(0, c.getInt(2))
    }
    assertEquals(NotesDb.VERSION, upgraded.readableDatabase.version)
    upgraded.close()
  }

  @Test
  fun landscapePagesLieOnTheirSide() {
    val p = Pages.PORTRAIT
    val l = Pages.LANDSCAPE
    assertEquals(p.width, l.height, 1e-3f)
    assertEquals(p.height, l.width, 1e-3f)
    assertTrue(l.aspect > 1f)
    assertFalse(p.aspect > 1f)
    // Pages stack one under the other with the same gap.
    assertEquals(l.height + Pages.GAP, l.rect(1).top, 1e-3f)
    assertEquals(1, l.indexAt(l.rect(1).top + 10f))
    assertEquals(2, l.needed(l.rect(1)))
  }
}
