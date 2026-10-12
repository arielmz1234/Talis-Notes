package com.tali.notes.canvas

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import com.tali.notes.geometry.Rect
import com.tali.notes.model.ImageItem
import java.io.File
import java.util.concurrent.Executors

/**
 * Imported PDF pages, sharp at any zoom, the way PDF apps do it. A page is shown from its picture
 * (made on import, about 190 dpi: quick to open and scroll); the original PDF is kept beside it
 * ([pdfFileFor]), and once the view settles zoomed in past what the picture holds, the part of each
 * page on screen is drawn again from the PDF at screen resolution ([Patch]), off the main thread.
 * The patch is drawn in the page's own place among the tiles, so writing stays on top of it.
 *
 * Which PDF and page a picture came from is in its file name ([pageFileName]), so copies of a
 * notebook, backups and restores keep the link with nothing else stored.
 */
class SharpPdf(private val imagesDir: File, private val onReady: (Rect) -> Unit) {
  /** A sharp piece of [itemRect] (the page's place when it was drawn): [rect] in world units. */
  class Patch(val bitmap: Bitmap, val rect: Rect, val itemRect: Rect)

  /** Read on the main thread by the renderer. */
  @Volatile var patches: Map<String, Patch> = emptyMap()
    private set

  private val main = Handler(Looper.getMainLooper())
  @Volatile private var generation = 0
  private var open: Pair<String, PdfRenderer>? = null
  private var openFd: ParcelFileDescriptor? = null

  /**
   * The view settled at [scale] (screen pixels per world unit) showing [visible]: make sure each PDF
   * page in view has a sharp patch if its picture is too coarse, and drop the rest.
   * [basePxPerUnit] gives a page picture's own pixels per world unit, or null if not loaded yet.
   */
  fun refresh(items: List<ImageItem>, visible: Rect, scale: Float, screenPixels: Int, basePxPerUnit: (ImageItem) -> Float?) {
    val gen = ++generation
    val wanted = ArrayList<Pair<ImageItem, Rect>>()
    for (item in items) {
      if (sourceOf(item.file) == null || item.flipX || item.flipY) continue
      val base = basePxPerUnit(item) ?: continue
      if (scale <= base * 1.1f) continue
      // What's on screen of the page, and a little round it so a small pan is still sharp.
      val margin = 0.15f
      val v = Rect(visible.left - visible.width * margin, visible.top - visible.height * margin, visible.right + visible.width * margin, visible.bottom + visible.height * margin)
      val r = intersect(item.rect, v) ?: continue
      wanted += item to r
    }
    // Patches that are still right stay; the others go.
    val keep = patches.filter { (id, p) -> wanted.any { (it, r) -> it.id == id && it.rect == p.itemRect && contains(p.rect, intersect(r, visibleOnly(visible, it.rect)) ?: r) && closeScale(p, scale) } }
    if (keep.size != patches.size) {
      val gone = patches.filterKeys { it !in keep }.values.map { it.rect }
      patches = keep
      gone.forEach(onReady)
    }
    val todo = wanted.filter { (it, _) -> it.id !in keep }
    if (todo.isEmpty()) return
    // A pixel budget for all of them together (a few screens' worth).
    val total = todo.sumOf { (_, r) -> (r.width * scale * r.height * scale).toDouble() }
    val k = scale * minOf(1.0, kotlin.math.sqrt(MAX_SCREENS * screenPixels / total)).toFloat()
    worker.execute {
      for ((item, r) in todo) {
        if (gen != generation) return@execute
        val bmp = runCatching { render(item, r, k) }.getOrNull() ?: continue
        main.post {
          if (gen != generation) return@post
          patches = patches + (item.id to Patch(bmp, r, item.rect))
          onReady(r)
        }
      }
    }
  }

  /** Nothing sharp (the view moved off the pages, or is going away). */
  fun clear() {
    generation++
    val gone = patches.values.map { it.rect }
    patches = emptyMap()
    gone.forEach(onReady)
  }

  /** The canvas is going away: drop the patches and close the PDF. */
  fun release() {
    clear()
    worker.execute {
      open?.second?.close()
      openFd?.close()
      open = null
      openFd = null
    }
  }

  /** Renders [part] of [item]'s page at [k] pixels per world unit. Worker thread only. */
  private fun render(item: ImageItem, part: Rect, k: Float): Bitmap? {
    val (pdfId, index) = sourceOf(item.file) ?: return null
    val renderer = rendererFor(pdfId) ?: return null
    if (index !in 0 until renderer.pageCount) return null
    val w = (part.width * k).toInt().coerceIn(1, MAX_SIDE)
    val h = (part.height * k).toInt().coerceIn(1, MAX_SIDE)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.WHITE)
    renderer.openPage(index).use { page ->
      // Page points → world (the page fills its rect) → this bitmap.
      val sx = w / part.width
      val sy = h / part.height
      val m = Matrix()
      m.setScale(item.rect.width / page.width * sx, item.rect.height / page.height * sy)
      m.postTranslate((item.rect.left - part.left) * sx, (item.rect.top - part.top) * sy)
      page.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }
    return bmp
  }

  private fun rendererFor(pdfId: String): PdfRenderer? {
    open?.let { if (it.first == pdfId) return it.second }
    open?.second?.close()
    openFd?.close()
    open = null
    openFd = null
    val file = pdfFileFor(imagesDir, pdfId)
    if (!file.isFile) return null
    return runCatching {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(fd)
        openFd = fd
        open = pdfId to r
        r
      }
      .getOrNull()
  }

  private fun closeScale(p: Patch, scale: Float): Boolean {
    val had = p.bitmap.width / p.rect.width
    return had >= scale * 0.9f && had <= scale * 1.6f
  }

  companion object {
    /** One thread for every canvas (one notebook is open at a time). */
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "sharp-pdf").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }
    private const val MAX_SIDE = 4096
    private const val MAX_SCREENS = 3.0

    private val NAME = Regex("""^pdf-([0-9a-f\-]{36})-p(\d+)-""")

    /** The picture of page [index] (from 0) of the PDF kept as [pdfId]. */
    fun pageFileName(pdfId: String, index: Int): String = "pdf-$pdfId-p$index-${java.util.UUID.randomUUID()}.jpg"

    fun pdfFileFor(imagesDir: File, pdfId: String) = File(imagesDir, "pdf-$pdfId.pdf")

    /** The PDF and page a picture was made from, if it was. */
    fun sourceOf(file: String): Pair<String, Int>? = NAME.find(file)?.let { it.groupValues[1] to it.groupValues[2].toInt() }

    /** The kept PDFs these pictures came from (for backing a notebook up on its own). */
    fun pdfsFor(imagesDir: File, files: Collection<String>): List<File> =
      files.mapNotNull { sourceOf(it)?.first }.distinct().map { pdfFileFor(imagesDir, it) }.filter { it.isFile }

    private fun intersect(a: Rect, b: Rect): Rect? {
      val l = maxOf(a.left, b.left)
      val t = maxOf(a.top, b.top)
      val r = minOf(a.right, b.right)
      val btm = minOf(a.bottom, b.bottom)
      return if (r > l && btm > t) Rect(l, t, r, btm) else null
    }

    private fun visibleOnly(visible: Rect, item: Rect): Rect = intersect(visible, item) ?: visible

    private fun contains(outer: Rect, inner: Rect) = outer.left <= inner.left + 0.5f && outer.top <= inner.top + 0.5f && outer.right >= inner.right - 0.5f && outer.bottom >= inner.bottom - 0.5f
  }
}
