package com.tali.notes.ui.notebook

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tali.notes.canvas.Document
import com.tali.notes.canvas.SelectionInfo
import com.tali.notes.data.AppSettings
import com.tali.notes.data.NotebookMeta
import com.tali.notes.data.NotesRepository
import com.tali.notes.data.Viewport
import com.tali.notes.export.Exporter
import com.tali.notes.export.PdfLayout
import com.tali.notes.export.PdfQuality
import com.tali.notes.model.CustomPaper
import com.tali.notes.geometry.Rect
import com.tali.notes.model.CustomShape
import com.tali.notes.model.ShapeItem
import com.tali.notes.model.InkItem
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.EraserMode
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenSlot
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeGeometry
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.Tool
import com.tali.notes.talisApp
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val CUSTOM_PREFIX = "custom:"

enum class PanelKind {
  PEN,
  ERASER,
  LASSO,
  SHAPES,
  FILL,
}

class NotebookViewModel(app: Application, val notebookId: String) : AndroidViewModel(app) {
  private val repo = app.talisApp.repository
  private val store = app.talisApp.settings

  val settings: StateFlow<AppSettings> = store.settings
  val meta: StateFlow<NotebookMeta?> =
    repo.notebooks.map { list -> list.firstOrNull { it.id == notebookId } }.stateIn(viewModelScope, SharingStarted.Eagerly, repo.notebooks.value.firstOrNull { it.id == notebookId })

  var document by mutableStateOf<Document?>(null)
    private set

  /** Viewport captured when the notebook was opened; the canvas restores it once. */
  var initialViewport: Viewport? = null
    private set

  var tool by mutableStateOf(Tool.PEN)
  var panel by mutableStateOf<PanelKind?>(null)
  var canUndo by mutableStateOf(false)
    private set

  var canRedo by mutableStateOf(false)
    private set

  var selection by mutableStateOf<SelectionInfo?>(null)
  var zoom by mutableStateOf(1f)
  var shapeKind by mutableStateOf(runCatching { ShapeKind.valueOf(store.settings.value.lastShape) }.getOrDefault(ShapeKind.LINE).let { if (it == ShapeKind.SQUARE) ShapeKind.RECTANGLE else it })
    private set

  /** The saved shape the shape tool draws, if she picked one of hers. */
  var customShapeId by mutableStateOf(store.settings.value.lastShape.removePrefix(CUSTOM_PREFIX).takeIf { store.settings.value.lastShape.startsWith(CUSTOM_PREFIX) })
    private set

  var hint by mutableStateOf<String?>(null)

  /** Toolbars hidden: only the page and a small button to bring them back. */
  var focus by mutableStateOf(false)

  /** The text block being typed, if any. */
  var textEdit by mutableStateOf<com.tali.notes.canvas.TextEdit?>(null)

  /** Changes whenever the view moves, so the text editor can follow the page. */
  var cameraTick by mutableStateOf(0)

  /** The page on screen and how many there are; index -1 when there are none. */
  var page by mutableStateOf(-1 to 0)

  /**
   * A4 page count, or null for an endless canvas. Kept here as well as in the database so the
   * canvas never flickers back to the old count while the write is on its way.
   */
  var pageCount by mutableStateOf(meta.value?.pages)
    private set

  init {
    viewModelScope.launch {
      val m = meta.value ?: repo.notebooks.value.firstOrNull { it.id == notebookId }
      initialViewport = m?.viewport
      pageCount = m?.pages
      val items = repo.loadItems(notebookId)
      val doc = Document(items)
      doc.listeners += { change ->
        repo.applyChange(notebookId, change)
        revision++
      }
      doc.onHistoryChanged = { u, r ->
        canUndo = u
        canRedo = r
      }
      document = doc
      warmUp(items, initialViewport)
      // A first-time nudge towards the app's best trick.
      kotlinx.coroutines.delay(1500)
      // One tip per visit, in this order, each shown once.
      if ("hold" !in settings.value.seenTips) tip("hold", "Tip: at the end of a line or shape, hold the pen still and it snaps clean")
      else tip("spen", "Tip: hold the S Pen's side button to erase, and let go to keep writing")
    }
  }

