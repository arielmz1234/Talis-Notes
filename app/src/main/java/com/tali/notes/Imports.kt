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
 * PDFs, pictures and scans added from the library's + menu, each made into an item of its own
 * straight away (no need to open it): every page or picture on an A4 page of its own, on blank paper,
 * with its preview drawn, so it shows in the library as what it is ([DocKind]). Underneath it's like
 * any notebook: she can open it and write on it.
 */
object Imports {
  /** A PDF from her files: its pages, named after the file. */
  suspend fun pdf(app: TalisApp, uri: Uri, folderId: String?, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): NotebookMeta {
    val pages = app.repository.importPdf(uri, onProgress)
    if (pages.isEmpty()) throw com.tali.notes.data.NotesRepository.PdfImportException(tr(R.string.that_pdf_has_no_pages))
    val name = Incoming.nameOf(app, uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() } ?: "PDF"
    return build(app, DocKind.PDF, name, folderId, pages)
  }

  /** Pictures picked together: one item, a picture on each page. Null if none could be opened. */
  suspend fun pictures(app: TalisApp, uris: List<Uri>, folderId: String?): NotebookMeta? {
    val pages = stored(app, uris)
    if (pages.isEmpty()) return null
    val title = if (pages.size > 1) english(R.string.n_pictures, pages.size) else english(R.string.picture)
    return build(app, DocKind.PICTURE, title, folderId, pages)
  }

  /** Pages from the document scanner (already cropped and straightened): one page each, named by the day. */
  suspend fun scan(app: TalisApp, uris: List<Uri>, folderId: String?): NotebookMeta? {
    val pages = stored(app, uris)
    if (pages.isEmpty()) return null
    val locale = java.util.Locale.ENGLISH
    val day = java.text.SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(locale, "dMMM"), locale).format(java.util.Date())
    return build(app, DocKind.SCAN, english(R.string.scan_n, day), folderId, pages)
  }

  /** Copies pictures into the app, upright and downscaled; their size in pixels stands in for the page size. */
  private suspend fun stored(app: TalisApp, uris: List<Uri>): List<ImportedPage> =
    uris.mapNotNull { uri ->
      val file = app.repository.importImage(uri) ?: return@mapNotNull null
      val bmp = withContext(Dispatchers.IO) { app.repository.images.loadNow(file) } ?: return@mapNotNull null
      ImportedPage(file, bmp.width.toFloat(), bmp.height.toFloat())
    }

  private suspend fun build(app: TalisApp, kind: DocKind, title: String, folderId: String?, pages: List<ImportedPage>): NotebookMeta {
    val repo = app.repository
    // Wide pages (slides, a photo taken sideways) lie on landscape A4 pages.
    val landscape = pages.count { it.widthPt > it.heightPt } * 2 > pages.size
    val layout = Pages.of(landscape)
    val meta = repo.create(title, PaperStyle.BLANK, folderId = folderId, pages = pages.size, landscape = landscape, kind = kind)
    val items =
      pages.mapIndexed { i, p ->
        val r = layout.rect(i)
        val rect =
          if (kind == DocKind.PICTURE) {
            // A picture sits in the middle of its page with a margin round it, like a print.
            val room = Rect(r.left + MARGIN, r.top + MARGIN, r.right - MARGIN, r.bottom - MARGIN)
            val s = minOf(room.width / p.widthPt, room.height / p.heightPt)
            val w = p.widthPt * s
            val h = p.heightPt * s
            Rect(room.center.x - w / 2, room.center.y - h / 2, room.center.x + w / 2, room.center.y + h / 2)
          } else {
            // A PDF page or a scan fills its page's width from the top (as a PDF added to a notebook
            // does), leaving room below a wide slide for notes.
            val s = minOf(r.width / p.widthPt, r.height / p.heightPt)
            val x = r.left + (r.width - p.widthPt * s) / 2f
            Rect(x, r.top, x + p.widthPt * s, r.top + p.heightPt * s)
          }
        ImageItem(UUID.randomUUID().toString(), i.toLong(), p.file, rect, pdfPage = kind != DocKind.PICTURE)
      }
    repo.applyChange(meta.id, Change(emptyList(), items))
    // Its preview: the top of the first page, as the library shows notebooks.
    withContext(Dispatchers.Default) {
      runCatching {
        val page = layout.rect(0)
        val region = Rect(page.left, page.top, page.right, minOf(page.bottom, page.top + page.width / 1.32f))
        items.filter { it.bounds.intersects(region) }.forEach { repo.images.loadNow(it.file) }
        val bmp = Exporter.thumbnail(items, region, PaperStyle.BLANK, 640, repo.images, originY = page.top, page = page, clip = { layout.clipFor(it, pages.size, false) })
        repo.saveThumbnail(meta.id, bmp)
      }
    }
    // Something new (a photo, a scan, a PDF): to Drive straight away, so her other devices get it.
    com.tali.notes.backup.DriveBackup.backupSoon(app)
    return meta
  }

  /** Room round a picture on its page, in page units (about 1 cm). */
  private const val MARGIN = 48f
}
