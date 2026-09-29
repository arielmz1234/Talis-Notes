package com.tali.notes.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.tali.notes.canvas.Camera
import com.tali.notes.canvas.ItemRenderer
import com.tali.notes.data.ImageStore
import com.tali.notes.canvas.PaperRenderer
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.Brushes
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.ImageItem
import com.tali.notes.model.PaperStyle
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

enum class PdfLayout(val label: String) {
  A4_PAGES("A4 pages"),
  ONE_PAGE("One page, fitted to content"),

  /** Each imported PDF page (a slide) becomes a page again, with her notes around it. */
  PDF_PAGES("Same pages as the PDF"),
}

/** Software rendering for previews and PDFs; never touches the live screen. */
object Exporter {
  /** PDF points per world unit: 72 pt per inch, 160 dp per inch. */
  private const val PT_PER_DP = 72f / 160f
  private const val A4_W_PT = 595
  private const val A4_H_PT = 842
  private const val MARGIN_DP = 48f

  private fun renderer(images: ImageStore) = ItemRenderer(CanvasStrokeRenderer.create(forcePathRendering = true, textureStore = Brushes.textureStore), images, sync = true)

  /** Renders the region [world] into a bitmap of the given width, keeping the aspect ratio. */
  fun thumbnail(items: Collection<CanvasItem>, world: Rect, paper: PaperStyle, widthPx: Int, images: ImageStore): Bitmap {
    val heightPx = (widthPx * world.height / world.width).toInt().coerceIn(1, widthPx * 2)
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val scale = widthPx / world.width
    val camera = Camera(scale).apply { set(com.tali.notes.data.Viewport(world.left, world.top, 1f)) }
    PaperRenderer().draw(canvas, paper, camera, widthPx, heightPx)
    canvas.concat(camera.worldToScreen)
    renderer(images).drawAll(canvas, items.filter { it.bounds.intersects(world) }, camera.worldToScreen)
    return bmp
  }

  /** A4 page size in world units (dp), matching the PDF's points. */
  val A4_WIDTH = A4_W_PT / PT_PER_DP
  val A4_HEIGHT = A4_H_PT / PT_PER_DP

  /**
   * [fixedPages] lays A4 pages on the canvas's fixed page grid (the one the page guides show),
   * starting at the top-left of the canvas; otherwise the pages are centred on the writing.
   */
  fun pdf(items: Collection<CanvasItem>, paper: PaperStyle, layout: PdfLayout, out: File, images: ImageStore, fixedPages: Boolean = false) {
    val content = items.map { it.bounds }.reduceOrNull(Rect::union)?.outset(MARGIN_DP) ?: Rect(0f, 0f, 1322f, 1871f)
    val pages: List<Rect> =
      when (layout) {
        PdfLayout.ONE_PAGE -> splitTall(content)
        PdfLayout.A4_PAGES -> {
          val grid = if (fixedPages) fixedA4Pages(items) else a4Pages(content)
          grid.filter { page -> items.any { it.bounds.intersects(page) } }.ifEmpty { grid.take(1) }
        }
        PdfLayout.PDF_PAGES -> pdfPages(items).ifEmpty { splitTall(content) }
      }
    val doc = PdfDocument()
    val r = renderer(images)
    val paperRenderer = PaperRenderer().apply { print = true }
    try {
      pages.forEachIndexed { i, world ->
        val wPt = ceil(world.width * PT_PER_DP).toInt()
        val hPt = ceil(world.height * PT_PER_DP).toInt()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(wPt, hPt, i + 1).create())
        val canvas = page.canvas
        val camera = Camera(PT_PER_DP).apply { set(com.tali.notes.data.Viewport(world.left, world.top, 1f)) }
        paperRenderer.draw(canvas, paper, camera, wPt, hPt)
        canvas.save()
        canvas.concat(camera.worldToScreen)
        r.drawAll(canvas, items.filter { it.bounds.intersects(world) }, camera.worldToScreen)
        canvas.restore()
        doc.finishPage(page)
      }
      out.parentFile?.mkdirs()
      out.outputStream().use { doc.writeTo(it) }
    } finally {
      doc.close()
    }
  }

  /** PDF pages max out at 200 inches; very tall canvases are split into several tall pages. */
  private fun splitTall(content: Rect): List<Rect> {
    val maxDp = 14000f / PT_PER_DP
    if (content.height <= maxDp && content.width <= maxDp) return listOf(content)
    val out = mutableListOf<Rect>()
    var top = content.top
    while (top < content.bottom) {
      out += Rect(content.left, top, content.left + minOf(content.width, maxDp), minOf(content.bottom, top + maxDp))
      top += maxDp
    }
    return out
  }

  /**
   * One page per imported PDF page, top to bottom. Each page also takes the notes written around
   * it: anything whose middle lies between this page's top and the next page's top.
   */
  private fun pdfPages(items: Collection<CanvasItem>): List<Rect> {
    val pages = items.filterIsInstance<ImageItem>().filter { it.pdfPage }.sortedBy { it.rect.top }
    if (pages.isEmpty()) return emptyList()
    val others = items.filter { !(it is ImageItem && it.pdfPage) }
    return pages.mapIndexed { i, page ->
      val bandTop = if (i == 0) Float.NEGATIVE_INFINITY else page.rect.top
      val bandBottom = pages.getOrNull(i + 1)?.rect?.top ?: Float.POSITIVE_INFINITY
      others.filter { it.bounds.center.y in bandTop..<bandBottom }.map { it.bounds }.fold(page.rect) { acc, r -> acc.union(r) }
    }
  }

  /** The A4 pages of the fixed grid that the writing touches, row by row. */
  private fun fixedA4Pages(items: Collection<CanvasItem>): List<Rect> {
    val b = items.map { it.bounds }.reduceOrNull(Rect::union) ?: return listOf(Rect(0f, 0f, A4_WIDTH, A4_HEIGHT))
    val out = mutableListOf<Rect>()
    for (row in floor(b.top / A4_HEIGHT).toInt()..floor(b.bottom / A4_HEIGHT).toInt()) {
      for (col in floor(b.left / A4_WIDTH).toInt()..floor(b.right / A4_WIDTH).toInt()) {
        out += Rect(col * A4_WIDTH, row * A4_HEIGHT, (col + 1) * A4_WIDTH, (row + 1) * A4_HEIGHT)
      }
    }
    return out
  }

  /** Tiles the content into A4 portrait pages, row by row, top to bottom. */
  private fun a4Pages(content: Rect): List<Rect> {
    val pw = A4_W_PT / PT_PER_DP
    val ph = A4_H_PT / PT_PER_DP
    val cols = ceil(content.width / pw).toInt().coerceAtLeast(1)
    val rows = ceil(content.height / ph).toInt().coerceAtLeast(1)
    // Centre the grid of pages on the content.
    val left = content.center.x - cols * pw / 2f
    val top = content.top
    val out = mutableListOf<Rect>()
    for (row in 0 until rows) for (col in 0 until cols) {
      out += Rect(left + col * pw, top + row * ph, left + (col + 1) * pw, top + (row + 1) * ph)
    }
    return out
  }
}