  /**
   * Builds the loaded strokes in the background, the ones nearest the saved view first, on every
   * core. The screen draws immediately; anything it reaches first is simply built on the spot.
   */
  private fun warmUp(items: List<CanvasItem>, viewport: Viewport?) {
    val pending = items.filter { (it is InkItem && !it.isBuilt) || it is ShapeItem }
    if (pending.isEmpty()) return
    val cx = (viewport?.x ?: 0f) + 600f / (viewport?.zoom ?: 1f)
    val cy = (viewport?.y ?: 0f) + 400f / (viewport?.zoom ?: 1f)
    val queue = pending.sortedBy { val c = it.bounds.center; (c.x - cx) * (c.x - cx) + (c.y - cy) * (c.y - cy) }
    val next = java.util.concurrent.atomic.AtomicInteger(0)
    val workers = Runtime.getRuntime().availableProcessors().coerceIn(1, 6)
    repeat(workers) {
      viewModelScope.launch(Dispatchers.Default) {
        // Stops as soon as she leaves the notebook (the scope is cancelled).
        while (isActive) {
          val item = queue.getOrNull(next.getAndIncrement()) ?: break
          runCatching {
            when (item) {
              is InkItem -> item.stroke
              is ShapeItem -> item.strokes
              else -> Unit
            }
          }
          com.tali.notes.canvas.StrokeBuilds.progress()
        }
      }
    }
  }

  // ---- Tools and panels ----

  fun onToolTapped(t: Tool) {
    val panelFor =
      when (t) {
        Tool.ERASER -> PanelKind.ERASER
        Tool.LASSO -> PanelKind.LASSO
        Tool.SHAPE -> PanelKind.SHAPES
        Tool.PEN -> PanelKind.PEN
        Tool.FILL -> PanelKind.FILL
        // The laser has nothing to set.
        Tool.LASER -> null
      }
    if (tool == t || t == Tool.SHAPE) {
      panel = if (panel == panelFor) null else panelFor
      if (t == Tool.SHAPE) tool = Tool.SHAPE
    } else {
      tool = t
      panel = null
    }
  }

  fun onPenTapped(slot: PenSlot) {
    if (tool == Tool.PEN && settings.value.activePen == slot.id) {
      panel = if (panel == PanelKind.PEN) null else PanelKind.PEN
    } else {
      store.update { it.copy(activePen = slot.id) }
      tool = Tool.PEN
      panel = null
    }
  }

  /** The arrow beside the pens: opens or closes the active pen's settings, switching to the pen if needed. */
  fun togglePenSettings() {
    tool = Tool.PEN
    panel = if (panel == PanelKind.PEN) null else PanelKind.PEN
  }

  fun pickShape(kind: ShapeKind) {
    shapeKind = kind
    customShapeId = null
    tool = Tool.SHAPE
    panel = null
    store.update { it.copy(lastShape = kind.name) }
  }

  fun pickCustomShape(shape: CustomShape) {
    customShapeId = shape.id
    tool = Tool.SHAPE
    panel = null
    store.update { it.copy(lastShape = CUSTOM_PREFIX + shape.id) }
  }

  fun saveCustomShape(name: String, geometry: ShapeGeometry) {
    val shape = CustomShape(UUID.randomUUID().toString().take(8), name.trim().ifBlank { "My shape" }, geometry)
    store.update { it.copy(customShapes = it.customShapes + shape) }
  }

  fun renameCustomShape(id: String, name: String) =
    store.update { s -> s.copy(customShapes = s.customShapes.map { if (it.id == id) it.copy(name = name.trim().ifBlank { it.name }) else it }) }

  fun deleteCustomShape(id: String) {
    val wasCurrent = customShapeId == id
    if (wasCurrent) customShapeId = null
    store.update { s -> s.copy(customShapes = s.customShapes.filterNot { it.id == id }, lastShape = if (wasCurrent) shapeKind.name else s.lastShape) }
  }

  fun updateActivePen(transform: (PenSlot) -> PenSlot) =
    store.update { s -> s.copy(pens = s.pens.map { if (it.id == s.activeSlot.id) transform(it) else it }) }

  /** A colour chosen for the active pen (from the palette, the picker or the quick colours). */
  fun pickPenColor(color: Int) = updateActivePen { it.pick(color) }

  /** The active pen's line: solid, dotted or dashed. */
  fun setPenLine(line: LineStyle) = updateActivePen { it.copy(line = line) }

