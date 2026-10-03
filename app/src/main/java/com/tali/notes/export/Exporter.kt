package com.tali.notes.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.tali.notes.canvas.Camera
import com.tali.notes.canvas.ItemRenderer
import com.tali.notes.canvas.Pages
import com.tali.notes.data.ImageStore
import com.tali.notes.canvas.PaperRenderer
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.Brushes
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.CustomPaper
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

/** How a PDF trades picture sharpness for file size. Writing always stays sharp lines. */
enum class PdfQuality(val label: String, val detail: String, private val dpi: Float?) {
  /** Pictures, slides and picture backgrounds at full resolution. */
  BEST("Best", "Pictures and slides at full resolution. The largest file.", null),

  /** Pictures reduced to print quality, 150 dpi at the size they print. */
  BALANCED("Balanced", "Pictures and slides at print quality. A medium file.", 150f),

  /** Pictures reduced to screen quality, 75 dpi: easy to send. */
  LIGHT("Light", "Pictures and slides at screen quality, easy to send. The smallest file.", 75f);

  /** Pixels per world unit (1/160 inch) pictures are reduced to, or null to keep them whole. */
  val imagePxPerDp: Float? = dpi?.let { it / 160f }
}

/** Software rendering for previews and PDFs; never touches the live screen. */
object Exporter {
  /** PDF points per world unit: 72 pt per inch, 160 dp per inch. */
  private const val PT_PER_DP = 72f / 160f
  private const val A4_W_PT = 595
  private const val A4_H_PT = 842
  private const val MARGIN_DP = 48f

  private fun renderer(images: ImageStore, dark: Boolean) =
    ItemRenderer(CanvasStrokeRenderer.create(forcePathRendering = true, textureStore = Brushes.textureStore), images, sync = true).also { it.dark = dark }

  /**
   * Renders the region [world] into a bitmap of the given width, keeping the aspect ratio. [page] is
   * the A4 page the region lies on in a paged notebook (a picture background fills it).
   */
  fun thumbnail(
    items: Collection<CanvasItem>,
    world: Rect,
    paper: PaperStyle,
    widthPx: Int,
    images: ImageStore,
    dark: Boolean = false,
    originY: Float = 0f,
    custom: CustomPaper? = null,
    page: Rect? = null,
    clip: ((CanvasItem) -> Rect?)? = null,
  ): Bitmap {
    val heightPx = (widthPx * world.height / world.width).toInt().coerceIn(1, widthPx * 2)
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val scale = widthPx / world.width
    val camera = Camera(scale).apply { set(com.tali.notes.data.Viewport(world.left, world.top, 1f)) }
    paperRenderer(dark, custom, images).apply {
      this.originY = originY
      pageRect = page
    }.draw(canvas, paper, camera, widthPx, heightPx)
    canvas.concat(camera.worldToScreen)
    renderer(images, dark).also { it.clipFor = clip }.drawAll(canvas, items.filter { it.bounds.intersects(world) }, camera.worldToScreen)
    return bmp
  }

  private fun paperRenderer(dark: Boolean, custom: CustomPaper?, images: ImageStore) =
    PaperRenderer().apply {
      this.dark = dark
      this.custom = custom
      customImage = custom?.image?.let(images::loadNow)
    }

  /** A4 page size in world units (dp), matching the PDF's points. */
  val A4_WIDTH = A4_W_PT / PT_PER_DP
  val A4_HEIGHT = A4_H_PT / PT_PER_DP

  /**
   * [fixedPages] lays A4 pages on the canvas's fixed page grid (the one the page guides show),
   * starting at the top-left of the canvas; otherwise the pages are centred on the writing. A paged
   * notebook ([pageCount] set) exports exactly its own pages, empty ones included. [dark] exports
   * dark paper as it looks on screen.
   */
  fun pdf(
    items: Collection<CanvasItem>,
    paper: PaperStyle,
    layout: PdfLayout,
    out: File,
    images: ImageStore,
    fixedPages: Boolean = false,
    pageCount: Int? = null,
    dark: Boolean = false,
    custom: CustomPaper? = null,
    quality: PdfQuality = PdfQuality.BEST,
    landscape: Boolean = false,
    inkBetweenPages: Boolean = false,
  ) {
    val pageLayout = Pages.of(landscape)
    val content = items.map { it.bounds }.reduceOrNull(Rect::union)?.outset(MARGIN_DP) ?: Rect(0f, 0f, 1322f, 1871f)
    val pages: List<Rect> =
      when (layout) {
        PdfLayout.ONE_PAGE -> splitTall(content)
        PdfLayout.A4_PAGES if pageCount != null -> pageLayout.all(pageCount)
        PdfLayout.A4_PAGES -> {
          val grid = if (fixedPages) fixedA4Pages(items) else a4Pages(content)
          grid.filter { page -> items.any { it.bounds.intersects(page) } }.ifEmpty { grid.take(1) }
        }
        PdfLayout.PDF_PAGES -> pdfPages(items).ifEmpty { splitTall(content) }
      }
    val doc = PdfDocument()
    // A paged notebook's writing shows only on its page, as on screen.
    val r = renderer(images, dark).apply {
      imagePxPerDp = quality.imagePxPerDp
      if (pageCount != null) clipFor = { item -> pageLayout.clipFor(item, pageCount, inkBetweenPages) }
    }
    val paperRenderer = paperRenderer(dark, custom, images).apply {
      print = true
      // A picture background shrinks like any picture (it fills an A4 page).
      val limit = quality.imagePxPerDp
      val bmp = customImage
      if (limit != null && bmp != null && bmp.width > pageLayout.width * limit) {
        val w = (pageLayout.width * limit).toInt()
        customImage = Bitmap.createScaledBitmap(bmp, w, (bmp.height.toLong() * w / bmp.width).toInt().coerceAtLeast(1), true)
      }
    }
    try {
      pages.forEachIndexed { i, world ->
        val wPt = ceil(world.width * PT_PER_DP).toInt()
        val hPt = ceil(world.height * PT_PER_DP).toInt()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(wPt, hPt, i + 1).create())
        paperRenderer.originY = if (pageCount != null) world.top else 0f
        paperRenderer.pageRect = if (pageCount != null) world else null
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
