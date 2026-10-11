package com.tali.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tali.notes.backup.BackupArchive
import com.tali.notes.canvas.Change
import com.tali.notes.geometry.Pt
import com.tali.notes.ink.Brushes
import com.tali.notes.ink.StrokeFactory
import com.tali.notes.model.InkItem
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenType
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The per-notebook backup (one file per notebook in Google Drive) holds just that notebook and restores it. */
@RunWith(AndroidJUnit4::class)
class NotebookBackupTest {
  @Test
  fun oneNotebooksFileRestoresOnlyThatNotebook() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val repo = context.talisApp.repository
    val brush = Brushes.brush(PenType.FOUNTAIN, 0xFF1B1F2A.toInt(), 2.6f)
    fun strokes(prefix: String, n: Int) = List(n) { i -> InkItem("$prefix-$i", i.toLong(), PenType.FOUNTAIN, StrokeFactory.strokeAlong(listOf(Pt(10f, 10f + i * 20f), Pt(200f, 12f + i * 20f)), brush)) }
    val kept = repo.create("Kept", PaperStyle.LINES)
    val backedUp = repo.create("Statics", PaperStyle.GRID)
    repo.applyChange(kept.id, Change(emptyList(), strokes("k", 3)))
    repo.applyChange(backedUp.id, Change(emptyList(), strokes("s", 5)))
    delay(1500) // the writer thread

    val snapshot = File(context.cacheDir, "test-snapshot.db")
    repo.snapshotDatabase(snapshot)
    val zip = File(context.cacheDir, "test-notebook.zip")
    zip.outputStream().use { BackupArchive.writeNotebook(context, snapshot, backedUp.id, it) }
    val settingsZip = File(context.cacheDir, "test-settings.zip")
    settingsZip.outputStream().use { BackupArchive.writeSettings(context, snapshot, it) }

    // Erased for good on this tablet, then brought back from its own file.
    repo.deleteForever(backedUp.id)
    delay(1500)
    assertNull(repo.notebooks.value.firstOrNull { it.id == backedUp.id })
    val settingsResult = settingsZip.inputStream().use { BackupArchive.restore(context, it) }
    assertEquals(0, settingsResult.added)
    val result = zip.inputStream().use { BackupArchive.restore(context, it) }
    assertEquals(1, result.added)
    val back = repo.notebooks.value.firstOrNull { it.id == backedUp.id }
    assertNotNull(back)
    assertEquals("Statics", back!!.title)
    assertEquals(5, repo.loadItems(backedUp.id).size)
    // The other notebook wasn't in that file and is untouched.
    assertEquals(3, repo.loadItems(kept.id).size)

    repo.deleteForever(kept.id)
    repo.deleteForever(backedUp.id)
    listOf(snapshot, zip, settingsZip).forEach { it.delete() }
  }
}