  fun setPaperLineWeight(weight: Float) = store.update { it.copy(paperLineWeight = weight.coerceIn(0.5f, 5f)) }

  fun setPenType(type: PenType) =
    updateActivePen { slot ->
      // Her thickness stays when she changes the pen type (only kept within what the new pen can do).
      val size = slot.size.coerceIn(type.minSize, type.maxSize)
      // ...and so does her colour.
      slot.copy(type = type, size = size, sizes = slot.presetSizes.map { it.coerceIn(type.minSize, type.maxSize) })
    }

  fun addPen() =
    store.update { s ->
      if (s.pens.size >= 8) return@update s
      val slot = PenSlot(UUID.randomUUID().toString().take(8), PenType.FINELINER, s.savedColors.firstOrNull() ?: 0xFF1B1F2A.toInt(), PenType.FINELINER.defaultSize)
      s.copy(pens = s.pens + slot, activePen = slot.id)
    }

  fun removeActivePen() =
    store.update { s ->
      if (s.pens.size <= 1) return@update s
      val remaining = s.pens.filterNot { it.id == s.activeSlot.id }
      s.copy(pens = remaining, activePen = remaining.first().id)
    }

  /** Edits the palette the active pen uses (the highlighter has its own). */
  private fun updatePalette(transform: (List<Int>) -> List<Int>) =
    store.update { s ->
      if (s.activeSlot.type.isHighlighter) s.copy(highlighterColors = transform(s.highlighterColors)) else s.copy(savedColors = transform(s.savedColors))
    }

  fun saveColor(color: Int) = updatePalette { if (color in it) it else it + color }

  fun removeColor(color: Int) = updatePalette { it - color }

  /** Her colours in a new order (the toolbar shows the first few). */
  fun reorderColors(order: List<Int>) = updatePalette { if (order.toSet() == it.toSet()) order else it }

  /** Whether this tablet has a pen (the star and the S Pen button cycle are only offered then). */
  val hasStylus: Boolean by lazy {
    android.view.InputDevice.getDeviceIds().any { id -> android.view.InputDevice.getDevice(id)?.supportsSource(android.view.InputDevice.SOURCE_STYLUS) == true }
  }

  /** Stars or unstars the active pen for cycling with the S Pen button. */
  fun toggleStar() {
    val on = !settings.value.activeSlot.starred
    updateActivePen { it.copy(starred = on) }
    if (on) {
      val count = settings.value.pens.count { it.starred }
      showHint(
        if (count < 2) "Starred. Star another pen, then double-press the S Pen button to switch to the next starred pen, or triple-press to go back"
        else "Starred. Double-press the S Pen button to switch to the next starred pen, or triple-press to go back"
      )
    }
  }

  /**
   * A double (+1) or triple (-1) press of the S Pen button: the next or previous starred pen, round
   * and round. From another tool, or a pen that isn't starred, it starts at the first (or last).
   */
  fun cyclePen(step: Int) {
    val s = settings.value
    val starred = s.pens.filter { it.starred }
    // Nothing starred (the default): the presses do nothing at all.
    if (starred.isEmpty()) return
    val at = starred.indexOfFirst { it.id == s.activePen }
    val next =
      if (tool != Tool.PEN || at < 0) (if (step > 0) starred.first() else starred.last())
      else starred[Math.floorMod(at + step, starred.size)]
    store.update { it.copy(activePen = next.id) }
    tool = Tool.PEN
    panel = null
    // With the toolbars hidden she can't see which pen is up, so say it.
    if (focus) showHint(next.type.label)
  }

  /** Switches the active pen to its [i]th thickness. */
  fun pickPenSize(i: Int) = updateActivePen { it.pickSize(i) }

  /** Sets the active pen's [i]th thickness. */
  fun setPenPreset(i: Int, v: Float) = updateActivePen { it.setPreset(i, v.coerceIn(it.type.minSize, it.type.maxSize)) }

  /** How many colours the active pen shows in the toolbar (each pen has its own). */
  fun setToolbarColors(n: Int) = updateActivePen { it.copy(toolbarColors = n.coerceIn(0, 6)) }

  fun setEraserAffects(type: PenType, on: Boolean) = store.update { it.copy(eraserAffects = it.eraserAffects + (type to on)) }

  fun setLassoAffects(type: PenType, on: Boolean) = store.update { it.copy(lassoAffects = it.lassoAffects + (type to on)) }

