package com.tali.notes

import android.net.Uri
import com.tali.notes.canvas.Pages
import com.tali.notes.data.DocKind
import com.tali.notes.data.NotebookMeta
import com.tali.notes.data.NotesRepository.ImportedPage
import com.tali.notes.model.PaperStyle
import com.tali.notes.export.Exporter
import com.tali.notes.geometry.Rect
import com.tali.notes.canvas.Change
import com.tali.notes.model.ImageItem
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PDFs, pictures and scans added from the library's + menu or opened from another app, each made into an item of its own
 * straight away (no need to open it): every page or picture on an A4 page of its own, on blank paper,
 * with its preview drawn, so it shows in the library as what it is ([DocKind]). Underneath it's like
 * any notebook: she can open it and write on it.
 */
object Imports {
  /** A PDF from her files: its pages, named after the file. */
  suspend fun pdf(app: TalisApp, uri: Uri, folderId: String?, draft: Boolean = false, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): NotebookMeta {
    val pages = app.repository.importPdf(uri, onProgress)
    if (pages.isEmpty()) throw com.tali.notes.data.NotesRepository.PdfImportException(tr(R.string.that_pdf_has_no_pages))
    val name = Incoming.nameOf(app, uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "PDF"
    return build(app, DocKind.PDF, name, folderId, pages, draft)
  }

  /** Pictures picked together: one item, a picture on each page. Null if none could be opened. */
  suspend fun pictures(app: TalisApp, uris: List<Uri>, folderId: String?, draft: Boolean = false): NotebookMeta? {
    val pages = stored(app, uris)
    if (pages.isEmpty()) return null
    val title = if (pages.size > 1) english(R.string.n_pictures, pages.size) else english(R.string.picture)
    return build(app, DocKind.PICTURE, title, folderId, pages, draft)
  }

  /** Copies pictures into the app, upright and downscaled; their size in pixels stands in for the page size. */
  private suspend fun stored(app: TalisApp, uris: List<Uri>): List<ImportedPage> =
    uris.mapNotNull { uri ->
      val file = app.repository.importImage(uri) ?: return@mapNotNull null
      val bmp = withContext(Dispatchers.IO) { app.repository.images.loadNow(file) } ?: return@mapNotNull null
      ImportedPage(file, bmp.width.toFloat(), bmp.height.toFloat())
    }

  /** [draft]: opened from another app, just for looking until she saves it (see NotebookMeta.draft). */
  private suspend fun build(app: TalisApp, kind: DocKind, title: String, folderId: String?, pages: List<ImportedPage>, draft: Boolean = false): NotebookMeta {
    val repo = app.repository
    // Its pages are the shape of its first page or picture (a slide deck's pages are slide-shaped),
    // with nothing round it: not A4 sheets with paper left over. Pages of another shape fit inside.
    val first = pages.first()
    val aspect = first.widthPt / first.heightPt
    val layout = Pages.of(aspect > 1f, aspect)
    val meta = repo.create(title, PaperStyle.BLANK, folderId = folderId, pages = pages.size, landscape = layout.landscape, draft = draft, kind = kind, pageAspect = aspect)
    val items =
      pages.mapIndexed { i, p ->
        val r = layout.rect(i)
        // Fills its page (one of another shape sits in the middle of it).
        val s = minOf(r.width / p.widthPt, r.height / p.heightPt)
        val w = p.widthPt * s
        val h = p.heightPt * s
        val rect = Rect(r.center.x - w / 2, r.center.y - h / 2, r.center.x + w / 2, r.center.y + h / 2)
        // Each one is its page, like a PDF's (never picked up by the lasso).
        ImageItem(UUID.randomUUID().toString(), i.toLong(), p.file, rect, pdfPage = true)
      }
    repo.applyChange(meta.id, Change(emptyList(), items))
    // Its preview: the top of the first page, as the library shows notebooks.
    withContext(Dispatchers.Default) {
      runCatching {
        val page = layout.rect(0)
        val region = Exporter.previewRegion(page)
        val onPage = items.filter { it.bounds.intersects(page) }
        onPage.forEach { repo.images.loadNow(it.file) }
        val bmp = Exporter.thumbnail(onPage, region, PaperStyle.BLANK, 640, repo.images, originY = page.top, page = page, clip = { layout.clipFor(it, pages.size, false) })
        repo.saveThumbnail(meta.id, bmp)
      }
    }
    // Something new (a photo, a scan, a PDF): to Drive straight away, so her other devices get it.
    if (!draft) com.tali.notes.backup.DriveBackup.backupSoon(app)
    return meta
  }
}
