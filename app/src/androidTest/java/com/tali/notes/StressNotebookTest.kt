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
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a regular test: makes a big notebook (a semester of notes) and saves a backup file of it, for
 * trying big notebooks in the release build. Runs only when asked:
 * `-Pandroid.testInstrumentationRunnerArguments.stress=true`.
 */
@RunWith(AndroidJUnit4::class)
class StressNotebookTest {
  @Test
  fun makeBigNotebookBackup() = runBlocking {
    assumeTrue(InstrumentationRegistry.getArguments().getString("stress") == "true")
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val repo = context.talisApp.repository
    val meta = repo.create("Big semester notebook", PaperStyle.LINES)
    val brush = Brushes.brush(PenType.FOUNTAIN, 0xFF1B1F2A.toInt(), 2.6f)
    // 40 pages of 100 word-like scribbles, stacked down the canvas.
    val items =
      List(4000) { i ->
        val x0 = 60f + (i % 10) * 120f
        val y0 = 80f + (i / 10) * 45f
        val pts = List(70) { k -> Pt(x0 + k * 1.3f, y0 + 11f * sin(k * 0.5f + i * 0.7f)) }
        InkItem("big-$i", i.toLong(), PenType.FOUNTAIN, StrokeFactory.strokeAlong(pts, brush))
      }
    items.chunked(250).forEach { repo.applyChange(meta.id, Change(emptyList(), it)) }
    delay(3000) // let the writer thread finish
    // Into Downloads, which survives the test app being uninstalled afterwards.
    val values = android.content.ContentValues().apply {
      put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "big-notebook-backup.zip")
      put(android.provider.MediaStore.Downloads.MIME_TYPE, BackupArchive.MIME)
    }
    val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
    context.contentResolver.openOutputStream(uri)!!.use { BackupArchive.write(context, it) }
  }
}