  fun setLassoImages(on: Boolean) = store.update { it.copy(lassoSelectsImages = on) }

  fun setLassoLocked(on: Boolean) = store.update { it.copy(lassoLocked = on) }

  fun setLassoFree(on: Boolean) = store.update { it.copy(lassoFree = on) }

  fun setEraserMode(mode: EraserMode) = store.update { it.copy(eraserMode = mode) }

  fun setEraserSize(size: Float) = store.update { it.copy(eraserSize = size) }

  fun setHoldToShape(on: Boolean) = store.update { it.copy(holdToShape = on) }

  fun setHoldDelay(ms: Long) = store.update { it.copy(holdDelayMs = ms) }

  fun setFingerDraws(value: Boolean?) = store.update { it.copy(fingerDraws = value) }

  fun setPageGuides(on: Boolean) = store.update { it.copy(pageGuides = on) }

  fun setFillColor(color: Int?) = store.update { it.copy(fillColor = color) }

  fun setFillOpacity(opacity: Float) = store.update { it.copy(fillOpacity = opacity.coerceIn(0.15f, 1f)) }

  fun setSmartUndo(on: Boolean) = store.update { it.copy(smartUndo = on) }

  fun setHideNavBar(on: Boolean) = store.update { it.copy(hideNavBar = on) }

  fun setInkBetweenPages(on: Boolean) = store.update { it.copy(inkBetweenPages = on) }



  fun setPaper(style: PaperStyle) {
    repo.setPaper(notebookId, style)
    store.update { it.copy(defaultPaper = style, defaultCustomPaper = null) }
  }

  /** This notebook's own background, if it uses one of hers (and it still exists). */
  fun customPaperOf(m: NotebookMeta?, s: AppSettings): CustomPaper? = m?.customPaper?.let { id -> s.customPapers.firstOrNull { it.id == id } }

  fun setCustomPaper(id: String) {
    repo.setCustomPaper(notebookId, id)
    store.update { it.copy(defaultCustomPaper = id) }
  }

  /** Saves a background she made and puts it on this notebook. */
  fun addCustomPaper(paper: CustomPaper) {
    store.update { it.copy(customPapers = it.customPapers + paper) }
    setCustomPaper(paper.id)
  }

  /** Removes one of her backgrounds; notebooks that used it show blank paper. */
  fun deleteCustomPaper(id: String) =
    store.update { s -> s.copy(customPapers = s.customPapers.filterNot { it.id == id }, defaultCustomPaper = s.defaultCustomPaper?.takeIf { it != id }) }

  /** Copies a picture in for a background; returns its file name. */
  suspend fun importPaperImage(uri: Uri): String? = repo.importImage(uri)

  /**
   * A picture of page [index] for the page overview, [widthPx] wide. [items] is a snapshot of the
   * notebook taken on the main thread.
   */
  suspend fun pageThumbnail(items: List<CanvasItem>, index: Int, widthPx: Int): Bitmap? {
    val m = meta.value
    val custom = customPaperOf(m, settings.value)
    val page = pageLayout.rect(index)
    return withContext(Dispatchers.Default) {
      runCatching { Exporter.thumbnail(items.filter { it.bounds.intersects(page) }, page, m?.paper ?: PaperStyle.DOTS, widthPx, repo.images, m?.dark == true, originY = page.top, custom = custom, page = page) }.getOrNull()
    }
  }

  /** Portrait or landscape A4 pages, as chosen when the notebook was made. */
  val pageLayout: com.tali.notes.canvas.Pages
    get() = com.tali.notes.canvas.Pages.of(meta.value?.landscape == true)

  fun rename(title: String) = repo.rename(notebookId, title.ifBlank { "Untitled" })

  /** A number of A4 pages, or null to make it an endless canvas. */
  fun setPages(count: Int?) {
    pageCount = count
    repo.setPages(notebookId, count)
  }

  fun setDark(on: Boolean) = repo.setDark(notebookId, on)

  /** Shows [message] the first time [id] comes up, never again after that. */
  fun tip(id: String, message: String) {
    if (id in settings.value.seenTips) return
    store.update { it.copy(seenTips = it.seenTips + id) }
    hint = message
  }

  fun showHint(message: String) {
    hint = message
  }

  // ---- Images ----

