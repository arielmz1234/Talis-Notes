package com.tali.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tali.notes.model.PaperStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** No two notebooks in one place (a folder, or none) share a name, however they get it; nor two folders side by side. */
@RunWith(AndroidJUnit4::class)
class NotebookNamesTest {
  @Test
  fun namesInOnePlaceStayApart() = runBlocking {
    val repo = InstrumentationRegistry.getInstrumentation().targetContext.talisApp.repository
    val tag = "Names${System.nanoTime() % 100000}"
    suspend fun title(id: String): String {
      delay(400) // the writer thread
      return repo.notebooks.value.first { it.id == id }.title
    }
    val folder = repo.createFolder("$tag folder", 0)
    val a = repo.create(tag, PaperStyle.LINES, folderId = null)
    val b = repo.create(tag, PaperStyle.LINES, folderId = null)
    val inFolder = repo.create(tag, PaperStyle.LINES, folderId = folder.id)
    assertEquals(tag, a.title)
    assertEquals("$tag (1)", b.title)
    // Another place: the same name is fine.
    assertEquals(tag, inFolder.title)

    // Renaming onto a taken name, moving into a place that has it, a copy, and coming back from the trash.
    val c = repo.create("$tag other", PaperStyle.LINES)
    repo.rename(c.id, tag)
    assertEquals("$tag (2)", title(c.id))
    repo.moveToFolder(b.id, folder.id)
    assertEquals("$tag (1)", title(b.id))
    val copy = repo.duplicate(a.id)
    assertEquals("$tag (1)", title(copy))
    repo.delete(a.id)
    delay(400)
    val d = repo.create(tag, PaperStyle.LINES)
    assertEquals(tag, d.title)
    repo.restoreFromTrash(a.id)
    assertEquals("$tag (3)", title(a.id))

    // Folders side by side.
    val f2 = repo.createFolder("$tag folder", 0)
    assertEquals("$tag folder (1)", f2.name)

    listOf(a.id, b.id, c.id, copy, d.id, inFolder.id).forEach { repo.deleteForever(it) }
    repo.deleteFolder(folder.id)
    repo.deleteFolder(f2.id)
    delay(800)
  }
}
