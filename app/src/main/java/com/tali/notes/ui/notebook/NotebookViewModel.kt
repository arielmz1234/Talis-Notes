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
import com.tali.notes.geometry.Rect
import com.tali.notes.model.CustomShape
import com.tali.notes.model.EraserMode
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenSlot
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val CUSTOM_PREFIX = "custom:"

enum class PanelKind {
  PEN,
  ERASER,
  LASSO,
  SHAPES,
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
  var shapeKind by mutableStateOf(runCatching { ShapeKind.valueOf(store.settings.value.lastShape) }.getOrDefault(ShapeKind.LINE))
    private set

  /** The saved shape the shape tool draws, if she picked one of hers. */
  var customShapeId by mutableStateOf(store.settings.value.lastShape.removePrefix(CUSTOM_PREFIX).takeIf { store.settings.value.lastShape.startsWith(CUSTOM_PREFIX) })
    private set

  var hint by mutableStateOf<String?>(null)

  /** The imported PDF page on screen and how many there are; index -1 when there are none. */
  var page by mutableStateOf(-1 to 0)

  init {
    viewModelScope.launch {
      initialViewport = meta.value?.viewport ?: repo.notebooks.value.firstOrNull { it.id == notebookId }?.viewport
      val items = repo.loadItems(notebookId)
      val doc = Document(items)
      doc.listeners += { change -> repo.applyChange(notebookId, change) }
      doc.onHistoryChanged = { u, r ->
        canUndo = u
        canRedo = r
      }
      document = doc
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

  fun setPenType(type: PenType) =
    updateActivePen { slot ->
      val size = if (slot.type == type) slot.size else type.defaultSize
      val color = if (type.isHighlighter && !slot.type.isHighlighter) 0xFFFFD60A.toInt() else slot.color
      slot.copy(type = type, size = size, color = color)
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

  fun saveColor(color: Int) = store.update { s -> if (color in s.savedColors) s else s.copy(savedColors = s.savedColors + color) }

  fun removeColor(color: Int) = store.update { s -> s.copy(savedColors = s.savedColors - color) }

  fun setEraserAffects(type: PenType, on: Boolean) = store.update { it.copy(eraserAffects = it.eraserAffects + (type to on)) }

  fun setLassoAffects(type: PenType, on: Boolean) = store.update { it.copy(lassoAffects = it.lassoAffects + (type to on)) }

  fun setLassoImages(on: Boolean) = store.update { it.copy(lassoSelectsImages = on) }

  fun setEraserMode(mode: EraserMode) = store.update { it.copy(eraserMode = mode) }

  fun setEraserSize(size: Float) = store.update { it.copy(eraserSize = size) }

  fun setHoldToShape(on: Boolean) = store.update { it.copy(holdToShape = on) }

  fun setHoldDelay(ms: Long) = store.update { it.copy(holdDelayMs = ms) }

  fun setFingerDraws(value: Boolean?) = store.update { it.copy(fingerDraws = value) }

  fun setPaper(style: PaperStyle) {
    repo.setPaper(notebookId, style)
    store.update { it.copy(defaultPaper = style) }
  }

  fun rename(title: String) = repo.rename(notebookId, title.ifBlank { "Untitled" })

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
    viewModelScope.launch(Dispatchers.Default) {
      // Preview the writing she last saw, framed to the card's proportions.
      val seen = items.map { it.bounds }.filter { it.intersects(visible) }.reduceOrNull(Rect::union)?.outset(48f) ?: visible
      val center = seen.center
      var w = maxOf(seen.width, seen.height * 1.32f, 560f)
      w = minOf(w, visible.width * 1.5f)
      val h = w / 1.32f
      val region = Rect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2)
      val bmp = runCatching { Exporter.thumbnail(items, region, paper, 640, repo.images) }.getOrNull() ?: return@launch
      repo.saveThumbnail(notebookId, bmp)
    }
  }

  suspend fun exportPdf(layout: PdfLayout): File? {
    val doc = document ?: return null
    val items = doc.all.toList()
    val paper = meta.value?.paper ?: PaperStyle.DOTS
    val title = (meta.value?.title ?: "Notebook").replace(Regex("[^\\p{L}\\p{N} _-]"), "").ifBlank { "Notebook" }
    return withContext(Dispatchers.Default) {
      val out = File(File(getApplication<Application>().cacheDir, "exports"), "$title.pdf")
      runCatching { Exporter.pdf(items, paper, layout, out, repo.images) }.map { out }.getOrNull()
    }
  }
}