  suspend fun importImage(uri: Uri): Pair<String, Bitmap>? {
    val name = repo.importImage(uri) ?: return null
    val bmp = withContext(Dispatchers.IO) { repo.images.loadNow(name) } ?: return null
    return name to bmp
  }

  /** Renders a PDF's pages to pictures, showing progress in the hint. Returns null (with a message) on failure. */
  suspend fun importPdf(uri: Uri): List<NotesRepository.ImportedPage>? =
    try {
      hint = "Opening PDF…"
      repo.importPdf(uri) { done, total -> hint = "Adding pages… $done of $total" }.ifEmpty { null }.also { if (it == null) hint = "That PDF has no pages" }
    } catch (e: NotesRepository.PdfImportException) {
      hint = e.message
      null
    } catch (t: Throwable) {
      com.tali.notes.CrashLog.recovered(getApplication(), t)
      hint = "Couldn't read that PDF"
      null
    }

  // ---- Saving and export ----

  /** Remembers where she was looking and refreshes the library preview. */
  fun persistView(viewport: Viewport, visible: Rect) {
    repo.saveViewport(notebookId, viewport)
    val doc = document ?: return
    val items = doc.all.toList()
    val paper = meta.value?.paper ?: PaperStyle.DOTS
    val dark = meta.value?.dark == true
    val pages = pageCount
    val custom = customPaperOf(meta.value, settings.value)
    viewModelScope.launch(Dispatchers.Default) {
      if (pages != null) {
        // A paged notebook previews the top of the page she was on, like a notebook lying open.
        val layout = pageLayout
        val page = layout.rect(layout.indexAt(visible.top + visible.height * 0.4f).coerceIn(0, pages - 1))
        val region = Rect(page.left, page.top, page.right, minOf(page.bottom, page.top + page.width / 1.32f))
        val bmp = runCatching { Exporter.thumbnail(items, region, paper, 640, repo.images, dark, originY = page.top, custom = custom, page = page) }.getOrNull() ?: return@launch
        repo.saveThumbnail(notebookId, bmp)
        return@launch
      }
      // Preview the writing she last saw, framed to the card's proportions.
      val seen = items.map { it.bounds }.filter { it.intersects(visible) }.reduceOrNull(Rect::union)?.outset(48f) ?: visible
      val center = seen.center
      var w = maxOf(seen.width, seen.height * 1.32f, 560f)
      w = minOf(w, visible.width * 1.5f)
      val h = w / 1.32f
      val region = Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
      val bmp = runCatching { Exporter.thumbnail(items, region, paper, 640, repo.images, dark, custom = custom) }.getOrNull() ?: return@launch
      repo.saveThumbnail(notebookId, bmp)
    }
  }

  /** Bumped on every edit, so a PDF made for the export dialog's size is only reused while it's current. */
  private var revision = 0
  private val exported = HashMap<String, File>()
  private val exporting = kotlinx.coroutines.sync.Mutex()

  /**
   * The PDF for [layout] at [quality]. Each one is made once and kept until the notebook changes,
   * so the size the export dialog shows is the file that is then shared or saved.
   */
  suspend fun exportPdf(layout: PdfLayout, quality: PdfQuality): File? =
    exporting.withLock {
      val doc = document ?: return@withLock null
      val m = meta.value
      val pages = pageCount
      val paper = m?.paper ?: PaperStyle.DOTS
      val dark = m?.dark == true
      val custom = customPaperOf(m, settings.value)
      val landscape = m?.landscape == true
      val key = listOf(layout, quality, revision, pages, paper, dark, custom, m?.title, settings.value.pageGuides, landscape, settings.value.paperLineWeight).joinToString("|")
      exported[key]?.takeIf { it.exists() }?.let { return@withLock it }
      val items = doc.all.toList()
      val title = (m?.title ?: "Notebook").replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "Notebook" }
      withContext(Dispatchers.Default) {
        // One folder per choice, so the file she shares is still called after the notebook.
        val out = File(File(File(getApplication<Application>().cacheDir, "exports"), "${layout.name}-${quality.name}"), "$title.pdf")
        runCatching { Exporter.pdf(items, paper, layout, out, repo.images, fixedPages = settings.value.pageGuides, pageCount = pages, dark = dark, custom = custom, quality = quality, landscape = landscape) }.map { out }.getOrNull()
      }?.also { exported[key] = it }
    }
}
