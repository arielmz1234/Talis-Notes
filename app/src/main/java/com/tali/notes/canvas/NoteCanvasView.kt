package com.tali.notes.canvas

import com.tali.notes.tr
import com.tali.notes.R
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import android.widget.FrameLayout
import androidx.core.view.doOnAttach
import androidx.core.view.doOnLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.geometry.AffineTransform
import androidx.ink.geometry.ImmutableParallelogram
import androidx.ink.geometry.ImmutableSegment
import androidx.ink.geometry.ImmutableVec
import androidx.ink.geometry.Intersection.intersects
import androidx.ink.geometry.Parallelogram
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor
import com.tali.notes.data.AppSettings
import com.tali.notes.data.Viewport
import com.tali.notes.geometry.Affine
import com.tali.notes.geometry.Geo
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import com.tali.notes.geometry.ShapeRecognizer
import com.tali.notes.model.ArcShape
import com.tali.notes.model.AudioItem
import com.tali.notes.model.CustomShape
import com.tali.notes.talisApp
import com.tali.notes.data.NotesRepository
import com.tali.notes.model.ShapeGeometry
import com.tali.notes.ink.Brushes
import com.tali.notes.ink.StrokeFactory
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.EraserMode
import com.tali.notes.model.ImageItem
import com.tali.notes.model.InkItem
import com.tali.notes.model.LineShape
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeItem
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.SectorShape
import com.tali.notes.model.Tool
import com.tali.notes.model.TextItem
import com.tali.notes.model.TriangleShape
import kotlin.math.hypot
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.abs
import kotlin.math.PI
import com.tali.notes.geometry.Lasso
import com.tali.notes.geometry.Snap
import com.tali.notes.geometry.SnapFeatures
import com.tali.notes.geometry.SnapGuide
import com.tali.notes.geometry.SnapSession
import com.tali.notes.model.snapFeatures

/** What the canvas tells the screen around it. */
interface CanvasHost {
  fun onSelectionChanged(selection: SelectionInfo?)

  fun onZoomChanged(zoom: Float)

  /** A brief message, such as "Converted to shape". */
  fun onHint(message: String) = Unit

  /** A one-time tip, shown only the first time [id] comes up. */
  fun onTip(id: String, message: String) = Unit

  /** Which page is on screen ([index] from 0): an A4 page, or an imported PDF page; -1 when there are none. */
  fun onPageChanged(index: Int, count: Int) = Unit

  /** A paged notebook gained or lost pages (scrolling past the end, writing low, a PDF, the Pages menu). */
  fun onPagesChanged(count: Int) = Unit

  /** Typing starts in (or edits) a text block; null when nothing is being typed. */
  fun onEditText(edit: TextEdit?) = Unit

  /** The view moved or zoomed (an open text editor follows it). */
  fun onCameraMoved() = Unit

  /**
   * Where bars float over the page, in window pixels. Ink's live layer draws above every view, so
   * a stroke passing under a toolbar showed on top of it until the pen lifted; it's masked there.
   */
  fun inkCovers(): List<android.graphics.RectF> = emptyList()

  /** The S Pen button was double-pressed (+1, next starred pen) or triple-pressed (-1, back). */
  fun onPenCycle(step: Int) = Unit

  /** The screenshot lasso took a picture of an area, saved as [file]. */
  fun onScreenshot(file: java.io.File) = Unit

  /** A hold on the page with something copied: offer to paste it there (null takes the offer away). */
  fun onPasteOffer(offer: PasteOffer?) = Unit

  /** Something was dragged in from another app and let go on the page. */
  fun onDropped(drop: NoteCanvasView.Dropped) = Unit

  /** A stroke was finished and is waiting for Ink's hand-off: undo can already take it. */
  fun onInkWaiting() = Unit
}

/** Paste offered at [screenX], [screenY] (pixels in the canvas), which is [at] in the world. */
data class PasteOffer(val screenX: Float, val screenY: Float, val at: Pt, val count: Int)

/** A text block being typed: [id] is null for a new one. Positions and sizes are in world units. */
data class TextEdit(val id: String?, val origin: Pt, val width: Float, val fontSize: Float, val color: Int, val text: String, val height: Float = 0f)

/** Selection summary in screen pixels, for placing the selection menu. */
data class SelectionInfo(
  val screenRect: RectF,
  val count: Int,
  val hasShape: Boolean,
  val hasImage: Boolean,
  val hasDrawing: Boolean = true,
  val rotatable: Boolean = false,
  val textId: String? = null,
  /** The colour of the first coloured item, for marking it in the style menu. */
  val color: Int? = null,
  /** Thickness and line style of the first stroke or shape, or null when there are none. */
  val lineSize: Float? = null,
  val line: LineStyle? = null,
  /** Everything picked up is locked (only possible when the lasso is set to pick up locked items). */
  val locked: Boolean = false,
)

/**
 * The drawing surface. Finished content is drawn from GPU tiles; the pen stroke in progress is drawn
 * by Ink's front-buffered view on top for minimal latency. All input handling lives here.
 */
@SuppressLint("ViewConstructor")
class NoteCanvasView(context: Context, private val host: CanvasHost) : FrameLayout(context), InProgressStrokesFinishedListener {
  private val density = resources.displayMetrics.density
  val camera = Camera(density)
  private val inProgress = InProgressStrokesView(context)
  private val images = context.talisApp.repository.images
  private val itemRenderer = ItemRenderer(Brushes.renderer(), images)

  /** More loaded strokes were built in the background: redraw the tiles that left them out. */
  private val onStrokesBuilt: () -> Unit = { if (tiles.refreshWaiting()) invalidate() }

  /** A picture finished loading: redraw the tiles it covers. */
  private val onImageLoaded: (String) -> Unit = { file ->
    doc?.all?.forEach { if (it is ImageItem && it.file == file) tiles.invalidate(it.bounds) }
    if (SharpPdf.sourceOf(file) != null) scheduleSharp()
    invalidate()
  }
  private val tiles = TileCache(itemRenderer)

  /** PDF pages drawn sharp from their PDF once the view settles zoomed in. */
  private val sharpPdf = SharpPdf(context.talisApp.repository.imagesDir) { r ->
    tiles.invalidate(r)
    invalidate()
  }
  private val sharpen = Runnable { refreshSharp() }

  init {
    itemRenderer.sharp = { sharpPdf.patches }
  }

  private fun scheduleSharp() {
    removeCallbacks(sharpen)
    postDelayed(sharpen, SHARP_WAIT_MS)
  }

  private fun refreshSharp() {
    val d = doc ?: return
    if (width == 0 || height == 0) return
    val visible = visibleWorld()
    val pages = d.intersecting(visible).filterIsInstance<ImageItem>().filter { SharpPdf.sourceOf(it.file) != null }
    if (pages.isEmpty()) {
      if (sharpPdf.patches.isNotEmpty()) sharpPdf.clear()
      return
    }
    sharpPdf.refresh(pages, visible, camera.scale, width * height) { item -> images.peek(item.file)?.let { it.width / item.rect.width } }
  }

  init {
    // On A4 pages, writing and shapes show only on their page (or the page column with Write
    // between pages); the parts past it are kept, just not drawn.
    itemRenderer.clipFor = { item -> pageCount?.let { n -> pageLayout.clipFor(item, n, settings.inkBetweenPages) } }
  }
  private val paper = PaperRenderer()
  private val predictor = MotionEventPredictor.newInstance(this)
  /** Keeps the predicted tail from overshooting sharp turns (it twitched fast writing): at most 2.5 mm ahead. */
  private val predictionGuard = com.tali.notes.ink.PredictionGuard(2.5f / 25.4f * resources.displayMetrics.xdpi)
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

  private var doc: Document? = null
  private val docListener: (Change) -> Unit = { onDocChanged(it); scheduleSharp() }

  var settings = AppSettings()
    set(value) {
      // The paper redraws when her line weight changes.
      if (value.paperLineWeight != field.paperLineWeight || value.paperWeights != field.paperWeights || value.paperLooks != field.paperLooks || value.customPapers != field.customPapers) invalidate()
      val reclip = value.inkBetweenPages != field.inkBetweenPages
      field = value
      // Writing past the pages shows or hides again with Write between pages.
      if (reclip) {
        tiles.invalidateAll()
        invalidate()
      }
    }
  var tool = Tool.PEN
    set(value) {
      if (field != value) {
        field = value
        clearSelection()
        invalidate()
      }
    }

  var shapeKind = ShapeKind.LINE

  /** How far down the toolbars reach (dp), so jumps to a page land just below them. */
  var topClearance = 124f

  /** When set, the shape tool draws this saved shape instead of [shapeKind]. */
  var customShape: CustomShape? = null

  /** When set (and no saved shape is), the shape tool draws this subject's shape ([com.tali.notes.model.ShapePacks]). */
  var packShape: com.tali.notes.model.PackShape? = null
  var paperStyle = PaperStyle.DOTS
    set(value) {
      field = value
      invalidate()
    }

  /** One of her own backgrounds, drawn instead of [paperStyle]. */
  var customPaper: com.tali.notes.model.CustomPaper? = null
    set(value) {
      if (field == value) return
      field = value
      paper.custom = value
      invalidate()
    }

  /** Number of A4 pages, or null for an endless canvas. */
  /** Portrait or landscape A4 pages, as chosen when the notebook was made. */
  var pageLayout: Pages = Pages.PORTRAIT
    set(value) {
      if (field == value) return
      field = value
      refreshPages()
      clampCamera()
      invalidate()
    }

  var pageCount: Int? = null
    set(value) {
      if (field == value) return
      field = value
      refreshPages()
      clampCamera()
      invalidate()
    }

  /** Dark paper: a near-black page, with ink shown in light counterpart colours. */
  var darkPaper = false
    set(value) {
      if (field == value) return
      field = value
      paper.dark = value
      itemRenderer.dark = value
      tiles.invalidateAll()
      invalidate()
    }

  /** The desk around the pages in a paged notebook. */
  var deskColor = 0xFFE9E4F2.toInt()
    set(value) {
      if (field == value) return
      field = value
      invalidate()
    }

  var accentColor = 0xFF7A4ED6.toInt()
    set(value) {
      if (field == value) return
      field = value
      selectionPaint.color = value
      lassoPaint.color = value
      lassoFill.color = (value and 0x00FFFFFF) or 0x1C000000
      invalidate()
    }

  /** Colour of the drag handles on shapes and selections (blue, to stand apart from the lilac lasso). */
  var handleColor = 0xFF4F7BE8.toInt()
    set(value) {
      if (field == value) return
      field = value
      handleStroke.color = value
      invalidate()
    }

  // ---- Gesture state ----

  private enum class Mode {
    NONE,
    INK,
    LIVE_SHAPE,
    ERASE,
    LASSO,
    MOVE_SELECTION,
    SCALE_SELECTION,
    ROTATE_SELECTION,
    HANDLE_DRAG,
    SHAPE_CREATE,

    /** The fill tool came down: on lifting (without dragging) it fills what's there. */
    FILL_TAP,
    FINGER_PENDING,
    PAN,
    PINCH,

    /** The pen came down off the pages of a paged notebook: nothing to draw there. */
    IGNORE,

    /** A highlighter drawing a straight line from where it landed. */
    STRAIGHT,

    /** The laser pointer's trail. */
    LASER,
  }

  private var mode = Mode.NONE
  private var drawPointer = -1
  private var drawIsFinger = false

  // Ink
  private var strokeId: InProgressStrokeId? = null
  private val strokePens = HashMap<InProgressStrokeId, PenType>()

  /** The pen colour each stroke really has (on dark paper the stroke is drawn live in its light version). */
  private val strokeColors = HashMap<InProgressStrokeId, Int>()
  private val strokePoints = ArrayList<Pt>()
  private var holdAnchor = Pt.ZERO
  private val holdRunnable = Runnable { onHold() }

  /** A pen or finger held still on the page with something copied offers Paste there ([offerPaste]). */
  private val pasteHold = Runnable { offerPaste() }
  private var pasteHoldAt = Pt(0f, 0f)
  private var liveShape: ShapeItem? = null

  /** The shape as it snapped, and where the pen was then: keeping on moving resizes it from there. */
  private var liveBase: ShapeItem? = null
  private var liveAnchor = Pt(0f, 0f)
  private var liveFollowing = false

  // Eraser
  private var erasePrev = Pt.ZERO
  private val erasedOriginals = LinkedHashMap<String, CanvasItem>()
  private val erasePieces = LinkedHashMap<String, InkItem>()
  /** Fills the precise eraser is rubbing out, by id: an editable copy of each picture (saved on lift). */
  private val fillEdits = LinkedHashMap<String, FillEdit>()

  private class FillEdit(val item: ImageItem, val bmp: Bitmap) {
    val canvas = Canvas(bmp)
  }

  private val fillRubber = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
  }
  /** For each piece the eraser has made, the item it came from (so a fill's outline can follow its pieces). */
  private val pieceRoot = HashMap<String, String>()
  private var eraserOverride = false

  // Lasso and selection
  private val lassoPoints = ArrayList<Pt>()
  private val lassoPath = Path()
  private var selection: List<CanvasItem> = emptyList()
  private var selectionBounds: Rect? = null
  private var dragStart = Pt.ZERO
  private var dragTransform = Affine.IDENTITY

  /** While rotating a selection: its centre, where the drag started, and the current angle. */
  private var rotatePivot = Pt.ZERO
  private var rotateAngle = 0f

  private val angleText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
  private val angleBubble = Paint(Paint.ANTI_ALIAS_FLAG)

  // Editable shape with handles
  private var activeShapeId: String? = null

  /** The selection is a shape that picked itself up when drawn: the pen writes inside it. */
  private var softSelection = false
  private var handleIndex = -1
  private var handleGrab = Pt(0f, 0f)
  private var liveEdit: ShapeItem? = null

  // Shape tool
  private var shapeStart = Pt.ZERO
  private var shapePreview: ShapeItem? = null

  // Fingers
  private var fingerStart = Pt.ZERO
  private var fingerDownTime = 0L
  private var lastFocusX = 0f
  private var lastFocusY = 0f
  private var lastSpan = 0f
  private var maxPointers = 0
  private var multiTapMoved = false

  /** Where a multi-finger touch started, to tell a tap (undo/redo) from a pan or pinch. */
  private var tapStartFocus = Pt.ZERO
  private var tapStartSpan = 0f


  // ---- Paints ----

  private val selectionPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = maxOf(2f, 1.5f * density)
      pathEffect = DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
      color = accentColor
    }
  private val lassoPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.5f * density
      pathEffect = DashPathEffect(floatArrayOf(5f * density, 5f * density), 0f)
      color = accentColor
    }
  private val lassoFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1C7A4ED6 }
  private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
  private val handleStroke =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 2f * density
      color = 0xFF4F7BE8.toInt()
    }
  private val eraserPaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      strokeWidth = 1.2f * density
      color = 0xAA5E4B8B.toInt()
    }
  private val eraserFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22D1AEFF }

  init {
    setWillNotDraw(false)
    addView(inProgress, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    inProgress.addFinishedStrokesListener(this)
    inProgress.textureBitmapStore = Brushes.textureStore
    inProgress.rendererFactory = { Brushes.renderer() }
    tiles.boundsOf = { id -> doc?.get(id)?.bounds }
    isFocusable = true
    // Ink sets up its front-buffered drawing surface on the first stroke unless asked earlier,
    // which made the first stroke on a page lag or only appear once the pen lifted.
    doOnAttach { inProgress.eagerInit() }
    // Pictures, PDFs and text dragged over from another app side by side.
    setOnDragListener { _, e ->
      when (e.action) {
        android.view.DragEvent.ACTION_DRAG_STARTED -> doc != null && droppable(e.clipDescription)
        android.view.DragEvent.ACTION_DROP -> {
          runCatching { acceptDrop(e) }.onFailure { com.tali.notes.CrashLog.recovered(context, it) }
          true
        }
        else -> true
      }
    }
  }

  /**
   * Builds a tiny stroke with each of her pens in the background, so the first real stroke doesn't
   * pay for loading Ink's native code and making the brush (a fresh notebook has no strokes to
   * have done it already).
   */
  private fun warmPens() {
    val pens = settings.pens
    Thread({ pens.forEach { runCatching { StrokeFactory.previewStroke(Brushes.brush(it.type, it.color, it.size, it.lineStyle), 40f, 20f) } } }, "warm-pens").apply {
      priority = Thread.MIN_PRIORITY
      start()
    }
  }

  // ---- Document wiring ----

  fun attach(document: Document, viewport: Viewport?) {
    doc?.listeners?.remove(docListener)
    doc = document
    document.listeners += docListener
    images.addListener(onImageLoaded)
    StrokeBuilds.addListener(onStrokesBuilt)
    refreshPages()
    tiles.invalidateAll()
    if (viewport != null) {
      camera.set(viewport)
      doOnLayout { clampCamera() }
    } else if (pageCount != null) {
      doOnLayout { showPageWidth(0) }
    } else {
      doOnLayout { document.contentBounds()?.let { camera.centerOn(Pt(it.center.x, it.top + height / 2f / camera.scale - 40f), width, height) } }
    }
    host.onZoomChanged(camera.zoom)
    invalidate()
    post { warmPens() }
    doOnLayout { scheduleSharp() }
  }

  fun detach() {
    doc?.listeners?.remove(docListener)
    images.removeListener(onImageLoaded)
    StrokeBuilds.removeListener(onStrokesBuilt)
    removeCallbacks(sharpen)
    sharpPdf.release()
    doc = null
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    post {
      clampCamera()
      updatePage()
      scheduleSharp()
      invalidate()
    }
  }

  override fun onDetachedFromWindow() {
    if (PenEvents.listener === penListener) PenEvents.listener = null
    super.onDetachedFromWindow()
    cameraAnimator?.cancel()
    stopFling()
    removeCallbacks(holdRunnable)
    detach()
  }

  /**
   * Tiles leave out strokes that aren't built yet (so a big notebook opens fast) and wait for the
   * background warm-up, which only runs when the notebook opens. Anything new that isn't built (a
   * recoloured shape, an undo that brings back an old stroke) is built now: right here for a few,
   * in the background for a big batch, so nothing ever goes missing.
   */
  private fun buildNew(added: List<CanvasItem>) {
    val unbuilt = added.filter { (it is ShapeItem && !it.isBuilt) || (it is InkItem && !it.isBuilt) }
    if (unbuilt.isEmpty()) return
    fun build(item: CanvasItem) = runCatching {
      when (item) {
        is ShapeItem -> item.strokes
        is InkItem -> item.stroke
        else -> Unit
      }
    }
    if (unbuilt.size <= 24) {
      unbuilt.forEach(::build)
    } else {
      fillWorker.execute {
        unbuilt.forEach(::build)
        StrokeBuilds.progress()
      }
    }
  }

  private fun onDocChanged(change: Change) {
    // Voice memos aren't drawn or touched by the canvas.
    val c = if (change.removed.none { it is AudioItem } && change.added.none { it is AudioItem }) change else Change(change.removed.filterNot { it is AudioItem }, change.added.filterNot { it is AudioItem })
    buildNew(c.added)
    if (pageCount == null && (c.added + c.removed).any { it is ImageItem && it.pdfPage }) refreshPages()
    c.removed.forEach { tiles.invalidate(it.bounds) }
    c.added.forEach { tiles.invalidate(it.bounds) }
    // Writing (or an undo) that reaches below the last page brings the pages it needs.
    val n = pageCount
    if (n != null && c.added.isNotEmpty()) {
      val need = pageLayout.needed(c.added.map { it.bounds }.reduce(Rect::union))
      if (need > n) setPages(need)
    }
    // Keep the selection pointing at live items (undo can remove them).
    val d = doc
    if (d != null && selection.isNotEmpty()) {
      val live = selection.mapNotNull { d[it.id] }
      if (live.size != selection.size) setSelection(live)
    }
    if (activeShapeId != null && d?.get(activeShapeId!!) == null) activeShapeId = null
    invalidate()
  }

  override fun onStrokesFinished(finished: Map<InProgressStrokeId, Stroke>) {
    // Parts of a stroke that went off its page: drawn here until the whole stroke is kept.
    val parts = finished.filterKeys { it in splitParts }
    if (parts.isNotEmpty()) {
      parts.forEach { (id, s) ->
        val g = splitParts.remove(id)!!
        val pen = strokePens.remove(id)
        if (pen != null && !g.dropped) g.handed += InkItem("part-$id", 0L, pen, realColoured(id, pen, s))
        strokeColors.remove(id)
        g.parts.remove(id)
        if (g.ended && g.parts.isEmpty()) keepWhole(g)
      }
      inProgress.removeFinishedStrokes(parts.keys)
      parts.keys.forEach { shadows.remove(it) }
      invalidate()
    }
    // Only strokes not handed over yet (Ink may report a stroke already taken).
    val strokes = finished.filterKeys { strokePens.containsKey(it) && it !in parts }
    if (strokes.isEmpty()) {
      inProgress.removeFinishedStrokes(finished.keys)
      finished.keys.forEach { shadows.remove(it) }
      return
    }
    val d = doc
    if (d != null) {
      try {
        val added =
          strokes.flatMap { (id, s) ->
            val pen = strokePens.remove(id)!!
            val stroke = realColoured(id, pen, s)
            strokeColors.remove(id)
            // Kept whole: on A4 pages the part past its page is only hidden (ItemRenderer.clipFor).
            listOf(InkItem(d.newId(), d.nextZ(), pen, stroke))
          }
        // Each stroke its own undo step, even when Ink hands several over at once.
        added.forEach { d.commit(Change(emptyList(), listOf(it))) }
      } catch (t: Throwable) {
        com.tali.notes.CrashLog.recovered(context, t)
      }
    }
    // Hand-off in the same frame the tiles pick the strokes up, so there's no flicker (and our own
    // copies of them, if the page moved meanwhile, go in that same frame). Every stroke Ink handed
    // over goes, not only the ones kept now: one already taken (undo, the eraser or the lasso right
    // after writing, see commitWaitingInk) and handed over together with a newer one stayed on Ink's
    // layer for good, a stroke that couldn't be erased or picked up until the notebook was reopened.
    inProgress.removeFinishedStrokes(finished.keys)
    finished.keys.forEach { shadows.remove(it) }
    invalidate()
  }

  // ---- Strokes that leave their page ----
  //
  // Ink hides ink with its mask by drawing the stroke and then clearing the masked area, on a
  // front buffer the screen shows as it's drawn. On some phones that showed: a stroke run off its
  // page (into the next page, or under a toolbar) appeared there and flickered (a tester's video,
  // 15 Oct 2026). So Ink is never given what's off the stroke's page: each stretch on the page is
  // shown as an Ink stroke of its own (a "part"), every sample of the whole stroke is recorded
  // ([Gesture.full]), and once she lifts and Ink has handed every part over, the stroke is kept
  // whole from those samples, exactly as an unbroken one would be, still clipped to its page.

  /** The stroke being written: its look, where its ink may show, and every sample of it. */
  private class Gesture(
    val pen: PenType,
    val color: Int,
    val brush: androidx.ink.brush.Brush,
    val unitCm: Float,
    /** Where its ink may show, in world units (its page, inset by half the pen), or null for anywhere. */
    val areas: List<Rect>?,
    /** Bars over the page, in this view's pixels, widened by half the pen. */
    val covers: List<android.graphics.RectF>,
    val full: InkShadow,
  ) {
    /** Ink strokes it was shown as that Ink hasn't handed over yet. */
    val parts = LinkedHashSet<InProgressStrokeId>()
    /** It left its area at least once: kept from [full] instead of from Ink's stroke. */
    var split = false
    var ended = false
    var dropped = false
    /** Parts Ink already handed over, drawn here until the whole stroke is kept. */
    val handed = ArrayList<InkItem>()
    /** The last sample shown, where a part ends when the pen leaves. */
    val lastCoords = MotionEvent.PointerCoords()
    var lastTime = 0L
  }

  private var gesture: Gesture? = null

  /** Each part of a split stroke, to its stroke. */
  private val splitParts = HashMap<InProgressStrokeId, Gesture>()

  /** Split strokes whose handed-over parts are drawn here (see [Gesture.handed]). */
  private val splitShown = ArrayList<Gesture>()

  private val partProps = arrayOf(MotionEvent.PointerProperties())
  private val partCoords = arrayOf(MotionEvent.PointerCoords())

  /** Samples [from]..[to] of the one-pointer event [se] as an event with [action]. Recycle it. */
  private fun samples(se: MotionEvent, from: Int, to: Int, action: Int): MotionEvent {
    se.getPointerProperties(0, partProps[0])
    var out: MotionEvent? = null
    for (h in from..to) {
      val latest = h >= se.historySize
      if (latest) se.getPointerCoords(0, partCoords[0]) else se.getHistoricalPointerCoords(0, h, partCoords[0])
      val time = if (latest) se.eventTime else se.getHistoricalEventTime(h)
      val o = out
      if (o == null) out = MotionEvent.obtain(se.downTime, time, action, 1, partProps, partCoords, se.metaState, se.buttonState, se.xPrecision, se.yPrecision, se.deviceId, 0, se.source, se.flags)
      else o.addBatch(time, partCoords, se.metaState)
    }
    return out!!
  }

  private fun sampleX(e: MotionEvent, h: Int) = if (h < e.historySize) e.getHistoricalX(0, h) else e.x

  private fun sampleY(e: MotionEvent, h: Int) = if (h < e.historySize) e.getHistoricalY(0, h) else e.y

  /** Whether ink at ([x], [y]) on screen shows: on its area and not under a bar. */
  private fun shows(g: Gesture, x: Float, y: Float): Boolean {
    if (g.covers.any { it.contains(x, y) }) return false
    val areas = g.areas ?: return true
    val w = camera.toWorld(x, y)
    return areas.any { it.contains(w) }
  }

  /**
   * Gives Ink the samples of [se] (one pointer, smoothed) that show, starting a part where the pen
   * comes onto its area and ending one where it leaves; every sample goes into [Gesture.full].
   * [last] ends the stroke.
   */
  private fun feedInk(g: Gesture, se: MotionEvent, prediction: MotionEvent?, last: Boolean) {
    val n = se.historySize
    for (h in 0..n) g.full.add(se, 0, h)
    // A guess at where the pen goes next, only while it stays where ink shows.
    val guess = prediction?.takeIf { pr -> !last && (0..pr.historySize).all { k -> shows(g, sampleX(pr, k), sampleY(pr, k)) } }
    var h = 0
    while (h <= n) {
      val on = shows(g, sampleX(se, h), sampleY(se, h))
      // The run of samples with the same answer.
      var end = h
      while (end + 1 <= n && shows(g, sampleX(se, end + 1), sampleY(se, end + 1)) == on) end++
      if (on) {
        val sid = strokeId
        if (sid == null) {
          // On its area (at the start, or back on it): a new part.
          val ev = samples(se, h, end, MotionEvent.ACTION_DOWN)
          val id = inProgress.startStroke(ev, se.getPointerId(0), g.brush, camera.screenToWorld)
          shadows[id] = InkShadow(g.pen, g.brush, ev.eventTime, g.unitCm, android.graphics.Matrix(camera.screenToWorld)).also { sh -> for (k in 0..ev.historySize) sh.add(ev, 0, k) }
          ev.recycle()
          strokeId = id
          strokePens[id] = g.pen
          if (darkPaper) strokeColors[id] = g.color
          g.parts += id
          if (g.split) splitParts[id] = g
        } else {
          val ev = samples(se, h, end, MotionEvent.ACTION_MOVE)
          inProgress.addToStroke(ev, se.getPointerId(0), sid, if (end == n) guess else null)
          shadows[sid]?.let { sh -> for (k in 0..ev.historySize) sh.add(ev, 0, k) }
          ev.recycle()
        }
        if (end < n) se.getHistoricalPointerCoords(0, end, g.lastCoords) else se.getPointerCoords(0, g.lastCoords)
        g.lastTime = if (end < n) se.getHistoricalEventTime(end) else se.eventTime
      } else {
        if (!g.split) {
          g.split = true
          g.parts.forEach { splitParts[it] = g }
        }
        endPart(g, se)
      }
      h = end + 1
    }
    if (last) {
      endPart(g, se)
      g.ended = true
      if (g.split && g.parts.isEmpty()) keepWhole(g)
    }
    if (g.split && !g.dropped && (!g.ended || g.parts.isNotEmpty()) && g !in splitShown) splitShown += g
  }

  /** Ends the part being shown (if any) at the last sample shown. */
  private fun endPart(g: Gesture, se: MotionEvent) {
    val sid = strokeId ?: return
    se.getPointerProperties(0, partProps[0])
    partCoords[0].copyFrom(g.lastCoords)
    val up = MotionEvent.obtain(se.downTime, g.lastTime, MotionEvent.ACTION_UP, 1, partProps, partCoords, se.metaState, se.buttonState, se.xPrecision, se.yPrecision, se.deviceId, 0, se.source, se.flags)
    inProgress.finishStroke(up, se.getPointerId(0), sid)
    shadows[sid]?.finished = true
    up.recycle()
    strokeId = null
  }

  /** Every part handed over: the stroke kept whole from all its samples, in the frame its parts go. */
  private fun keepWhole(g: Gesture) {
    splitShown.remove(g)
    val d = doc
    if (!g.dropped && d != null) {
      try {
        val built = androidx.ink.strokes.Stroke(g.brush, g.full.inputs)
        val stroke = if (darkPaper) built.copy(built.brush.copyWithColorIntArgb(Brushes.inkColor(g.pen, g.color))) else built
        d.commit(Change(emptyList(), listOf(InkItem(d.newId(), d.nextZ(), g.pen, stroke))))
      } catch (t: Throwable) {
        com.tali.notes.CrashLog.recovered(context, t)
      }
    }
    g.handed.clear()
    invalidate()
  }

  /**
   * The stroke being written goes without being kept (a tap that opened something, hold-to-shape,
   * paste, a second finger): its part on screen is cancelled, and parts already finished are
   * dropped as Ink hands them over.
   */
  private fun dropStroke(e: MotionEvent?) {
    strokeId?.let {
      inProgress.cancelStroke(it, e)
      strokePens.remove(it)
      strokeColors.remove(it)
      splitParts.remove(it)
      gesture?.parts?.remove(it)
    }
    strokeId = null
    gesture?.let { g ->
      g.dropped = true
      g.handed.clear()
      splitShown.remove(g)
      // Parts Ink hasn't handed over yet are dropped when it does (onStrokesFinished).
      g.parts.forEach { splitParts[it] = g }
    }
    gesture = null
    inkLayerIdle()
  }

  /**
   * Strokes she finished that Ink hasn't handed over yet (it does at most every half second) go
   * into the notebook now, built from their recorded inputs, before anything that acts on what's
   * there: undo, redo, the eraser, the lasso. Two quick strokes then two quick undos used to undo
   * the first stroke and leave the second (a tester's video), since the second wasn't in yet.
   * Ink's layer is cleared, and its hand-off later finds them already taken.
   */
  private fun commitWaitingInk() {
    if (mode == Mode.INK || strokeId != null) return
    val d = doc ?: return
    var any = false
    // Strokes that left their page, ended, with parts still on Ink's layer: kept whole now.
    splitShown.filter { it.ended && !it.dropped && it.parts.isNotEmpty() }.forEach { g ->
      keepWhole(g)
      g.dropped = true
      // Its parts aren't drawn here again if the page moves before Ink hands them over.
      g.parts.forEach { shadows.remove(it) }
      any = true
    }
    val waiting = shadows.entries.filter { (id, sh) -> sh.finished && strokePens.containsKey(id) && id !in splitParts }
    for ((id, sh) in waiting) {
      val pen = strokePens[id] ?: continue
      val stroke = runCatching { androidx.ink.strokes.Stroke(sh.brush, sh.inputs) }.getOrNull() ?: continue
      try {
        d.commit(Change(emptyList(), listOf(InkItem(d.newId(), d.nextZ(), pen, realColoured(id, pen, stroke)))))
      } catch (t: Throwable) {
        com.tali.notes.CrashLog.recovered(context, t)
        continue
      }
      strokePens.remove(id)
      strokeColors.remove(id)
      shadows.remove(id)
      any = true
    }
    if (any) {
      hideInkLayer()
      invalidate()
    }
  }

  /** On dark paper the live stroke used the light version of the colour: the stroke in its real one. */
  private fun realColoured(id: InProgressStrokeId, pen: PenType, s: Stroke): Stroke {
    val real = strokeColors[id]?.let { Brushes.inkColor(pen, it) }
    return if (real != null && real != s.brush.colorIntArgb) s.copy(s.brush.copyWithColorIntArgb(real)) else s
  }

  /**
   * A stroke on Ink's layer (being written, or finished and waiting for Ink's hand-off), with its
   * inputs recorded exactly as Ink takes them (see Ink's StrokeInputPool), so it can be drawn on
   * the page here, the same, when the page moves before Ink hands it over ([shadowWaitingInk]).
   */
  private class InkShadow(val pen: PenType, val brush: androidx.ink.brush.Brush, val start: Long, val unitCm: Float, val toWorld: android.graphics.Matrix) {
    val inputs = androidx.ink.strokes.MutableStrokeInputBatch()
    var finished = false

    /** Drawn on the page by us instead of by Ink's layer (the page moved before the hand-off). */
    var item: InkItem? = null
    private val pt = FloatArray(2)

    /** Sample [h] of [e] ([MotionEvent.getHistorySize] for the latest one). */
    fun add(e: MotionEvent, idx: Int, h: Int) {
      if (idx < 0) return
      val latest = h >= e.historySize
      fun axis(a: Int) = if (latest) e.getAxisValue(a, idx) else e.getHistoricalAxisValue(a, idx, h)
      pt[0] = axis(MotionEvent.AXIS_X)
      pt[1] = axis(MotionEvent.AXIS_Y)
      toWorld.mapPoints(pt)
      val time = (if (latest) e.eventTime else e.getHistoricalEventTime(h)) - start
      val type = e.getToolType(idx)
      val stylus = type == MotionEvent.TOOL_TYPE_STYLUS
      val tool = when (type) {
        MotionEvent.TOOL_TYPE_MOUSE -> androidx.ink.brush.InputToolType.MOUSE
        MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER -> androidx.ink.brush.InputToolType.STYLUS
        else -> androidx.ink.brush.InputToolType.TOUCH
      }
      // Ink drops samples it finds invalid (repeated, or out of order) the same way.
      runCatching {
        inputs.add(
          tool,
          pt[0],
          pt[1],
          time,
          unitCm,
          if (stylus) axis(MotionEvent.AXIS_PRESSURE).coerceIn(0f, 1f) else androidx.ink.strokes.StrokeInput.NO_PRESSURE,
          if (stylus) axis(MotionEvent.AXIS_TILT).coerceIn(0f, (PI / 2).toFloat()) else androidx.ink.strokes.StrokeInput.NO_TILT,
          if (stylus) (axis(MotionEvent.AXIS_ORIENTATION) + 2.5f * PI.toFloat()).mod(2f * PI.toFloat()) else androidx.ink.strokes.StrokeInput.NO_ORIENTATION,
        )
      }
    }
  }

  private val shadows = HashMap<InProgressStrokeId, InkShadow>()

  private val smoother = com.tali.notes.ink.PenSmoother()

  /**
   * Finished strokes Ink hasn't handed over yet are drawn by its own layer, fixed to the screen,
   * and Ink hands strokes over at most every half second. If the page moved meanwhile (a quick
   * scroll or zoom right after writing), the last strokes slid along with the screen and then
   * jumped into place.
   *
   * Tried before: taking them early (`getFinishedStrokes` only holds strokes already handed over;
   * `flush()` crashed Ink's drawing layer on her tablet), and moving Ink's layer with the page
   * (it held for a plain scroll, not on her tablet in use). Now, as the page starts to move, Ink's
   * layer is masked out (its waiting strokes are cleared from it) and those strokes are drawn on
   * the page here from the same inputs, until Ink hands them over.
   */
  private fun shadowWaitingInk() {
    if (mode == Mode.INK) return
    val waiting = shadows.entries.filter { (_, s) -> s.finished && s.item == null }
    if (waiting.isEmpty()) return
    // All or nothing: a stroke that can't be drawn here is left to Ink's layer.
    val items = waiting.map { (id, s) ->
      val stroke = runCatching { androidx.ink.strokes.Stroke(s.brush, s.inputs) }.getOrNull() ?: return
      InkItem("waiting-ink-$id", 0L, s.pen, realColoured(id, s.pen, stroke))
    }
    waiting.forEachIndexed { i, (_, s) -> s.item = items[i] }
    hideInkLayer()
    invalidate()
  }

  /**
   * Clears Ink's layer: a mask over all of it, which Ink applies the next time it draws (it
   * clears the masked area on every draw); a stroke started and cancelled at once, off screen
   * and invisible, makes it draw now. The next stroke sets its usual mask again ([startInk]).
   */
  private fun hideInkLayer() {
    inProgress.maskPath = android.graphics.Path().apply { addRect(-1e5f, -1e5f, 1e5f, 1e5f, android.graphics.Path.Direction.CW) }
    runCatching {
      val id = inProgress.startStroke(androidx.ink.strokes.StrokeInput.create(-2000f, -2000f, 0L, androidx.ink.brush.InputToolType.STYLUS), clearBrush)
      inProgress.cancelStroke(id, null)
    }
  }

  private val clearBrush by lazy { Brushes.brush(PenType.FINELINER, 0x00000000, 1f, com.tali.notes.model.LineStyle.SOLID, false) }

  /** Strokes that went away without being kept (cancelled) are forgotten here too. */
  private fun inkLayerIdle() {
    shadows.keys.retainAll(strokePens.keys)
  }

  fun viewport(): Viewport = camera.viewport()

  // ---- Drawing ----

  private val guidePaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      color = 0x669B7BE0
      pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }

  /** Faint dashed A4 page edges on the fixed page grid, so she knows where exported pages break. */
  private fun drawPageGuides(canvas: Canvas) {
    if (!settings.pageGuides) return
    val w = com.tali.notes.export.Exporter.A4_WIDTH
    val h = com.tali.notes.export.Exporter.A4_HEIGHT
    val view = camera.visibleWorld(width, height)
    guidePaint.strokeWidth = 1.2f * density
    var x = kotlin.math.floor(view.left / w) * w
    while (x <= view.right) {
      val sx = (x - camera.x) * camera.scale
      canvas.drawLine(sx, 0f, sx, height.toFloat(), guidePaint)
      x += w
    }
    var y = kotlin.math.floor(view.top / h) * h
    while (y <= view.bottom) {
      val sy = (y - camera.y) * camera.scale
      canvas.drawLine(0f, sy, width.toFloat(), sy, guidePaint)
      y += h
    }
  }

  private val pageShadow = Paint(Paint.ANTI_ALIAS_FLAG)
  private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
  private val ghostText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

  /** Paged notebooks: the desk, then each visible page with its paper, then the page to pull in. */
  private fun drawPages(canvas: Canvas, n: Int) {
    canvas.drawColor(deskColor)
    val view = camera.visibleWorld(width, height)
    val first = pageLayout.indexAt(view.top).coerceAtLeast(0)
    val last = pageLayout.indexAt(view.bottom).coerceAtMost(n - 1)
    pageShadow.color = deskColor
    pageShadow.setShadowLayer(6f * density, 0f, 2f * density, if (darkPaper || deskIsDark()) 0x66000000 else 0x2E3A2A70)
    for (i in first..last) {
      val r = pageLayout.rect(i)
      val a = camera.toScreen(Pt(r.left, r.top))
      val b = camera.toScreen(Pt(r.right, r.bottom))
      canvas.drawRect(a.x, a.y, b.x, b.y, pageShadow)
      canvas.save()
      canvas.clipRect(a.x, a.y, b.x, b.y)
      paper.originY = r.top
      paper.pageRect = r
      paper.draw(canvas, paperStyle, camera, width, height)
      canvas.restore()
      // "2/5" in the page's bottom corner, when the page is big enough to read it.
      if (b.y - a.y > 240f * density) {
        ghostText.textAlign = Paint.Align.RIGHT
        ghostText.textSize = 11f * density
        ghostText.color = if (darkPaper) 0x88FFFFFF.toInt() else 0x66000000
        canvas.drawText("${i + 1}/$n", b.x - 10f * density, b.y - 9f * density, ghostText)
      }
    }
    paper.originY = 0f
    paper.pageRect = null
    drawPullIndicator(canvas, n)
  }

  /**
   * Pulled up past the last page: a small round sign in the space below it. An arrow and "Drag for
   * a new page" at first; a plus and "Release to add a page" once letting go would add one.
   */
  private fun drawPullIndicator(canvas: Canvas, n: Int) {
    // Only while her fingers are pulling, and only past the little room that's always left under
    // the last page: resting at the bottom (or zoomed out with space to spare) shows nothing.
    if (!pulling) return
    val pull = effectivePull()
    if (pull < REST_DP + 6f) return
    val lastBottom = camera.toScreen(Pt(0f, lastPageBottom(n))).y
    val ready = pull >= ADD_PULL_DP
    val alpha = ((pull - REST_DP - 6f) / 40f).coerceIn(0f, 1f)
    val tone = if (darkPaper || deskIsDark()) 0xFFC9C3D6.toInt() else 0xFF77718A.toInt()
    val color = (tone and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
    val cx = width / 2f
    val cy = (lastBottom + height) / 2f - 10f * density
    val r = 13f * density
    ghostPaint.pathEffect = null
    ghostPaint.color = color
    ghostPaint.strokeWidth = 1.6f * density
    ghostPaint.strokeCap = Paint.Cap.ROUND
    canvas.drawCircle(cx, cy, r, ghostPaint)
    val s = 6f * density
    if (ready) {
      canvas.drawLine(cx - s, cy, cx + s, cy, ghostPaint)
      canvas.drawLine(cx, cy - s, cx, cy + s, ghostPaint)
    } else {
      canvas.drawLine(cx, cy + s, cx, cy - s, ghostPaint)
      canvas.drawLine(cx, cy - s, cx - s * 0.7f, cy - s * 0.3f, ghostPaint)
      canvas.drawLine(cx, cy - s, cx + s * 0.7f, cy - s * 0.3f, ghostPaint)
    }
    ghostText.textAlign = Paint.Align.CENTER
    ghostText.textSize = 12f * density
    ghostText.color = color
    canvas.drawText(if (ready) tr(R.string.release_to_add_a_page) else tr(R.string.drag_for_a_new_page), cx, cy + r + 18f * density, ghostText)
  }

  private fun deskIsDark() = android.graphics.Color.luminance(deskColor) < 0.3f

  override fun onDraw(canvas: Canvas) {
    // A picture background loads in the background like any picture; the listener redraws.
    paper.customImage = customPaper?.image?.let { file -> images.peek(file) ?: null.also { images.request(file) } }
    val n = pageCount
    if (n != null) {
      drawPages(canvas, n)
    } else {
      paper.draw(canvas, paperStyle, camera, width, height)
      drawPageGuides(canvas)
    }
    val d = doc ?: return
    val visible = camera.visibleWorld(width, height)

    canvas.save()
    canvas.concat(camera.worldToScreen)
    try {
      tiles.draw(canvas, visible, camera.scale, d)
    } catch (t: Throwable) {
      com.tali.notes.CrashLog.recovered(context, t)
      tiles.invalidateAll()
    }
    erasePieces.values.forEach { itemRenderer.draw(canvas, it, camera.worldToScreen) }
    // A thickness being slid in the selection menu, shown live (rebuilt at most once a frame).
    pendingThickness?.let { size ->
      pendingThickness = null
      val dd = doc
      if (dd != null) restylePreview = selection.mapNotNull { dd[it.id] }.map { item ->
        when (item) {
          is InkItem -> item.restyled(size, item.line)
          is ShapeItem -> item.restyled(size, item.line)
          else -> item
        }
      }
    }
    restylePreview?.let { itemRenderer.drawAll(canvas, it, camera.worldToScreen) }
    // Parts of a stroke that left its page, already handed over by Ink, until it's kept whole.
    splitShown.forEach { g -> if (g.handed.isNotEmpty()) itemRenderer.drawAll(canvas, g.handed, camera.worldToScreen) }
    // Strokes still waiting for Ink's hand-off when the page moved, drawn here in Ink's place.
    if (shadows.isNotEmpty()) shadows.values.mapNotNull { it.item }.takeIf { it.isNotEmpty() }?.let { itemRenderer.drawAll(canvas, it, camera.worldToScreen) }
    if (mode == Mode.LIVE_SHAPE) updateLiveShape()
    liveShape?.let { drawShapePreview(canvas, it) }
    straightPreview?.let { preview ->
      canvas.save()
      // The same area the kept stroke is drawn in (Pages.clipFor); the canvas is in world units here.
      inkAreas(pageAt(straightStart))?.let { areas -> canvas.clipPath(areaPath(areas, null)) }
      itemRenderer.draw(canvas, preview, camera.worldToScreen)
      canvas.restore()
    }
    liveEdit?.let { drawShapePreview(canvas, it) }
    shapePreview?.let { drawShapePreview(canvas, it) }
    if (isDraggingSelection() && selection.isNotEmpty()) {
      canvas.save()
      canvas.concat(dragTransform.toMatrix())
      // Carried whole (not clipped to its page) while it's being moved.
      val clip = itemRenderer.clipFor
      itemRenderer.clipFor = null
      itemRenderer.drawAll(canvas, selection, camera.worldToScreen)
      itemRenderer.clipFor = clip
      canvas.restore()
    }
    canvas.restore()

    drawLasso(canvas)
    drawShotFlash(canvas)
    drawSelection(canvas)
    drawShapeHandles(canvas)
    drawSnapGuides(canvas)
    drawMeasure(canvas)
    drawSquareHint(canvas)
    drawEraserCursor(canvas)
    drawLaser(canvas)
    drawLockCues(canvas)
  }

  private val squareHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
  }

  /**
   * While a rectangle is drawn or stretched and it's exactly square, or an ellipse is exactly round,
   * a dotted diagonal shows across it (corner to corner, or a diameter at 45°), so she can see it
   * locked on.
   */
  private fun drawSquareHint(canvas: Canvas) {
    val g = when (mode) {
      Mode.HANDLE_DRAG -> liveEdit?.geometry
      Mode.SHAPE_CREATE -> shapePreview?.geometry
      Mode.LIVE_SHAPE -> liveShape?.geometry
      // Resizing it with the chip keeps it square (or round): still say so.
      Mode.SCALE_SELECTION -> ((selection.singleOrNull() as? ShapeItem)?.geometry)?.transformed(dragTransform)
      else -> null
    } ?: return
    val (a, b) = when {
      g is com.tali.notes.model.PolyShape && g.isSquare() -> g.points[0] to g.points[2]
      g is com.tali.notes.model.EllipseShape && g.round && g.rx > 4f -> g.point((0.75f * PI).toFloat()) to g.point((1.75f * PI).toFloat())
      else -> return
    }
    val sa = camera.toScreen(a)
    val sb = camera.toScreen(b)
    squareHintPaint.color = accentColor
    squareHintPaint.alpha = 200
    squareHintPaint.pathEffect = DashPathEffect(floatArrayOf(0.1f, 6f * density), 0f)
    // Round dots: tiny dashes with round caps.
    squareHintPaint.strokeWidth = 2.4f * density
    canvas.drawLine(sa.x, sa.y, sb.x, sb.y, squareHintPaint)
  }

  private fun drawLasso(canvas: Canvas) {
    if (mode != Mode.LASSO || lassoPoints.size < 2) return
    // The screenshot lasso shows the rectangle it will capture around what she draws.
    if (settings.lassoShot) Rect.around(lassoPoints)?.let { area ->
      val r = screenRect(area, Affine.IDENTITY)
      canvas.drawRect(r, lassoFill)
      canvas.drawRect(r, selectionPaint)
    }
    lassoPath.reset()
    lassoPoints.forEachIndexed { i, p ->
      val s = camera.toScreen(p)
      if (i == 0) lassoPath.moveTo(s.x, s.y) else lassoPath.lineTo(s.x, s.y)
    }
    // Free form is a dotted trail of where she traced (closing up only once it comes round);
    // the lasso is its closed, softly filled loop.
    if (settings.lassoFree && !loopCloses(lassoPoints)) {
      canvas.drawPath(lassoPath, lassoPaint)
      return
    }
    lassoPath.close()
    canvas.drawPath(lassoPath, lassoFill)
    canvas.drawPath(lassoPath, lassoPaint)
  }

  private fun isDraggingSelection() = mode == Mode.MOVE_SELECTION || mode == Mode.SCALE_SELECTION || mode == Mode.ROTATE_SELECTION

  /** Pictures can't be turned (they stay upright), so the rotate handle only shows for drawings. */
  // ---- Fill ----

  private var filling = false

  /**
   * Fills what's under [p] with the fill colour. A closed shape there gets a fill of its own (crisp,
   * moves with the shape); otherwise an area her writing closes in gets a fill picture behind the
   * writing, worked out in the background. With no fill colour, it takes a fill away instead.
   */
  private fun fillAt(p: Pt) {
    val d = doc ?: return
    val colour = settings.fillArgb
    // 1. The topmost closed shape the tap is inside. Nothing running through it: it gets a fill
    // of its own. A line (or anything) through it splits it, so the area round the tap is filled
    // instead (step 3), unless that turns out to be nearly the whole shape (a label inside it).
    val shape = d.all.toList().asReversed().firstOrNull { it is ShapeItem && !it.locked && it.closed && it.bounds.contains(p) && Geo.pointInPolygon(p, it.outline) } as ShapeItem?
    val split = shape != null && runsThrough(d, shape)
    if (shape != null && (!split || colour == null && shape.fill != null)) {
      if (shape.fill == colour) return
      d.commit(Change(listOf(shape), listOf(shape.filled(colour))))
      return
    }
    // 2. A fill picture already there: taken away, or replaced by the new colour below.
    val oldFills = d.all.filter { it is ImageItem && it.file.startsWith(FILL_PREFIX) && it.rect.contains(p) && fillCovers(it, p) }
    if (colour == null) {
      if (oldFills.isEmpty()) host.onHint(tr(R.string.tap_a_filled_shape_or_area_2))
      else d.commit(Change(oldFills, emptyList()))
      return
    }
    // 3. The area her writing encloses, found off the main thread.
    if (filling) return
    filling = true
    val n = pageCount
    val page = if (n != null) pageLayout.rect(pageLayout.indexAt(p.y).coerceIn(0, n - 1)) else null
    var window = Rect(p.x - FILL_REACH, p.y - FILL_REACH, p.x + FILL_REACH, p.y + FILL_REACH)
    if (page != null) window = Rect(maxOf(window.left, page.left), maxOf(window.top, page.top), minOf(window.right, page.right), minOf(window.bottom, page.bottom))
    // Shapes' own fills aren't walls: only their outlines close an area in.
    val walls = d.intersecting(window).filter { it !is ImageItem }.map { if (it is ShapeItem && it.fill != null) it.filled(null) else it }
    // A shape split by a line: filling nearly all of it (90%) still gives it a fill of its own.
    val whole = if (shape != null) abs(Geo.polygonArea(shape.outline)) * 0.9f else null
    fillWorker.execute {
      val made = runCatching { fillPicture(p, window, walls, colour, whole) }.getOrNull()
      post {
        filling = false
        val dd = doc ?: return@post
        if (dd !== d) return@post
        if (made == null) {
          host.onHint(if (walls.isEmpty()) tr(R.string.draw_around_an_area_first_or) else tr(R.string.that_area_isn_t_closed_close))
          return@post
        }
        val live = shape?.let { dd[it.id] as? ShapeItem }
        if (made === WHOLE_SHAPE) {
          if (live != null && live.fill != colour) dd.commit(Change(listOf(live), listOf(live.filled(colour))))
          return@post
        }
        val (file, rect, outline) = made
        val item = ImageItem(dd.newId(), dd.nextZ(), file, rect, walls = outline)
        // The shape's own fill would cover the part filled now (fill pictures sit behind shapes): it goes.
        val unfill = if (live?.fill != null) listOf(live to live.filled(null)) else emptyList()
        dd.commit(Change(oldFills.filter { dd[it.id] != null } + unfill.map { it.first }, listOf(item) + unfill.map { it.second }))
      }
    }
  }

  /**
   * Whether writing or another shape runs through the inside of [shape] (a line across a box),
   * so a tap there means the part it splits off, not the whole shape.
   */
  private fun runsThrough(d: Document, shape: ShapeItem): Boolean {
    val outline = shape.outline
    fun inside(q: Pt) = Geo.pointInPolygon(q, outline) && shape.bounds.contains(q)
    return d.intersecting(shape.bounds).any { item ->
      item.id != shape.id &&
        when (item) {
          is InkItem -> StrokeFactory.points(item.stroke).any(::inside)
          is ShapeItem -> item.geometry.paths(item.size).any { path -> path.any(::inside) }
          else -> false
        }
    }
  }

  /** Whether the fill picture [item] actually covers [p] (not just its box). */
  private fun fillCovers(item: CanvasItem, p: Pt, unloaded: Boolean = true): Boolean {
    val img = item as ImageItem
    val bmp = images.peek(img.file) ?: return unloaded
    var x = ((p.x - img.rect.left) / img.rect.width * bmp.width).toInt().coerceIn(0, bmp.width - 1)
    var y = ((p.y - img.rect.top) / img.rect.height * bmp.height).toInt().coerceIn(0, bmp.height - 1)
    if (img.flipX) x = bmp.width - 1 - x
    if (img.flipY) y = bmp.height - 1 - y
    return (bmp.getPixel(x, y) ushr 24) > 0
  }

  /**
   * Draws the writing in [window] into a mask, finds the area around [p] it encloses and returns a
   * picture of that area in [colour] (saved, with its place in the world), or null if it isn't closed.
   */
  private fun fillPicture(p: Pt, window: Rect, walls: List<CanvasItem>, colour: Int, whole: Float? = null): Triple<String, Rect, Set<String>>? {
    val k = minOf(1f, FILL_PIXELS / maxOf(window.width, window.height))
    val w = (window.width * k).toInt().coerceAtLeast(8)
    val h = (window.height * k).toInt().coerceAtLeast(8)
    val mask = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = Canvas(mask)
    c.scale(k, k)
    c.translate(-window.left, -window.top)
    val hint = android.graphics.Matrix().apply { setScale(k, k) }
    ItemRenderer(androidx.ink.rendering.android.canvas.CanvasStrokeRenderer.create(forcePathRendering = true, textureStore = Brushes.textureStore), images, sync = true).drawAll(c, walls, hint)
    val px = IntArray(w * h)
    mask.getPixels(px, 0, w, 0, 0, w, h)
    mask.recycle()
    val wallGrid = BooleanArray(w * h) { (px[it] ushr 24) > 40 }
    // Close gaps of about 1 mm in hand-drawn outlines.
    val bridge = (FILL_BRIDGE * k).toInt().coerceAtLeast(1)
    val filled = AreaFill.fill(wallGrid, w, h, ((p.x - window.left) * k).toInt(), ((p.y - window.top) * k).toInt(), bridge) ?: return null
    var x0 = w
    var y0 = h
    var x1 = -1
    var y1 = -1
    for (y in 0 until h) for (x in 0 until w) if (filled[y * w + x]) {
      if (x < x0) x0 = x
      if (x > x1) x1 = x
      if (y < y0) y0 = y
      if (y > y1) y1 = y
    }
    if (x1 < x0) return null
    // Nearly the whole shape it's in: that shape gets a fill of its own instead (see fillAt).
    if (whole != null && filled.count { it } / (k * k) >= whole) return WHOLE_SHAPE
    val bw = x1 - x0 + 1
    val bh = y1 - y0 + 1
    val out = IntArray(bw * bh)
    for (y in 0 until bh) for (x in 0 until bw) if (filled[(y + y0) * w + (x + x0)]) out[y * bw + x] = colour
    val bmp = android.graphics.Bitmap.createBitmap(out, bw, bh, android.graphics.Bitmap.Config.ARGB_8888)
    // The colour goes in the name, in case a fill ever needs to be recognised by colour.
    val file = images.save(bmp, FILL_PREFIX + "%08x-".format(colour))
    val outline = outlineOf(walls, filled, w, h, window, k)
    return Triple(file, Rect(window.left + x0 / k, window.top + y0 / k, window.left + (x1 + 1) / k, window.top + (y1 + 1) / k), outline)
  }

  /**
   * Which of [walls] outline the filled area: along much of its length (a quarter or more), there is
   * fill just to one side of it and not the other. Writing inside the area has fill on both sides,
   * and a stroke or shape that only touches or crosses the edge borders it just briefly; neither is
   * part of the outline, so neither moves with the fill.
   */
  private fun outlineOf(walls: List<CanvasItem>, filled: BooleanArray, w: Int, h: Int, window: Rect, k: Float): Set<String> {
    fun on(x: Float, y: Float): Boolean {
      val px = ((x - window.left) * k).toInt()
      val py = ((y - window.top) * k).toInt()
      return px in 0 until w && py in 0 until h && filled[py * w + px]
    }
    val step = 3f / k
    val out = HashSet<String>()
    for (item in walls) {
      val (paths, size) = when (item) {
        is InkItem -> listOf(StrokeFactory.points(item.stroke)) to item.size
        is ShapeItem -> item.geometry.paths(item.size) to item.size
        else -> continue
      }
      // Probe just past the line's own width and the gap bridging, on either side.
      val reach = size / 2f + FILL_BRIDGE + 2.5f / k
      var samples = 0
      var oneSided = 0
      for (path in paths) for (i in 0 until path.size - 1) {
        val a = path[i]
        val b = path[i + 1]
        val len = a.dist(b)
        if (len < 1e-3f) continue
        val n = Pt(-(b.y - a.y) / len, (b.x - a.x) / len)
        var t = 0f
        while (t < len) {
          val q = a + (b - a) * (t / len)
          val left = on(q.x + n.x * reach, q.y + n.y * reach)
          val right = on(q.x - n.x * reach, q.y - n.y * reach)
          samples++
          if (left != right) oneSided++
          t += step
        }
      }
      if (samples > 0 && oneSided >= 4 && oneSided >= samples / 4) out += item.id
    }
    return out
  }

  /** Whether a touch at [screen] lands on the active shape's dots or on the selection (to move, resize or turn it). */
  private fun grabsEdit(screen: Pt): Boolean {
    val hit = HANDLE_HIT_DP * density
    currentActiveShape()?.let { shape -> if (handleAt(shape, screen) >= 0) return true }
    val sb = selectionBounds ?: return false
    val r = screenRect(sb, Affine.IDENTITY).apply { inset(-8f * density, -8f * density) }
    if (canRotateSelection() && rotateHandle(r).dist(screen) < hit) return true
    return resizeChip(r).dist(screen) < hit || moveChip(r).dist(screen) < hit || r.contains(screen.x, screen.y)
  }

  private fun canRotateSelection() = selection.isNotEmpty() && selection.none { it is ImageItem || it is TextItem }

  /**
   * The rotate handle hangs below the middle of the selection (the menu sits above it), and lower
   * still when one of the shape's own dots sits under the middle (a lever's fulcrum, a neuron's
   * axon), so the two never sit on each other.
   */
  private fun rotateHandle(r: RectF): Pt {
    var y = r.bottom + ROTATE_DROP_DP * density
    val shape = currentActiveShape()
    if (shape != null && selection.size == 1 && shapeScreenSize(shape) >= TINY_SHAPE_DP * density) {
      shape.geometry.handles().map(camera::toScreen)
        .filter { abs(it.x - r.centerX()) < HANDLE_HIT_DP * 1.5f * density && it.y > r.bottom - HANDLE_HIT_DP * density }
        .maxOfOrNull { it.y }
        ?.let { y = max(y, it + ROTATE_DROP_DP * density) }
    }
    return Pt(r.centerX(), y)
  }

  private fun drawRotateHandle(canvas: Canvas, r: RectF) {
    if (!canRotateSelection() || mode == Mode.MOVE_SELECTION || mode == Mode.SCALE_SELECTION || mode == Mode.HANDLE_DRAG) return
    val h = rotateHandle(r)
    canvas.drawLine(h.x, r.bottom, h.x, h.y - 6.5f * density, selectionPaint)
    drawHandle(canvas, h.x, h.y, 6.5f)
    // A little turning arrow inside the handle.
    val a = 3.2f * density
    canvas.drawArc(h.x - a, h.y - a, h.x + a, h.y + a, -60f, 280f, false, handleStroke)
  }

  private fun drawSelection(canvas: Canvas) {
    val b = selectionBounds ?: return
    val pad = 8f * density
    if (activeShapeId != null && selection.size == 1 && mode != Mode.ROTATE_SELECTION && mode != Mode.SCALE_SELECTION) {
      // The shape's own dots move its corners; add the rotate handle and the resize chip.
      val r = screenRect(b, Affine.IDENTITY).apply { inset(-pad, -pad) }
      drawRotateHandle(canvas, r)
      if (mode != Mode.HANDLE_DRAG && mode != Mode.MOVE_SELECTION) {
        drawResizeChip(canvas, resizeChip(r))
        drawMoveChip(canvas, moveChip(r))
      }
      return
    }
    if (mode == Mode.ROTATE_SELECTION) {
      drawRotatingSelection(canvas, b, pad)
      return
    }
    val r = screenRect(b, dragTransform.takeIf { isDraggingSelection() } ?: Affine.IDENTITY)
    r.inset(-pad, -pad)
    // Snap to the pixel grid so all four dashed edges look equally crisp.
    r.set(Math.round(r.left) + 0.5f, Math.round(r.top) + 0.5f, Math.round(r.right) + 0.5f, Math.round(r.bottom) + 0.5f)
    canvas.drawRoundRect(r, 6f * density, 6f * density, selectionPaint)
    if (mode != Mode.MOVE_SELECTION) drawResizeChip(canvas, resizeChip(r))
    if (mode != Mode.MOVE_SELECTION && mode != Mode.SCALE_SELECTION) drawMoveChip(canvas, moveChip(r))
    drawRotateHandle(canvas, r)
  }

  /** The turning selection: its outline follows the rotation, with the angle shown above it. */
  private fun drawRotatingSelection(canvas: Canvas, b: Rect, pad: Float) {
    val padW = pad / camera.scale
    val corners = listOf(Pt(b.left - padW, b.top - padW), Pt(b.right + padW, b.top - padW), Pt(b.right + padW, b.bottom + padW), Pt(b.left - padW, b.bottom + padW)).map { camera.toScreen(dragTransform.apply(it)) }
    lassoPath.reset()
    corners.forEachIndexed { i, s -> if (i == 0) lassoPath.moveTo(s.x, s.y) else lassoPath.lineTo(s.x, s.y) }
    lassoPath.close()
    canvas.drawPath(lassoPath, selectionPaint)
    // Screen y points down, so flip the sign: counter-clockwise reads as positive, as in maths class.
    val degrees = Math.round(-Math.toDegrees(rotateAngle.toDouble())).toInt().let { ((it + 540) % 360) - 180 }
    val label = "$degrees\u00B0"
    val center = camera.toScreen(rotatePivot)
    angleText.textSize = 13f * density
    angleText.color = 0xFFFFFFFF.toInt()
    angleBubble.color = handleColor
    val w = angleText.measureText(label) + 16f * density
    val h = 24f * density
    canvas.drawRoundRect(center.x - w / 2, center.y - h / 2, center.x + w / 2, center.y + h / 2, h / 2, h / 2, angleBubble)
    canvas.drawText(label, center.x, center.y + 4.5f * density, angleText)
  }

  /**
   * Measurements for the line, arc or part circle being drawn or changed (or showing its dots): a
   * line's angle (counter-clockwise from the horizontal, as in class) and its length at print size;
   * how far round an arc goes; how much of a circle a part circle is. The badge stays clear of the
   * pen, where her hand would cover it: beside the middle of a line, or at the circle's centre.
   */
  private fun drawMeasure(canvas: Canvas) {
    if (isDraggingSelection()) return
    val g =
      when (mode) {
        Mode.LIVE_SHAPE -> liveShape?.geometry
        Mode.SHAPE_CREATE -> shapePreview?.geometry
        else -> currentActiveShape()?.geometry
      } ?: return
    when (g) {
      is LineShape -> {
        val v = g.b - g.a
        if (v.length() < 1f) return
        var degrees = Math.round(-Math.toDegrees(kotlin.math.atan2(v.y, v.x).toDouble())).toInt()
        if (degrees < 0) degrees += 180
        if (degrees == 180) degrees = 0
        // World units are dp; exported PDFs print them at 72/160 pt each.
        val cm = g.length * (72f / 160f) / 72f * 2.54f
        val label = "$degrees°  ·  ${String.format(java.util.Locale.US, "%.1f", cm)} cm"
        val startMoving = mode == Mode.HANDLE_DRAG && handleIndex == 0
        val moving = if (startMoving) g.a else g.b
        val fixed = if (startMoving) g.b else g.a
        val a = camera.toScreen(fixed)
        val m = camera.toScreen(moving)
        val dir = (m - a).normalized()
        val w = bubbleWidth(label)
        val idle = if (mode == Mode.NONE) selectionBounds?.let { screenRect(it, Affine.IDENTITY).apply { inset(-8f * density, -8f * density) } } else null
        if (idle != null) {
          // Just showing its dots: the menu is above and the chips and rotate handle below, so it
          // sits under all of them, in the middle.
          drawBubble(canvas, label, idle.centerX(), idle.bottom + 64f * density)
        } else if (a.dist(m) < w + 90f * density) {
          // Short: just past the end that isn't moving, on the far side from the pen.
          val reach = abs(dir.x) * w / 2 + abs(dir.y) * BUBBLE_H * density / 2 + 14f * density
          drawBubble(canvas, label, a.x - dir.x * reach, a.y - dir.y * reach)
        } else {
          // Beside the middle of the line, on its upper side.
          var n = Pt(-dir.y, dir.x)
          if (n.y > 0f || (n.y == 0f && n.x > 0f)) n = n * -1f
          val mid = Pt((a.x + m.x) / 2, (a.y + m.y) / 2)
          val off = abs(n.x) * w / 2 + abs(n.y) * BUBBLE_H * density / 2 + 10f * density
          drawBubble(canvas, label, mid.x + n.x * off, mid.y + n.y * off)
        }
      }
      is ArcShape -> if (!g.arrowEnd) drawBubble(canvas, "${g.degrees}°", camera.toScreen(g.center))
      is TriangleShape -> {
        // Each corner's angle, just inside the corner, while it's being drawn or reshaped.
        if (mode != Mode.HANDLE_DRAG && mode != Mode.SHAPE_CREATE && mode != Mode.LIVE_SHAPE) return
        val corners = g.points.map { camera.toScreen(it) }
        val mid = Pt(corners.sumOf { it.x.toDouble() }.toFloat() / 3f, corners.sumOf { it.y.toDouble() }.toFloat() / 3f)
        g.angles.forEachIndexed { i, a ->
          val v = mid - corners[i]
          val reach = min(v.length() * 0.55f, 46f * density)
          val at = corners[i] + v.normalized() * reach
          drawBubble(canvas, "${Math.round(a)}°", at)
        }
      }
      is SectorShape -> {
        val label = "${Math.round(g.fraction * 100)}%  ·  ${Math.round(g.fraction * 360)}°"
        val c = camera.toScreen(g.center)
        if (g.fraction > 0.6f) drawBubble(canvas, label, c)
        else {
          // The centre is a corner of the slice: sit just behind it, away from the curve.
          val away = Pt.polar(1f, g.start + g.sweep / 2) * -1f
          val off = abs(away.x) * bubbleWidth(label) / 2 + abs(away.y) * BUBBLE_H * density / 2 + 10f * density
          drawBubble(canvas, label, c.x + away.x * off, c.y + away.y * off)
        }
      }
      else -> Unit
    }
  }

  private fun bubbleWidth(label: String): Float {
    angleText.textSize = 12.5f * density
    return angleText.measureText(label) + 18f * density
  }

  private fun drawBubble(canvas: Canvas, label: String, at: Pt) = drawBubble(canvas, label, at.x, at.y)

  private fun drawBubble(canvas: Canvas, label: String, x: Float, y: Float) {
    val w = bubbleWidth(label)
    angleText.color = 0xFFFFFFFF.toInt()
    angleBubble.color = handleColor
    val h = BUBBLE_H * density
    val (cx, cy) = clearSpot(x, y, w, h)
    canvas.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, angleBubble)
    canvas.drawText(label, cx, cy + 4.5f * density, angleText)
  }

  /**
   * Where a [w]×[h] badge wanted at ([x], [y]) goes: there, unless it would cover a chip (resize,
   * move, rotate) or one of the shape's dots, then the nearest clear place around it, sideways
   * first (the chips hang below the selection and its menu sits above). Always on screen.
   */
  private fun clearSpot(x: Float, y: Float, w: Float, h: Float): Pt {
    fun onScreen(px: Float, py: Float) = Pt(px.coerceIn(w / 2 + 4f, width - w / 2 - 4f), py.coerceIn(h / 2 + 4f, height - h / 2 - 4f))
    val avoid = ArrayList<Pair<Pt, Float>>()
    var menu: RectF? = null
    selectionBounds?.let { b ->
      val r = screenRect(b, Affine.IDENTITY).apply { inset(-8f * density, -8f * density) }
      val chip = 17f * density
      avoid += resizeChip(r) to chip
      avoid += moveChip(r) to chip
      if (canRotateSelection()) avoid += rotateHandle(r) to chip
      // The selection menu (showing while nothing's being dragged), roughly: a bar centred above the selection.
      if (mode == Mode.NONE) menu = RectF(r.centerX() - 130f * density, r.top - 70f * density, r.centerX() + 130f * density, r.top - 2f * density)
    }
    currentActiveShape()?.geometry?.handles()?.forEach { avoid += camera.toScreen(it) to 10f * density }
    fun clear(p: Pt): Boolean {
      if (menu?.intersects(p.x - w / 2, p.y - h / 2, p.x + w / 2, p.y + h / 2) == true) return false
      return avoid.none { (c, rad) ->
        val nx = c.x.coerceIn(p.x - w / 2, p.x + w / 2)
        val ny = c.y.coerceIn(p.y - h / 2, p.y + h / 2)
        hypot(c.x - nx, c.y - ny) < rad + 3f * density
      }
    }
    val first = onScreen(x, y)
    if (avoid.isEmpty() || clear(first)) return first
    val dirs = listOf(-1f to 0f, 1f to 0f, -0.7f to 0.7f, 0.7f to 0.7f, -0.7f to -0.7f, 0.7f to -0.7f, 0f to 1f, 0f to -1f)
    for (step in 1..10) {
      val d = step * 12f * density
      for ((dx, dy) in dirs) {
        val p = onScreen(x + dx * d, y + dy * d)
        if (clear(p)) return p
      }
    }
    return first
  }

  private fun drawShapeHandles(canvas: Canvas) {
    if (isDraggingSelection()) return
    val shape = currentActiveShape() ?: return
    // Smaller dots on a small shape, so they don't cover it; none on a tiny one (it's moved and resized whole).
    val size = shapeScreenSize(shape)
    if (size < TINY_SHAPE_DP * density && liveEdit == null) return
    val r = (size * 0.12f / density).coerceIn(4.5f, 6.5f)
    shape.geometry.handles().forEach { p ->
      val s = camera.toScreen(p)
      drawHandle(canvas, s.x, s.y, r)
    }
  }

  /**
   * How big [shape] is on screen (pixels) for sizing its dots: the length of a line or arc, the
   * shorter side of anything else.
   */
  private fun shapeScreenSize(shape: ShapeItem): Float {
    val r = screenRect(shape.bounds, Affine.IDENTITY)
    val g = shape.geometry
    return if (g is LineShape || g is ArcShape || g is com.tali.notes.model.CurveShape) max(r.width(), r.height()) else min(r.width(), r.height())
  }

  /**
   * The dot of [shape] under [screen], or -1. The dots reach less far on a small shape (a share of
   * its size, down to [MIN_HANDLE_HIT_DP]), and a touch on the shape that's nearer its middle than
   * the nearest dot moves the shape instead, so a tiny box can still be picked up and carried.
   */
  private fun handleAt(shape: ShapeItem, screen: Pt): Int {
    val handles = shape.geometry.handles()
    if (handles.isEmpty() || shapeScreenSize(shape) < TINY_SHAPE_DP * density) return -1
    val dists = handles.map { camera.toScreen(it).dist(screen) }
    val i = dists.indices.minBy { dists[it] }
    val reach = (shapeScreenSize(shape) * 0.3f).coerceIn(MIN_HANDLE_HIT_DP * density, HANDLE_HIT_DP * density)
    if (dists[i] >= reach) return -1
    val area = screenRect(shape.bounds, Affine.IDENTITY).apply { inset(-8f * density, -8f * density) }
    if (area.contains(screen.x, screen.y) && camera.toScreen(shape.bounds.center).dist(screen) < dists[i]) return -1
    // A dot beside the rotate handle or a chip (a bar chart's corner, a Gantt row's end): the
    // nearer of the two takes the touch, so the chips stay easy to grab.
    if (shape.id == activeShapeId) {
      selectionBounds?.let { sb ->
        val r = screenRect(sb, Affine.IDENTITY).apply { inset(-8f * density, -8f * density) }
        val chips = listOfNotNull(resizeChip(r), moveChip(r), rotateHandle(r).takeIf { canRotateSelection() })
        if (chips.any { it.dist(screen) < dists[i] }) return -1
      }
    }
    return i
  }

  /** The resize chip sits just off the selection's bottom-right corner, clear of a shape's own corner dot. */
  private fun resizeChip(r: RectF) = Pt(r.right + RESIZE_CHIP_OFFSET_DP * density, r.bottom + RESIZE_CHIP_OFFSET_DP * density)

  private val resizeArrow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val resizePath = android.graphics.Path()

  /** The move chip sits just off the selection's bottom-left corner, mirroring the resize chip. */
  private fun moveChip(r: RectF) = Pt(r.left - RESIZE_CHIP_OFFSET_DP * density, r.bottom + RESIZE_CHIP_OFFSET_DP * density)

  /**
   * A round chip with a four-way arrow: hold and drag it to move the selection. Handy for shapes
   * that are mostly dots and lines, where grabbing the shape itself is fiddly.
   */
  private fun drawMoveChip(canvas: Canvas, at: Pt) {
    val d = density
    val back = ((SystemClock.uptimeMillis() - chipBackAt) / CHIP_BACK_MS.toFloat()).coerceIn(0f, 1f)
    val fading = back < 1f
    if (fading) {
      val a = easeOut(back)
      canvas.saveLayerAlpha(at.x - 16f * d, at.y - 16f * d, at.x + 16f * d, at.y + 16f * d, (a * 255).toInt())
      canvas.scale(0.85f + 0.15f * a, 0.85f + 0.15f * a, at.x, at.y)
    }
    canvas.drawCircle(at.x, at.y, 13f * d, handleFill)
    canvas.drawCircle(at.x, at.y, 13f * d, handleStroke)
    resizeArrow.color = handleStroke.color
    resizeArrow.strokeWidth = 1.6f * d
    val a = 7.2f * d
    val h = 2.1f * d
    resizePath.rewind()
    resizePath.moveTo(at.x - a, at.y)
    resizePath.lineTo(at.x + a, at.y)
    resizePath.moveTo(at.x, at.y - a)
    resizePath.lineTo(at.x, at.y + a)
    // An arrowhead on each of the four ends.
    for ((dx, dy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) {
      val tx = at.x + dx * a
      val ty = at.y + dy * a
      resizePath.moveTo(tx - dx * h - dy * h, ty - dy * h - dx * h)
      resizePath.lineTo(tx, ty)
      resizePath.lineTo(tx - dx * h + dy * h, ty - dy * h + dx * h)
    }
    canvas.drawPath(resizePath, resizeArrow)
    if (fading) {
      canvas.restore()
      postInvalidateOnAnimation()
    }
  }

  /** When a move or a dot drag ended: the resize chip (hidden meanwhile) fades back in from then. */
  private var chipBackAt = 0L

  /** A round chip with a diagonal two-way arrow, like an enlarge icon: drag it to resize. */
  private fun drawResizeChip(canvas: Canvas, at: Pt) {
    val d = density
    // Hidden while the shape is carried or reshaped; it comes back quickly once she lets go.
    val back = ((SystemClock.uptimeMillis() - chipBackAt) / CHIP_BACK_MS.toFloat()).coerceIn(0f, 1f)
    if (back < 1f) {
      val a = easeOut(back)
      canvas.saveLayerAlpha(at.x - 16f * d, at.y - 16f * d, at.x + 16f * d, at.y + 16f * d, (a * 255).toInt())
      canvas.scale(0.85f + 0.15f * a, 0.85f + 0.15f * a, at.x, at.y)
      drawChipBody(canvas, at)
      canvas.restore()
      postInvalidateOnAnimation()
      return
    }
    drawChipBody(canvas, at)
  }

  private fun drawChipBody(canvas: Canvas, at: Pt) {
    val d = density
    canvas.drawCircle(at.x, at.y, 13f * d, handleFill)
    canvas.drawCircle(at.x, at.y, 13f * d, handleStroke)
    resizeArrow.color = handleStroke.color
    resizeArrow.strokeWidth = 1.8f * d
    val a = 4.6f * d
    val h = 3.2f * d
    resizePath.rewind()
    resizePath.moveTo(at.x - a, at.y - a)
    resizePath.lineTo(at.x + a, at.y + a)
    resizePath.moveTo(at.x - a + h, at.y - a)
    resizePath.lineTo(at.x - a, at.y - a)
    resizePath.lineTo(at.x - a, at.y - a + h)
    resizePath.moveTo(at.x + a - h, at.y + a)
    resizePath.lineTo(at.x + a, at.y + a)
    resizePath.lineTo(at.x + a, at.y + a - h)
    canvas.drawPath(resizePath, resizeArrow)
  }

  private fun drawHandle(canvas: Canvas, x: Float, y: Float, radiusDp: Float) {
    canvas.drawCircle(x, y, radiusDp * density, handleFill)
    canvas.drawCircle(x, y, radiusDp * density, handleStroke)
  }

  private fun drawEraserCursor(canvas: Canvas) {
    // Only while erasing: after the pen lifts it keeps hovering for a moment, and a circle that
    // followed the hover looked like an eraser mark left behind on the page.
    if (mode != Mode.ERASE) return
    val p = erasePrev
    val s = camera.toScreen(p)
    val r = settings.eraserSize / 2f * density
    canvas.drawCircle(s.x, s.y, r, eraserFill)
    canvas.drawCircle(s.x, s.y, r, eraserPaint)
  }

  private fun screenRect(b: Rect, t: Affine): RectF {
    val a = camera.toScreen(t.apply(Pt(b.left, b.top)))
    val c = camera.toScreen(t.apply(Pt(b.right, b.bottom)))
    return RectF(minOf(a.x, c.x), minOf(a.y, c.y), maxOf(a.x, c.x), maxOf(a.y, c.y))
  }

  private fun currentActiveShape(): ShapeItem? {
    liveEdit?.let { return it }
    val id = activeShapeId ?: return null
    return doc?.get(id) as? ShapeItem
  }

  // ---- Input ----

  private fun isStylus(toolType: Int) = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

  private fun fingerDraws(): Boolean = settings.fingerDraws ?: !deviceHasStylus

  private val deviceHasStylus: Boolean by lazy {
    InputDevice.getDeviceIds().any { id -> InputDevice.getDevice(id)?.supportsSource(InputDevice.SOURCE_STYLUS) == true }
  }

  /**
   * Just looking (a PDF opened from another app, not saved yet): the pen and fingers only move and
   * zoom the page; nothing is written, picked up, pasted, undone or added.
   */
  var viewOnly = false
    set(value) {
      if (field == value) return
      field = value
      if (value) {
        runCatching { abortDrawing() }
        clearSelection()
        mode = Mode.NONE
      }
    }

  private fun drawsWith(toolType: Int) = !viewOnly && (isStylus(toolType) || toolType == MotionEvent.TOOL_TYPE_MOUSE || (toolType == MotionEvent.TOOL_TYPE_FINGER && fingerDraws()))

  private fun isDrawingMode() = mode in DRAW_MODES

  private fun world(e: MotionEvent, index: Int) = camera.toWorld(e.getX(index), e.getY(index))

  /** Any finger or pen landing on the page (an open voice memo folds away). */
  var onTouchDown: (() -> Unit)? = null

  @SuppressLint("ClickableViewAccessibility")
  override fun dispatchTouchEvent(e: MotionEvent): Boolean {
    if (doc == null) return true
    if (e.actionMasked == MotionEvent.ACTION_DOWN) onTouchDown?.invoke()
    try {
      handleTouch(e)
    } catch (t: Throwable) {
      // One bad gesture must never close the app or lose the notebook; drop the gesture instead.
      com.tali.notes.CrashLog.recovered(context, t)
      runCatching { abortDrawing() }
      mode = Mode.NONE
      host.onHint(tr(R.string.that_didn_t_work_try_again))
    }
    return true
  }

  private fun handleTouch(e: MotionEvent) {
    if ((0 until e.pointerCount).any { isStylus(e.getToolType(it)) }) {
      lastPenAt = SystemClock.uptimeMillis()
      cancelHold()
      // Writing (or erasing with the button held) isn't part of a double or triple press.
      if (e.actionMasked == MotionEvent.ACTION_DOWN) {
        removeCallbacks(penPressesDone)
        penPresses = 0
        penButtonDown = false
      }
    }
    // Any new touch catches a gliding page, like putting a finger on a moving list.
    if (e.actionMasked == MotionEvent.ACTION_DOWN) stopFling()
    when (e.actionMasked) {
      MotionEvent.ACTION_DOWN,
      MotionEvent.ACTION_POINTER_DOWN -> {
        val idx = e.actionIndex
        val toolType = e.getToolType(idx)
        if (drawsWith(toolType)) {
          when {
            isDrawingMode() && drawIsFinger && toolType == MotionEvent.TOOL_TYPE_FINGER -> {
              // A second finger while finger-drawing means pan/zoom instead, or undo if it's a quick tap.
              abortDrawing()
              maxPointers = 2
              multiTapMoved = false
              fingerDownTime = e.downTime
              startPinch(e)
            }
            isDrawingMode() -> Unit
            else -> {
              if (mode == Mode.PAN || mode == Mode.PINCH || mode == Mode.FINGER_PENDING) mode = Mode.NONE
              beginDraw(e, idx)
            }
          }
        } else if (!isDrawingMode()) {
          // A finger on a shape's dot or on a selection edits it (that's how she adjusts shapes);
          // anywhere else it scrolls and zooms as usual.
          if (e.actionMasked == MotionEvent.ACTION_DOWN && !viewOnly && grabsEdit(Pt(e.getX(idx), e.getY(idx)))) {
            stopFling()
            beginDraw(e, idx)
          } else {
            fingerDown(e)
          }
        }
      }
      MotionEvent.ACTION_MOVE -> if (isDrawingMode()) drawMove(e) else fingerMove(e)
      MotionEvent.ACTION_UP,
      MotionEvent.ACTION_POINTER_UP -> {
        val pid = e.getPointerId(e.actionIndex)
        if (isDrawingMode()) {
          if (pid == drawPointer) {
            if (e.flags and MotionEvent.FLAG_CANCELED != 0) abortDrawing() else endDraw(e)
          }
        } else {
          fingerUp(e)
        }
      }
      MotionEvent.ACTION_CANCEL -> {
        // The system's own palm rejection cancels touches it decides were a palm.
        cancelHold()
        if (isDrawingMode()) abortDrawing()
        mode = Mode.NONE
      }
    }
  }

  /** Pen hover and button events, from the activity (see [PenEvents]). */
  private fun penHover(e: MotionEvent) {
    when (e.actionMasked) {
      MotionEvent.ACTION_BUTTON_PRESS,
      MotionEvent.ACTION_BUTTON_RELEASE -> {
        if (isStylus(e.getToolType(0)) && e.actionButton and PEN_BUTTONS != 0) penButton(e.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
      }
      MotionEvent.ACTION_HOVER_ENTER,
      MotionEvent.ACTION_HOVER_MOVE -> {
        if (isStylus(e.getToolType(0))) {
          lastPenAt = SystemClock.uptimeMillis()
          cancelHold()
          // Some pens only report the button through the hover's button state.
          penButton(e.buttonState and PEN_BUTTONS != 0)
        }
        eraserOverride = e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
      }
      MotionEvent.ACTION_HOVER_EXIT -> {
        // Pulled away with the button down: not a press.
        penButtonDown = false
      }
    }
  }

  private val penListener: (MotionEvent) -> Unit = { e -> penHover(e) }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    PenEvents.listener = penListener
  }

  override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
    when (e.actionMasked) {
      // Hover and button events arrive through the activity (penHover); only claim them here.
      MotionEvent.ACTION_BUTTON_PRESS,
      MotionEvent.ACTION_BUTTON_RELEASE,
      MotionEvent.ACTION_HOVER_ENTER,
      MotionEvent.ACTION_HOVER_MOVE,
      MotionEvent.ACTION_HOVER_EXIT -> return true
      MotionEvent.ACTION_SCROLL -> {
        val v = e.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val h = e.getAxisValue(MotionEvent.AXIS_HSCROLL)
        if (e.metaState and android.view.KeyEvent.META_CTRL_ON != 0) {
          camera.zoomAround(e.x, e.y, if (v > 0) 1.12f else 1 / 1.12f)
          host.onZoomChanged(camera.zoom)
        } else {
          camera.pan(-h * 60f * density, v * 60f * density)
        }
        afterCameraMove()
        return true
      }
    }
    return super.dispatchGenericMotionEvent(e)
  }

  // ---- Drawing gestures (stylus, mouse, or finger when finger drawing is on) ----

  private fun beginDraw(e: MotionEvent, idx: Int) {
    val d = doc ?: return
    // Another touch before the menu came back: it waits until she's done again.
    removeCallbacks(menuBack)
    drawPointer = e.getPointerId(idx)
    drawIsFinger = e.getToolType(idx) == MotionEvent.TOOL_TYPE_FINGER
    val p = world(e, idx)
    val screen = Pt(e.getX(idx), e.getY(idx))
    eraserOverride = e.getToolType(idx) == MotionEvent.TOOL_TYPE_ERASER || e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0

    // 0. The resize and move chips win over everything (they sit clear of the shape's own dots).
    selectionBounds?.let { sb ->
      if (eraserOverride) return@let
      val r = screenRect(sb, Affine.IDENTITY).apply { inset(-8f * density, -8f * density) }
      val resizing = resizeChip(r).dist(screen) < HANDLE_HIT_DP * density
      if (resizing || moveChip(r).dist(screen) < HANDLE_HIT_DP * density) {
        dragStart = p
        dragTransform = Affine.IDENTITY
        tiles.hidden = selection.map { it.id }.toSet()
        startMoveSnap()
        mode = if (resizing) Mode.SCALE_SELECTION else Mode.MOVE_SELECTION
        host.onSelectionChanged(null)
        invalidate()
        return
      }
    }

    // 1. Handles of the shape being edited win over everything else.
    currentActiveShape()?.let { shape ->
      val handles = shape.geometry.handles()
      val hit = handleAt(shape, screen)
      if (hit >= 0) {
        handleIndex = hit
        // Keep where on the dot she grabbed it, so the shape doesn't jump to the pen tip.
        handleGrab = handles[hit] - p
        liveEdit = shape
        tiles.hidden = setOf(shape.id)
        snapTargets = gatherSnapTargets(setOf(shape.id))
        snap = newSnap()
        mode = Mode.HANDLE_DRAG
        host.onSelectionChanged(null)
        return
      }
    }

    // 2. Existing selection: drag inside to move, corner to resize, the handle above to rotate.
    val sb = selectionBounds
    if (sb != null && !eraserOverride) {
      val r = screenRect(sb, Affine.IDENTITY).apply { inset(-8f * density, -8f * density) }
      if (canRotateSelection() && rotateHandle(r).dist(screen) < HANDLE_HIT_DP * density) {
        dragStart = p
        rotatePivot = sb.center
        rotateAngle = 0f
        dragTransform = Affine.IDENTITY
        tiles.hidden = selection.map { it.id }.toSet()
        mode = Mode.ROTATE_SELECTION
        host.onSelectionChanged(null)
        invalidate()
        return
      }
      val nearCorner = resizeChip(r).dist(screen) < HANDLE_HIT_DP * density
      // A shape that picked itself up after being drawn doesn't take the pen inside it: she may be
      // writing in the box she just drew. A finger still moves it, and so does the pen with the
      // Shapes tool (dragging in the shape just drawn moves it rather than starting another).
      val writesThrough = softSelection && !drawIsFinger && !nearCorner && tool != Tool.SHAPE
      if (!writesThrough && (nearCorner || r.contains(screen.x, screen.y))) {
        dragStart = p
        dragTransform = Affine.IDENTITY
        tiles.hidden = selection.map { it.id }.toSet()
        if (!nearCorner) startMoveSnap()
        mode = if (nearCorner) Mode.SCALE_SELECTION else Mode.MOVE_SELECTION
        host.onSelectionChanged(null)
        invalidate()
        return
      }
    }
    if (selection.isNotEmpty() || activeShapeId != null) clearSelection()

    val effective = if (eraserOverride) Tool.ERASER else tool
    val n = pageCount
    val onPaper = if (effective == Tool.PEN) inkAreas()?.any { it.contains(p) } ?: true else n == null || pageLayout.contains(n, p.x, p.y)
    if (n != null && (effective == Tool.PEN || effective == Tool.SHAPE || effective == Tool.FILL) && !onPaper) {
      mode = Mode.IGNORE
      return
    }
    if (effective != Tool.ERASER && effective != Tool.LASER) armPasteHold(screen, p)
    when (effective) {
      Tool.PEN -> if (settings.activeSlot.straight) startStraight(p) else startInk(e, idx, p, d)
      Tool.LASER -> startLaser(p)
      Tool.ERASER -> {
        requestUnbufferedDispatch(e)
        startErase(p)
      }
      Tool.LASSO -> startLasso(p)
      Tool.SHAPE -> {
        // A new shape can start right on another one (a line from a corner or off an edge).
        snapTargets = gatherSnapTargets(emptySet())
        val stuck = Snap.point(p, snapTargets, newSnap())
        // Not touching anything: it starts lined up with other shapes' sides, middles and corners.
        val lined = if (stuck == null) Snap.alignPoint(p, snapTargets, newSnap()) else null
        snap = newSnap()
        shapeStart = stuck ?: lined?.first ?: p
        shapeStartStuck = stuck != null
        startGuides = lined?.second.orEmpty()
        shapePreview = null
        mode = Mode.SHAPE_CREATE
      }
      Tool.FILL -> {
        shapeStart = p
        mode = Mode.FILL_TAP
      }
    }
  }

  private fun startInk(e: MotionEvent, idx: Int, p: Pt, d: Document) {
    // Every pen sample at once, for the lowest-latency ink. Only ink and the eraser ask for this:
    // anything that rebuilds a shape per sample would flood the main thread and stop it drawing.
    requestUnbufferedDispatch(e)
    val slot = settings.activeSlot
    val brush = Brushes.brush(slot.type, if (darkPaper) Brushes.onDarkPaper(slot.color) else slot.color, slot.size, slot.lineStyle, slot.square)
    // The pen's path is smoothed before Ink sees it (see PenSmoother); the predictor follows the smoothed path too.
    smoother.reset()
    // The guess ahead of the pen is a few millimetres on screen; the marker is wide, so zoomed in it
    // stays inside the tip, but zoomed out the marker is thin and the guess stuck out past it as a
    // pointed tail, often at an angle (the owner's video, 2 Nov 2026). For the marker it reaches at
    // most half the tip's width on screen.
    predictionGuard.reset(if (slot.type.isHighlighter) slot.size * camera.scale * 0.5f else null)
    val se = smoother.smooth(e, idx, MotionEvent.ACTION_DOWN)
    predictor.record(se)
    predictionGuard.record(se)
    // What she sees while writing is what's kept: ink off the page is hidden exactly where the
    // kept stroke is clipped (Pages.clipFor), and so is ink under the toolbars. Ink's live layer
    // sits above every view and isn't clipped to this one, so the stroke used to show over the
    // toolbars and status bar, then vanish on lift (a tester's video).
    val areas = inkAreas(pageAt(p))
    inProgress.maskPath = inkMask(areas)
    // Ink's stroke unit: how far the pen moves (cm) for one world unit, as Ink works it out.
    val unitCm = camera.scale * 2.54f / resources.displayMetrics.xdpi
    // Half the pen in, so a stretch shown never reaches past the edge (nothing for the mask to clear).
    val half = slot.size / 2f
    val halfScreen = half * camera.scale
    val loc = IntArray(2).also { getLocationInWindow(it) }
    val covers = host.inkCovers().map { r -> android.graphics.RectF(r.left - loc[0] - halfScreen, r.top - loc[1] - halfScreen, r.right - loc[0] + halfScreen, r.bottom - loc[1] + halfScreen) }
    val g = Gesture(
      slot.type,
      slot.color,
      brush,
      unitCm,
      areas?.map { r -> Rect(r.left + half, r.top + half, r.right - half, r.bottom - half) },
      covers,
      InkShadow(slot.type, brush, e.eventTime, unitCm, android.graphics.Matrix(camera.screenToWorld)),
    )
    gesture = g
    strokeId = null
    feedInk(g, se, null, last = false)
    se.recycle()
    strokePoints.clear()
    strokePoints += p
    holdAnchor = p
    mode = Mode.INK
    scheduleHold()
  }

  private fun scheduleHold() {
    removeCallbacks(holdRunnable)
    if (settings.holdToShape) postDelayed(holdRunnable, settings.holdDelayMs)
  }

  private fun drawMove(e: MotionEvent) {
    val idx = e.findPointerIndex(drawPointer)
    if (idx < 0) return
    if (hypot(e.getX(idx) - pasteHoldAt.x, e.getY(idx) - pasteHoldAt.y) > touchSlop) removeCallbacks(pasteHold)
    when (mode) {
      Mode.INK -> {
        val se = smoother.smooth(e, idx, MotionEvent.ACTION_MOVE)
        predictor.record(se)
        predictionGuard.record(se)
        val prediction = predictionGuard.tame(predictor.predict())
        gesture?.let { g -> feedInk(g, se, prediction, last = false) }
        prediction?.recycle()
        se.recycle()
        for (h in 0 until e.historySize) addInkPoint(camera.toWorld(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h)))
        addInkPoint(world(e, idx))
      }
      Mode.LIVE_SHAPE -> {
        // Only the latest pen position is kept; the shape follows it once per frame (in onDraw).
        // The pen is still sending every sample at full speed from the stroke that snapped, and
        // reshaping on each one made it trail behind.
        livePen = world(e, idx)
        invalidate()
      }
      Mode.STRAIGHT -> straightTo(world(e, idx))
      Mode.LASER -> {
        for (h in 0 until e.historySize) laserTo(camera.toWorld(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h)))
        laserTo(world(e, idx))
      }
      Mode.ERASE -> {
        for (h in 0 until e.historySize) eraseTo(camera.toWorld(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h)))
        eraseTo(world(e, idx))
        invalidate()
      }
      Mode.LASSO -> {
        for (h in 0 until e.historySize) lassoPoints += camera.toWorld(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h))
        lassoPoints += world(e, idx)
        invalidate()
      }
      Mode.MOVE_SELECTION -> {
        moveSelectionTo(world(e, idx))
        dragScreen = Pt(e.getX(idx), e.getY(idx))
        if (!edgeScrolling) {
          edgeScrolling = true
          edgeSince = 0L
          edgeFrameAt = 0L
          postOnAnimation(edgeStep)
        }
        invalidate()
      }
      Mode.ROTATE_SELECTION -> {
        val start = (dragStart - rotatePivot).angle()
        var angle = (world(e, idx) - rotatePivot).angle() - start
        // Snap to 45° steps when close (she asked for 45° rather than finer steps). A single line
        // snaps by its own direction, so it lands level, upright or diagonal however it started.
        val step = (PI / 4).toFloat()
        val line = ((selection.singleOrNull() as? ShapeItem)?.geometry as? LineShape)
        val base = line?.let { (it.b - it.a).angle() } ?: 0f
        val snapped = Math.round((base + angle) / step) * step - base
        if (abs(angle - snapped) < SNAP_ROTATE_RAD) angle = snapped
        rotateAngle = angle
        dragTransform = Affine.rotateAround(rotatePivot, angle)
        invalidate()
      }
      Mode.SCALE_SELECTION -> {
        val b = selectionBounds ?: return
        val anchor = Pt(b.left, b.top)
        val corner = Pt(b.right, b.bottom)
        var s = (world(e, idx).dist(anchor) / max(1f, dragStart.dist(anchor))).coerceIn(0.1f, 10f)
        // Its far side or middle lines up with nearby shapes as it grows or shrinks.
        snapMoving?.let { m -> m.box?.let { box -> m to box } }?.let { (m, box) ->
          val (snapped, guides) = Snap.scale(box, anchor, s, snapTargets, snap, m.middleOnly)
          s = snapped.coerceIn(0.1f, 10f)
          showSnap(guides)
        }
        dragTransform = Affine.scaleAround(anchor, s)
        if (corner.dist(anchor) * s < 4f) dragTransform = Affine.IDENTITY
        invalidate()
      }
      Mode.HANDLE_DRAG -> {
        val shape = liveEdit ?: return
        val base = doc?.get(shape.id) as? ShapeItem ?: return
        val raw = world(e, idx) + handleGrab
        val bg = base.geometry
        // A dot dragged onto another shape's corner or edge locks on there (a line meeting a line).
        val stuck = if (bg is LineShape) lineEndAt(if (handleIndex == 0) bg.b else bg.a, raw).let { (p, on) -> p.takeIf { on } } else Snap.point(raw, snapTargets, snap)
        // Not touching anything: the dot lines up with other shapes' sides, middles and corners
        // (and with its own shape's other corners). A line's end on a tidy angle keeps the angle
        // and slides along the line to line up (above a box's side or middle, level with a corner).
        var aligned: Pair<Pt, List<SnapGuide>>? = null
        if (stuck == null) {
          val fixed = if (handleIndex == 0) (bg as? LineShape)?.b else (bg as? LineShape)?.a
          val tidy = fixed?.let { ShapeRecognizer.snappedLine(it, raw).b }?.takeIf { it.dist(raw) > 0.01f }
          aligned =
            if (fixed != null && tidy != null) Snap.alongAngle(fixed, tidy, snapTargets, snap).takeIf { it.second.isNotEmpty() }
            else Snap.alignPoint(raw, snapTargets + ownCorners(bg, handleIndex), snap).takeIf { it.second.isNotEmpty() }
        }
        val at = stuck ?: aligned?.first
        var g = when {
          at != null && bg is TriangleShape -> when (handleIndex) {
            0 -> bg.copy(a = at)
            1 -> bg.copy(b = at)
            else -> bg.copy(c = at)
          }
          at != null && bg is LineShape -> if (handleIndex == 0) bg.copy(a = at) else bg.copy(b = at)
          at != null -> bg.withHandle(handleIndex, at)
          else -> bg.withHandle(handleIndex, raw)
        }
        // A line end being dragged snaps to the same tidy angles as a freshly drawn line.
        if (at == null && g is LineShape) g = if (handleIndex == 1) g.copy(b = ShapeRecognizer.snappedLine(g.a, g.b).b) else g.copy(a = ShapeRecognizer.snappedLine(g.b, g.a).b)
        showSnap(stuck?.let { listOf(SnapGuide.Touch(it)) } ?: aligned?.second.orEmpty())
        liveEdit = base.withGeometry(g)
        invalidate()
      }
      Mode.SHAPE_CREATE -> {
        val raw = world(e, idx)
        // Nothing until the pen has slid a little: a touch alone never drops a shape.
        if (shapePreview == null && camera.toScreen(raw).dist(camera.toScreen(shapeStart)) < SHAPE_START_DP * density) return
        val pack = packShape.takeIf { customShape == null }
        val kind = shapeKind.takeIf { customShape == null && pack == null }
        val fromPoint = pack?.drawnFromPoint ?: (kind?.drawnFromPoint == true)
        val asLine = pack?.drawnAsLine ?: (kind?.drawnAsLine == true)
        // Where the drag ends: a line's end (and a point load's tip, a fixed support's wall) onto
        // an edge or corner; a box's corner onto a corner or edge, or lined up with other shapes.
        // Shapes drawn out from a point (supports, moments, circles) only snap where they start.
        val (end, guides) = when {
          fromPoint -> raw to emptyList()
          asLine -> lineEndAt(shapeStart, raw).let { (p, on) ->
            when {
              on -> p to listOf<SnapGuide>(SnapGuide.Touch(p))
              // On a tidy angle already: it keeps it, sliding along to line up with other shapes.
              // Otherwise its end lines up like any corner.
              p.dist(raw) > 0.01f -> Snap.alongAngle(shapeStart, p, snapTargets, snap).takeIf { it.second.isNotEmpty() } ?: (raw to emptyList())
              else -> Snap.alignPoint(raw, snapTargets, snap)
            }
          }
          else -> Snap.point(raw, snapTargets, snap)?.let { it to listOf<SnapGuide>(SnapGuide.Touch(it)) } ?: Snap.alignPoint(raw, snapTargets, snap)
        }
        shapePreview = buildShape(shapeStart, end, keepEnd = guides.isNotEmpty())
        showSnap(listOfNotNull(shapeStart.takeIf { shapeStartStuck }?.let { SnapGuide.Touch(it) }) + startGuides + guides)
        invalidate()
      }
      else -> Unit
    }
  }

  private fun addInkPoint(p: Pt) {
    strokePoints += p
    if ((p.dist(holdAnchor)) * camera.scale > HOLD_TOLERANCE_DP * density) {
      holdAnchor = p
      scheduleHold()
    }
  }

  /** The pen stopped moving mid-stroke: try to turn the stroke into a clean shape. */
  private fun onHold() {
    try {
      snapToShape()
    } catch (t: Throwable) {
      com.tali.notes.CrashLog.recovered(context, t)
    }
  }

  private var livePen: Pt? = null

  /** The live shape after hold-to-shape, following the pen (see [livePen]). */
  private fun updateLiveShape() {
    val pen = livePen ?: return
    livePen = null
    val shape = liveShape ?: return
    val g = shape.geometry
    // While still holding, a snapped line follows the pen so its length and angle can be set.
    if (g is LineShape && !g.arrowEnd) {
      // Its end locks onto another shape's corner or edge, else it snaps to the usual angles.
      val (end, on) = lineEndAt(g.a, pen)
      val stuck = end.takeIf { on }
      // On its tidy angle, its end slides along to line up with other shapes' sides, middles and corners.
      val lined = if (stuck == null) Snap.alongAngle(g.a, end, snapTargets, snap).takeIf { it.second.isNotEmpty() } else null
      liveShape = shape.withGeometry(
        when {
          stuck != null -> g.copy(b = stuck)
          lined != null -> g.copy(b = lined.first)
          else -> ShapeRecognizer.snappedLine(g.a, pen)
        },
      )
      showSnap(listOfNotNull(g.a.takeIf { liveStartStuck }?.let { SnapGuide.Touch(it) }, stuck?.let { SnapGuide.Touch(it) }) + lined?.second.orEmpty())
      invalidate()
    } else {
      // Any other shape grows or shrinks with the pen, evenly around its middle (a circle's edge
      // follows the pen tip). A small dead zone keeps hold-and-lift exactly as it snapped.
      val base = liveBase ?: return
      if (!liveFollowing && camera.toScreen(pen).dist(camera.toScreen(liveAnchor)) < touchSlop) return
      liveFollowing = true
      val bg = base.geometry
      val c = if (bg is com.tali.notes.model.EllipseShape) bg.center else base.bounds.center
      val from = liveAnchor.dist(c)
      if (from < 1f) return
      val k = (pen.dist(c) / from).coerceIn(0.15f, 8f)
      liveShape = base.withGeometry(bg.transformed(Affine.scaleAround(c, k)))
      invalidate()
    }
  }

  private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val previewFill = Paint(Paint.ANTI_ALIAS_FLAG)
  private val previewPath = android.graphics.Path()

  /**
   * A shape that is still moving (after hold-to-shape, a dot being dragged, the shapes tool), drawn
   * as plain lines in its colour, thickness and line style. Building its real Ink strokes every
   * frame made it lag; they're built once it's let go.
   */
  private fun drawShapePreview(canvas: Canvas, item: ShapeItem) {
    val paths = item.geometry.paths(item.size)
    item.fill?.let { f ->
      val outline = paths.firstOrNull()
      if (outline != null && item.closed) {
        previewPath.rewind()
        outline.forEachIndexed { i, p -> if (i == 0) previewPath.moveTo(p.x, p.y) else previewPath.lineTo(p.x, p.y) }
        previewPath.close()
        previewFill.color = f
        canvas.drawPath(previewPath, previewFill)
      }
    }
    val color = if (darkPaper) Brushes.onDarkPaper(item.color) else item.color
    previewPaint.color = Brushes.shapeInk(item.pen, color)
    previewPaint.strokeWidth = item.size
    val pattern = item.line.pattern
    previewPaint.pathEffect =
      if (pattern.isEmpty()) null
      else android.graphics.DashPathEffect(pattern.mapIndexed { i, v -> if (i % 2 == 0) maxOf((v - 1f) * item.size, 0.01f) else (v + 1f) * item.size }.toFloatArray(), 0f)
    for (path in paths) {
      if (path.size < 2) continue
      previewPath.rewind()
      path.forEachIndexed { i, p -> if (i == 0) previewPath.moveTo(p.x, p.y) else previewPath.lineTo(p.x, p.y) }
      canvas.drawPath(previewPath, previewPaint)
    }
  }

  private fun snapToShape() {
    if (mode != Mode.INK) return
    val d = doc ?: return
    val id = strokeId ?: return
    if (Geo.pathLength(strokePoints) * camera.scale < 24f * density) return
    // Her own shapes and the shapes of the subjects she starred (not every subject's).
    val packs = com.tali.notes.model.ShapePacks.all.filter { it.id in settings.starredPacks }.flatMap { it.shapes }
    var geometry = ShapeRecognizer.recognize(strokePoints, settings.customShapes, packs) ?: return
    // A line drawn from another shape starts exactly on it.
    snapTargets = gatherSnapTargets(emptySet())
    liveStartStuck = false
    val line = geometry as? LineShape
    snap = newSnap()
    if (line != null && !line.arrowEnd) {
      Snap.point(line.a, snapTargets, newSnap())?.let { a ->
        geometry = line.copy(a = a)
        liveStartStuck = true
      }
    }
    val slot = settings.activeSlot
    val pen = strokePens[id] ?: slot.type
    dropStroke(null)
    liveShape = ShapeItem(d.newId(), d.nextZ(), pen, slot.color, slot.size, geometry, line = slot.lineStyle)
    liveBase = liveShape
    liveAnchor = strokePoints.lastOrNull() ?: Pt(0f, 0f)
    liveFollowing = false
    mode = Mode.LIVE_SHAPE
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    invalidate()
  }

  /** The shape the Shapes tool draws from [p0] to [p1]; a line keeps [p1] exactly when [keepEnd] (it snapped onto something). */
  private fun buildShape(p0: Pt, p1: Pt, keepEnd: Boolean = false): ShapeItem {
    // The Shapes tool has its own style (its panel), apart from the pen in use.
    val look = settings.shapeLook
    // Anything drawn like a line (lines, arrows, dimensions, point loads, fixed supports) keeps to tidy angles unless its end locked onto something.
    val pack = packShape.takeIf { customShape == null }
    val end = if (!keepEnd && customShape == null && (pack?.drawnAsLine ?: shapeKind.drawnAsLine)) ShapeRecognizer.snappedLine(p0, p1).b else p1
    val geometry = customShape?.fitInto(p0, p1) ?: pack?.create(p0, end) ?: shapeKind.create(p0, end)
    val shape = ShapeItem("preview", 0, PenType.FINELINER, look.outlineArgb, look.size, geometry, line = look.line)
    val fill = look.fillArgb
    return if (fill != null && shape.closed) shape.filled(fill) else shape
  }

  private fun endDraw(e: MotionEvent) {
    val d = doc
    val idx = e.findPointerIndex(drawPointer)
    removeCallbacks(holdRunnable)
    removeCallbacks(pasteHold)
    when (mode) {
      Mode.INK -> {
        val quickTap = e.eventTime - e.downTime < PEN_TAP_MS
        val text = if (quickTap) textTappedByPen() else null
        val tapped = if (quickTap && text == null) shapeTappedByPen() else null
        if (text != null) {
          // A quick tap on a text box opens it for typing.
          dropStroke(e)
          post { editText(text.id) }
        } else if (tapped != null) {
          // A quick tap on a shape's outline picks the shape up to change it, rather than leaving a dot.
          dropStroke(e)
          selectShape(tapped)
        } else {
          val g = gesture
          // Undo lights up now, not when Ink hands the stroke over (a quick tap on it was lost).
          if (g != null) host.onInkWaiting()
          if (g != null && idx >= 0) {
            val se = smoother.smooth(e, idx, MotionEvent.ACTION_UP)
            feedInk(g, se, null, last = true)
            se.recycle()
          } else {
            strokeId?.let { sid ->
              inProgress.finishStroke(e, drawPointer, sid)
              shadows[sid]?.finished = true
            }
            strokeId = null
            g?.let {
              it.ended = true
              if (it.split && it.parts.isEmpty()) keepWhole(it)
            }
          }
          strokeId = null
          gesture = null
        }
      }
      Mode.LIVE_SHAPE -> {
        updateLiveShape()
        liveBase = null
        val shape = liveShape
        if (shape != null && d != null) {
          d.commit(Change(emptyList(), listOf(shape)))
          // Picked up straight away, so it can be adjusted right after drawing.
          selectShape(shape, soft = true)
        }
        liveShape = null
      }
      Mode.ERASE -> finishErase()
      Mode.STRAIGHT -> finishStraight()
      Mode.LASER -> endLaser()
      Mode.LASSO -> finishLasso(if (idx >= 0) world(e, idx) else null)
      Mode.MOVE_SELECTION,
      Mode.SCALE_SELECTION,
      Mode.ROTATE_SELECTION -> {
        if (mode == Mode.MOVE_SELECTION) chipBackAt = SystemClock.uptimeMillis()
        finishSelectionDrag()
      }
      Mode.HANDLE_DRAG -> {
        chipBackAt = SystemClock.uptimeMillis()
        val edited = liveEdit
        val original = edited?.let { d?.get(it.id) }
        if (edited != null && original != null && d != null && edited != original) d.commit(Change(listOf(original), listOf(edited)))
        liveEdit = null
        tiles.hidden = emptySet()
        // It stays selected after being adjusted, ready for the next change.
        (edited?.let { d?.get(it.id) } as? ShapeItem)?.let { shape ->
          val soft = softSelection || selection.isEmpty()
          // Reshaped: the menu stays closed; a dot only tapped leaves it as it was.
          setSelection(listOf(shape), menu = if (shape == original) null else false)
          activeShapeId = shape.id
          softSelection = soft
        }
      }
      Mode.FILL_TAP -> {
        val at = if (idx >= 0) world(e, idx) else shapeStart
        if (camera.toScreen(at).dist(camera.toScreen(shapeStart)) < touchSlop * 2) fillAt(shapeStart)
      }
      Mode.SHAPE_CREATE -> {
        val preview = shapePreview
        val tapped = if (preview == null) hitTest(shapeStart, pages = false) { true } as? ShapeItem else null
        if (tapped != null) selectShape(tapped)
        else if (preview == null) {
          // A tap draws nothing (it used to drop a shape on every touch); it only lets go of a selection.
          host.onTip("shape-drag", tr(R.string.drag_on_the_page_to_draw))
        } else if (d != null) {
          val shape = ShapeItem(d.newId(), d.nextZ(), preview.pen, preview.color, preview.size, preview.geometry, fill = preview.fill, line = preview.line)
          d.commit(Change(emptyList(), listOf(shape)))
          // Picked up straight away, so it can be adjusted right after drawing.
          selectShape(shape, soft = true)
          if (shape.geometry is SectorShape) host.onTip("part-circle", tr(R.string.drag_an_end_dot_to_choose))
        }
        shapePreview = null
      }
      else -> Unit
    }
    endSnap()
    mode = Mode.NONE
    drawPointer = -1
    // Whatever this touch was, once she's done the menu comes back to a selection still held.
    if (menuClosed && selection.isNotEmpty()) {
      removeCallbacks(menuBack)
      postDelayed(menuBack, MENU_BACK_MS)
    }
    invalidate()
  }

  private fun abortDrawing() {
    endSnap()
    removeCallbacks(holdRunnable)
    removeCallbacks(pasteHold)
    dropStroke(null)
    liveShape = null
    liveBase = null
    liveEdit = null
    shapePreview = null
    straightPreview = null
    endLaser()
    lassoPoints.clear()
    erasedOriginals.clear()
    erasePieces.clear()
    dropFillEdits()
    dragTransform = Affine.IDENTITY
    tiles.hidden = emptySet()
    if (selection.isNotEmpty()) setSelection(selection)
    mode = Mode.NONE
    drawPointer = -1
    invalidate()
  }

  // ---- Snapping ----

  /** What's near the view that the thing being moved or drawn can snap to (gathered when it starts). */
  private var snapTargets: List<SnapFeatures> = emptyList()

  /** The selection's own snapping features where the move started, or null when it doesn't snap (just writing). */
  private var snapMoving: SnapFeatures? = null
  private var snapGuides: List<SnapGuide> = emptyList()
  private var shapeStartStuck = false

  /** What a new shape's first point lined up with (shown while it's drawn). */
  private var startGuides: List<SnapGuide> = emptyList()
  private var liveStartStuck = false

  /**
   * This drag's snapping (see [SnapSession]): touching catches from [SNAP_DP] on screen, lining up
   * from [ALIGN_DP], at any zoom; and it remembers what she pulled free of.
   */
  private var snap = SnapSession(SNAP_DP)

  private fun newSnap() = SnapSession(SNAP_DP / camera.zoom, ALIGN_DP / camera.zoom)

  /** What the snap targets were gathered without (the thing being moved), to gather them again when the view moves. */
  private var snapSkip: Set<String>? = null
  private var snapIds: Set<String> = emptySet()

  private fun featuresOf(item: CanvasItem): SnapFeatures? =
    when (item) {
      is ShapeItem -> item.geometry.snapFeatures()
      is ImageItem -> if (item.pdfPage || isAreaFill(item)) null else boxFeatures(item.rect)
      is TextItem -> boxFeatures(item.bounds)
      else -> null
    }

  private fun boxFeatures(r: Rect): SnapFeatures {
    val c = listOf(Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom))
    return SnapFeatures(c + r.center, c.indices.map { c[it] to c[(it + 1) % 4] }, r)
  }

  /** Shapes, pictures and text on screen now, apart from [skip]. Handwriting isn't snapped to. */
  private fun snapItemsInView(skip: Set<String>): List<CanvasItem> {
    if (!settings.snapShapes) return emptyList()
    val d = doc ?: return emptyList()
    return d.intersecting(camera.visibleWorld(width, height)).filter { it.id !in skip && it !is InkItem }.take(MAX_SNAP_TARGETS)
  }

  /**
   * What can be snapped to: shapes, pictures and text in view, apart from [skip]. Only what's on
   * screen: lining up with something out of view (on another page) would hold for no reason she
   * can see.
   */
  private fun gatherSnapTargets(skip: Set<String>): List<SnapFeatures> {
    snapSkip = skip
    val items = snapItemsInView(skip)
    snapIds = items.map { it.id }.toSet()
    return items.mapNotNull(::featuresOf)
  }

  /** The view moved mid-drag (it scrolls at the screen's edge, or she pans): snap to what's in view now. */
  private fun refreshSnapTargets() {
    val skip = snapSkip ?: return
    if (mode != Mode.MOVE_SELECTION && mode != Mode.SCALE_SELECTION && mode != Mode.HANDLE_DRAG && mode != Mode.SHAPE_CREATE && mode != Mode.LIVE_SHAPE) return
    val items = snapItemsInView(skip)
    val ids = items.map { it.id }.toSet()
    if (ids == snapIds) return
    snapIds = ids
    snapTargets = items.mapNotNull(::featuresOf)
    // Lock keys name targets by their place in the list, which just changed: start afresh.
    snap = newSnap()
  }

  /**
   * A move begins: shapes, pictures and text snap by their own features. Handwriting picked up on
   * its own lines up by the middle of its box (centred in a box, or on a line); nothing snaps to it.
   */
  private fun startMoveSnap() {
    snap = newSnap()
    if (!settings.snapShapes) {
      snapMoving = null
      return
    }
    val parts = selection.filter { it !is InkItem }.mapNotNull(::featuresOf)
    snapMoving = when {
      parts.isNotEmpty() -> {
        val f = SnapFeatures.of(parts)
        // A big selection just lines up by its box (checking every corner of every part would be slow).
        if (f.points.size > 80 || f.segments.size > 600) SnapFeatures(emptyList(), emptyList(), f.box) else f
      }
      selection.isNotEmpty() -> SnapFeatures.middleOf(selection.map { it.bounds }.reduce(Rect::union))
      else -> null
    }
    snapTargets = gatherSnapTargets(selection.map { it.id }.toSet())
  }

  /** Carries the selection so the point grabbed at [dragStart] is at [p], locking onto nearby shapes. */
  private fun moveSelectionTo(p: Pt) {
    var dx = p.x - dragStart.x
    var dy = p.y - dragStart.y
    val m = snapMoving
    if (m != null) {
      val r = Snap.move(m.moved(dx, dy), snapTargets, snap)
      dx += r.offset.x
      dy += r.offset.y
      showSnap(r.guides)
    }
    dragTransform = Affine.translate(dx, dy)
  }

  /**
   * Where a line's end goes, dragged to [raw] with its other end at [fixed]: onto another shape's
   * end or corner; else, keeping its tidy angle, onto the edge it reaches; else onto the nearest
   * edge; else free at its tidy angle. The second value says whether it locked onto something.
   */
  private fun lineEndAt(fixed: Pt, raw: Pt): Pair<Pt, Boolean> {
    Snap.corner(raw, snapTargets, snap)?.let { return it to true }
    val angled = ShapeRecognizer.snappedLine(fixed, raw).b
    Snap.alongLine(fixed, angled, snapTargets, snap)?.let { return it to true }
    Snap.point(raw, snapTargets, snap)?.let { return it to true }
    return angled to false
  }

  /**
   * The other corners of [g] while its dot [index] is dragged, for its corner to line up with
   * (a triangle's or polygon's corner level with another). Only shapes whose dot moves its own
   * corner alone: a rectangle's side moves two corners, which would line up with themselves.
   */
  private fun ownCorners(g: ShapeGeometry, index: Int): List<SnapFeatures> {
    val corners = when {
      g is TriangleShape -> g.points
      g is com.tali.notes.model.PolyShape && !g.smooth && !g.isRect() -> g.points
      else -> return emptyList()
    }
    return listOf(SnapFeatures(corners.filterIndexed { i, _ -> i != index }, emptyList(), null))
  }

  /**
   * Shows [guides] at the end of a frame's snapping (one cross at most, where the thing being moved
   * touches now; lining-up guides all show); a light tick when something first locks on.
   */
  private fun showSnap(guides: List<SnapGuide>) {
    val touch = guides.lastOrNull { it is SnapGuide.Touch }
    val shown = guides.filter { it !is SnapGuide.Touch } + listOfNotNull(touch)
    if (shown.isNotEmpty() && snapGuides.isEmpty()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    snapGuides = shown
    // The frame is done: note what she pulled free of, and start the next one at the zoom now.
    snap.end()
    snap.begin(SNAP_DP / camera.zoom, ALIGN_DP / camera.zoom)
  }

  private fun endSnap() {
    snapGuides = emptyList()
    snapTargets = emptyList()
    snapSkip = null
    snapIds = emptySet()
    snapMoving = null
    shapeStartStuck = false
    startGuides = emptyList()
    liveStartStuck = false
  }

  private val guideLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

  /** Thin dashed lines where things line up, and a small ring where they touch. */
  private fun drawSnapGuides(canvas: Canvas) {
    if (snapGuides.isEmpty()) return
    val d = density
    guideLine.color = accentColor
    for (g in snapGuides) when (g) {
      is SnapGuide.Align -> {
        guideLine.strokeWidth = 1.2f * d
        guideLine.pathEffect = DashPathEffect(floatArrayOf(5f * d, 4f * d), 0f)
        val pad = 14f * d
        if (g.vertical) {
          val a = camera.toScreen(Pt(g.at, g.from))
          val b = camera.toScreen(Pt(g.at, g.to))
          canvas.drawLine(a.x, a.y - pad, b.x, b.y + pad, guideLine)
        } else {
          val a = camera.toScreen(Pt(g.from, g.at))
          val b = camera.toScreen(Pt(g.to, g.at))
          canvas.drawLine(a.x - pad, a.y, b.x + pad, b.y, guideLine)
        }
      }
      is SnapGuide.Touch -> {
        // A small cross where they meet, on a soft white halo so it reads over any line.
        val s = camera.toScreen(g.at)
        val k = 2.6f * d
        guideLine.pathEffect = null
        guideLine.strokeCap = Paint.Cap.ROUND
        guideLine.color = 0xE6FFFFFF.toInt()
        guideLine.strokeWidth = 3.4f * d
        canvas.drawLine(s.x - k, s.y - k, s.x + k, s.y + k, guideLine)
        canvas.drawLine(s.x - k, s.y + k, s.x + k, s.y - k, guideLine)
        guideLine.color = accentColor
        guideLine.strokeWidth = 1.4f * d
        canvas.drawLine(s.x - k, s.y - k, s.x + k, s.y + k, guideLine)
        canvas.drawLine(s.x - k, s.y + k, s.x + k, s.y - k, guideLine)
      }
    }
  }

  // ---- Straight highlighter ----

  private var straightStart = Pt.ZERO
  private var straightPreview: InkItem? = null

  private fun startStraight(p: Pt) {
    straightStart = p
    straightTo(p)
    mode = Mode.STRAIGHT
  }

  /** The line from where the pen landed to [p], level or upright when it's within a few degrees. */
  private fun straightTo(p: Pt) {
    val slot = settings.activeSlot
    var end = p
    val v = p - straightStart
    if (v.length() > 0.5f) {
      val a = Math.toDegrees(kotlin.math.atan2(v.y, v.x).toDouble())
      val off = ((a % 90.0) + 90.0) % 90.0
      if (off < STRAIGHT_SNAP_DEG || off > 90.0 - STRAIGHT_SNAP_DEG) {
        val axis = Math.round(a / 90.0) * 90.0
        val r = Math.toRadians(axis).toFloat()
        end = straightStart + Pt(kotlin.math.cos(r), kotlin.math.sin(r)) * v.length()
      }
    }
    // A tap leaves a short mark, as the freehand highlighter does.
    if (end.dist(straightStart) < 0.1f) end = straightStart + Pt(0.1f, 0f)
    val color = if (darkPaper) Brushes.onDarkPaper(slot.color) else slot.color
    val brush = Brushes.brush(slot.type, color, slot.size, LineStyle.SOLID, slot.square)
    // Rebuilt as the pen moves, so it must stay quick however long the line is (see straightLine).
    straightPreview = InkItem("straight", Long.MAX_VALUE, slot.type, StrokeFactory.straightLine(straightStart, end, brush))
    invalidate()
  }

  private fun finishStraight() {
    val d = doc
    val preview = straightPreview
    straightPreview = null
    if (d == null || preview == null) return
    val slot = settings.activeSlot
    // Stored in the real colour; dark paper only changes how it's shown.
    val stroke = preview.stroke.let { st -> st.copy(st.brush.copyWithColorIntArgb(Brushes.inkColor(slot.type, slot.color))) }
    d.commit(Change(emptyList(), listOf(InkItem(d.newId(), d.nextZ(), slot.type, stroke))))
  }

  // ---- Scrolling while carrying something to an edge ----

  private var dragScreen = Pt.ZERO
  private var edgeScrolling = false

  /** A voice memo being carried, where it is on screen (null when none is). */
  private var carriedMemo: Pt? = null

  /**
   * A voice memo is being carried at [at] on screen (null when it's let go): near an edge the page
   * scrolls that way, sooner and from further in than for a selection, since it's a small button.
   */
  fun carryMemo(at: Pt?) {
    carriedMemo = at
    if (at != null && !edgeScrolling) {
      edgeScrolling = true
      edgeSince = 0L
      edgeFrameAt = 0L
      postOnAnimation(edgeStep)
    }
  }
  private var edgeSince = 0L
  private var edgeFrameAt = 0L

  /**
   * How fast to scroll (screen px per second, in the direction to look) with the carried item at
   * [p]: nothing outside the edge zones, then a gentle ramp the deeper into the zone it goes.
   */
  private fun edgeVelocity(p: Pt, memo: Boolean = false): Pt {
    val zone = (if (memo) MEMO_EDGE_ZONE_DP else EDGE_ZONE_DP) * density
    val top = max(zone, (topClearance + 20f) * density)
    fun push(dist: Float, z: Float): Float = ((z - dist) / z).coerceIn(0f, 1f).let { it * it }
    val max = EDGE_MAX_DP_S * density
    val vx = (push(width - p.x, zone) - push(p.x, zone)) * max
    val vy = (push(height - p.y, zone) - push(p.y, top)) * max
    return Pt(vx, vy)
  }

  /** Each frame while an item is carried: after a short pause in an edge zone, the page scrolls along. */
  private val edgeStep: Runnable = object : Runnable {
    override fun run() {
      val memo = carriedMemo
      if (mode != Mode.MOVE_SELECTION && memo == null) {
        edgeScrolling = false
        return
      }
      val now = SystemClock.uptimeMillis()
      val v = if (memo != null) edgeVelocity(memo, memo = true) else edgeVelocity(dragScreen)
      if (v.x == 0f && v.y == 0f) {
        edgeSince = 0L
      } else {
        if (edgeSince == 0L) edgeSince = now
        // Passing an edge on the way somewhere doesn't scroll; resting there does.
        if (now - edgeSince >= (if (memo != null) MEMO_EDGE_DWELL_MS else EDGE_DWELL_MS) && edgeFrameAt != 0L) {
          val dt = ((now - edgeFrameAt).coerceAtMost(32L)) / 1000f
          camera.pan(-v.x * dt, -v.y * dt)
          afterCameraMove()
          // The item stays under the finger or pen while the page moves beneath it.
          if (memo == null) moveSelectionTo(camera.toWorld(dragScreen.x, dragScreen.y))
          invalidate()
        }
      }
      edgeFrameAt = now
      postOnAnimation(this)
    }
  }

  // ---- Keeping ink on the pages ----

  /**
   * Where ink may go on A4 pages, the same areas the kept strokes are clipped to (Pages.clipFor):
   * each page, or with *Write between pages* on, the whole column of pages including the gaps.
   * Never past the sides. Null on an endless canvas.
   */
  private fun inkAreas(startPage: Int? = null): List<Rect>? {
    val n = pageCount ?: return null
    return when {
      settings.inkBetweenPages -> listOf(Rect(0f, 0f, pageLayout.width, pageLayout.rect(n - 1).bottom))
      // Off: a stroke stays on the page it started on; crossing onto another page doesn't count.
      startPage != null -> listOf(pageLayout.rect(startPage.coerceIn(0, n - 1)))
      else -> (0 until n).map(pageLayout::rect)
    }
  }

  /** The page under [p] (the gap below a page counts as that page). */
  private fun pageAt(p: Pt): Int? = pageCount?.let { n -> pageLayout.indexAt(p.y).coerceIn(0, n - 1) }

  /**
   * The live stroke's mask: everything off [areas] (null: an endless canvas), and every bar
   * floating over the page ([CanvasHost.inkCovers]), so the stroke looks the same as it's written
   * as once it's kept (drawn under the bars, clipped to its page). Null when nothing is covered.
   */
  private fun inkMask(areas: List<Rect>?): android.graphics.Path? {
    val loc = IntArray(2).also { getLocationInWindow(it) }
    val covers = host.inkCovers()
    if (areas == null && covers.isEmpty()) return null
    val mask = android.graphics.Path()
    if (areas != null) {
      mask.addRect(-1e5f, -1e5f, 1e5f, 1e5f, android.graphics.Path.Direction.CW)
      mask.op(areaPath(areas, camera.worldToScreen), android.graphics.Path.Op.DIFFERENCE)
    }
    if (covers.isNotEmpty()) {
      val bars = android.graphics.Path()
      covers.forEach { r -> bars.addRect(r.left - loc[0], r.top - loc[1], r.right - loc[0], r.bottom - loc[1], android.graphics.Path.Direction.CW) }
      mask.op(bars, android.graphics.Path.Op.UNION)
    }
    return mask
  }

  /** [areas] as one path, in world units, or on screen through [toScreen]. */
  private fun areaPath(areas: List<Rect>, toScreen: android.graphics.Matrix?): android.graphics.Path =
    android.graphics.Path().apply {
      areas.forEach { addRect(it.left, it.top, it.right, it.bottom, android.graphics.Path.Direction.CW) }
      if (toScreen != null) transform(toScreen)
    }

  /**
   * On A4 pages, pictures stay on a page: moved off (to the side or into a gap), the selection is
   * nudged so its pictures sit in the nearest place on the page they overlap most (or the closest
   * one). Writing picked up with them moves the same way, so nothing comes apart.
   */
  private fun pageNudge(items: List<CanvasItem>): Affine? {
    val n = pageCount ?: return null
    val pics = items.filterIsInstance<ImageItem>().filterNot { isAreaFill(it) }
    if (pics.isEmpty()) return null
    val u = pics.map { it.rect }.reduce(Rect::union)
    fun overlap(r: Rect) = max(0f, min(r.right, u.right) - max(r.left, u.left)) * max(0f, min(r.bottom, u.bottom) - max(r.top, u.top))
    val page = (0 until n).map { pageLayout.rect(it) }.maxWith(compareBy<Rect> { overlap(it) }.thenBy { -it.center.dist(u.center) })
    fun fit(lo: Float, hi: Float, a: Float, b: Float): Float =
      if (b - a >= hi - lo) lo - a else when {
        a < lo -> lo - a
        b > hi -> hi - b
        else -> 0f
      }
    val dx = fit(page.left, page.right, u.left, u.right)
    val dy = fit(page.top, page.bottom, u.top, u.bottom)
    return if (dx == 0f && dy == 0f) null else Affine.translate(dx, dy)
  }

  // ---- Laser ----

  /** One stroke of the laser; [fadeAt] is when it began fading (0 while it's still lit). */
  private class LaserTrail {
    val dots = ArrayList<Pt>()
    var fadeAt = 0L
  }

  /** When the laser was last lifted: everything stays lit until it's been put down for a moment. */
  private var laserLiftedAt = 0L

  private val laserTrails = ArrayList<LaserTrail>()
  private var laserDown = false
  private var laserTip = Pt.ZERO
  private val laserPath = android.graphics.Path()
  private val laserGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    color = 0xFFFF3B4E.toInt()
  }
  private val laserCore = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
    color = 0xFFFF6B78.toInt()
  }
  private val laserDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  private fun startLaser(p: Pt) {
    // Pointing again brings back whatever was still fading: it all stays lit together.
    laserTrails.forEach { it.fadeAt = 0L }
    laserTrails += LaserTrail().apply { dots += p }
    laserDown = true
    laserTip = p
    mode = Mode.LASER
    invalidate()
  }

  private fun laserTo(p: Pt) {
    laserTrails.lastOrNull()?.dots?.add(p)
    laserTip = p
    invalidate()
  }

  private fun endLaser() {
    if (!laserDown) return
    laserDown = false
    laserLiftedAt = SystemClock.uptimeMillis()
    invalidate()
  }

  /**
   * The laser: a red glow with a bright core. While she keeps pointing, every stroke stays lit;
   * once the laser has been off the page for [LASER_IDLE_MS], everything drawn so far fades and
   * thins away together. Nothing is saved.
   */
  private fun drawLaser(canvas: Canvas) {
    val now = SystemClock.uptimeMillis()
    if (!laserDown && now - laserLiftedAt >= LASER_IDLE_MS) {
      laserTrails.forEach { if (it.fadeAt == 0L) it.fadeAt = laserLiftedAt + LASER_IDLE_MS }
    }
    laserTrails.removeAll { it.fadeAt != 0L && now - it.fadeAt > LASER_FADE_MS }
    if (laserTrails.isEmpty() && !laserDown) return
    for (trail in laserTrails) {
      // Ease out: it dims quickly at first, then lingers faintly for a moment.
      val t = if (trail.fadeAt == 0L) 0f else ((now - trail.fadeAt).toFloat() / LASER_FADE_MS).coerceIn(0f, 1f)
      val life = 1f - t * (2f - t)
      if (life <= 0f || trail.dots.size < 2) continue
      laserPath.rewind()
      trail.dots.forEachIndexed { i, d ->
        val sp = camera.toScreen(d)
        if (i == 0) laserPath.moveTo(sp.x, sp.y) else laserPath.lineTo(sp.x, sp.y)
      }
      laserGlow.strokeWidth = (8f + 4f * life) * density
      laserGlow.alpha = (70 * life).toInt()
      canvas.drawPath(laserPath, laserGlow)
      laserCore.strokeWidth = (2.2f + 1.3f * life) * density
      laserCore.alpha = (255 * life).toInt()
      canvas.drawPath(laserPath, laserCore)
    }
    if (laserDown) {
      // The pointer itself: a glowing red dot with a white-hot middle.
      val s = camera.toScreen(laserTip)
      laserDotPaint.color = 0x55FF3B4E
      canvas.drawCircle(s.x, s.y, 11f * density, laserDotPaint)
      laserDotPaint.color = 0xFFFF3B4E.toInt()
      canvas.drawCircle(s.x, s.y, 5f * density, laserDotPaint)
      laserDotPaint.color = 0xFFFFE4E6.toInt()
      canvas.drawCircle(s.x, s.y, 2.2f * density, laserDotPaint)
    }
    // Keep drawing frames while waiting to fade and while fading; while drawing, the pen's moves redraw.
    if (!laserDown && laserTrails.isNotEmpty()) postInvalidateOnAnimation()
  }

  // ---- Eraser ----

  private fun startErase(p: Pt) {
    // The last strokes, if Ink hasn't handed them over yet, can be erased or picked up too.
    commitWaitingInk()
    erasedOriginals.clear()
    erasePieces.clear()
    dropFillEdits()
    erasePrev = p
    mode = Mode.ERASE
    eraseSegment(p, p)
    invalidate()
  }

  private fun eraseTo(p: Pt) {
    eraseSegment(erasePrev, p)
    erasePrev = p
  }

  private fun eraseSegment(a: Pt, b: Pt) {
    val d = doc ?: return
    val radius = settings.eraserSize / 2f / camera.zoom
    val region = (Rect.around(listOf(a, b)) ?: return).outset(radius + 2f)
    val para = parallelogram(a, b, radius)
    val candidates = d.intersecting(region).filter { !it.locked && it.id !in erasedOriginals && (settings.eraserHits(it.pen) || isAreaFill(it)) } + erasePieces.values.filter { it.bounds.intersects(region) }
    for (item in candidates) {
      if (isAreaFill(item)) {
        // A fill is erased apart from its outline: whole by the stroke eraser, rubbed out where it passes by the precise one.
        val fill = item as ImageItem
        if (settings.eraserMode == EraserMode.STROKE) {
          if (erasesFill(fill, a, b, radius)) erasedOriginals[fill.id] = fill
        } else {
          rubFill(fill, a, b, radius)
        }
        continue
      }
      if (!strokesOf(item).any { para.intersects(it.shape, AffineTransform.IDENTITY) }) continue
      val isPiece = erasePieces.containsKey(item.id)
      if (settings.eraserMode == EraserMode.STROKE) {
        if (isPiece) erasePieces.remove(item.id) else erasedOriginals[item.id] = item
        continue
      }
      val inks = when (item) {
        is ShapeItem -> item.toInk { d.newId() }
        is InkItem -> listOf(item)
        else -> continue
      }
      // Split each part; untouched parts of a shape carry over as plain ink.
      val splits = inks.map { ink ->
        val reach = radius + ink.stroke.brush.size * 0.5f
        ink to StrokeFactory.split(ink.stroke, max(0.5f, radius / 2f)) { x, y -> Geo.distToSegment(Pt(x, y), a, b) <= reach }
      }
      if (splits.all { it.second == null }) continue
      if (isPiece) erasePieces.remove(item.id) else erasedOriginals[item.id] = item
      val root = pieceRoot[item.id] ?: item.id
      for ((ink, parts) in splits) {
        if (parts == null) {
          erasePieces[ink.id] = ink
          pieceRoot[ink.id] = root
        } else {
          parts.forEach { s ->
            val id = d.newId()
            erasePieces[id] = InkItem(id, ink.z, ink.pen, s)
            pieceRoot[id] = root
          }
        }
      }
    }
    tiles.hidden = erasedOriginals.keys.toSet()
  }

  /** The precise eraser clears the part of [fill] it sweeps over (from [a] to [b], [radius] wide each side). */
  private fun rubFill(fill: ImageItem, a: Pt, b: Pt, radius: Float) {
    val edit = fillEdits[fill.id] ?: run {
      if (!erasesFill(fill, a, b, radius)) return
      val src = images.peek(fill.file) ?: return
      FillEdit(fill, src.copy(Bitmap.Config.ARGB_8888, true) ?: return).also {
        fillEdits[fill.id] = it
        itemRenderer.swapped = fillEdits.mapValues { (_, e) -> e.bmp }
      }
    }
    val r = fill.rect
    val sx = edit.bmp.width / r.width
    val sy = edit.bmp.height / r.height
    fun px(p: Pt): PointF {
      var x = (p.x - r.left) * sx
      var y = (p.y - r.top) * sy
      if (fill.flipX) x = edit.bmp.width - x
      if (fill.flipY) y = edit.bmp.height - y
      return PointF(x, y)
    }
    val pa = px(a)
    val pb = px(b)
    fillRubber.strokeWidth = 2f * radius * (sx + sy) / 2f
    edit.canvas.drawLine(pa.x, pa.y, pb.x + 0.01f, pb.y, fillRubber)
    tiles.invalidate((Rect.around(listOf(a, b)) ?: return).outset(radius + 2f))
  }

  /** Forgets fills being rubbed out without keeping the change. */
  private fun dropFillEdits() {
    if (fillEdits.isEmpty()) return
    fillEdits.values.forEach { tiles.invalidate(it.item.rect) }
    fillEdits.clear()
    itemRenderer.swapped = emptyMap()
  }

  /** The fills rubbed out this stroke as they are now: an old picture → its new one, or null when nothing's left of it. */
  private fun finishFillEdits(): Map<ImageItem, ImageItem?> {
    val out = LinkedHashMap<ImageItem, ImageItem?>()
    for (edit in fillEdits.values) {
      val bmp = edit.bmp
      val px = IntArray(bmp.width * bmp.height)
      bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
      val f = edit.item
      out[f] = if (px.none { (it ushr 24) > 0 }) null
      else ImageItem(f.id, f.z, images.saveSoon(bmp, f.file.take(FILL_PREFIX.length + 9)), f.rect, f.pdfPage, f.front, f.locked, f.walls, f.flipX, f.flipY)
    }
    fillEdits.clear()
    itemRenderer.swapped = emptyMap()
    return out
  }

  /** Whether the eraser, sweeping from [a] to [b] with [radius], passes over the coloured part of [fill]. */
  private fun erasesFill(fill: ImageItem, a: Pt, b: Pt, radius: Float): Boolean {
    val step = max(1f, radius / 2f)
    val n = (a.dist(b) / step).toInt() + 1
    val r = radius * 0.7f
    for (i in 0..n) {
      val t = if (n == 0) 0f else i.toFloat() / n
      val c = Pt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
      for (o in listOf(Pt(0f, 0f), Pt(r, 0f), Pt(-r, 0f), Pt(0f, r), Pt(0f, -r))) {
        val q = Pt(c.x + o.x, c.y + o.y)
        if (fill.rect.contains(q) && fillCovers(fill, q, unloaded = false)) return true
      }
    }
    return false
  }

  private fun finishErase() {
    val d = doc ?: return
    // A fill whose outline was split keeps outlining the pieces that are left.
    val rubbed = finishFillEdits()
    rubbed.forEach { (old, now) -> if (now == null) erasedOriginals[old.id] = old }
    val gone = erasedOriginals.keys
    val fills = d.all.filterIsInstance<ImageItem>().filter { f -> f.id !in gone && (f.walls.any { it in gone } || rubbed[f] != null) }
    val regrouped = fills.map { old ->
      val f = rubbed[old] ?: old
      if (f.walls.none { it in gone }) return@map f
      val kept = f.walls.filterNot { it in gone }.toSet()
      val pieces = erasePieces.keys.filter { pieceRoot[it] in f.walls }.toSet()
      f.withWalls(kept + pieces)
    }
    pieceRoot.clear()
    val change = Change(erasedOriginals.values.toList() + fills, erasePieces.values.toList() + regrouped)
    erasePieces.clear()
    erasedOriginals.clear()
    d.commit(change)
    tiles.hidden = emptySet()
  }

  private fun strokesOf(item: CanvasItem): List<Stroke> =
    when (item) {
      is InkItem -> listOf(item.stroke)
      is ShapeItem -> item.strokes
      is ImageItem, is TextItem, is AudioItem -> emptyList()
    }

  private fun parallelogram(a: Pt, b: Pt, padding: Float): Parallelogram {
    val end = if (a.dist(b) < 0.01f) Pt(a.x + 0.01f, a.y) else b
    return ImmutableParallelogram.fromSegmentAndPadding(ImmutableSegment(ImmutableVec(a.x, a.y), ImmutableVec(end.x, end.y)), padding)
  }

  // ---- Lasso and selection ----

  private fun startLasso(p: Pt) {
    // The last strokes, if Ink hasn't handed them over yet, can be erased or picked up too.
    commitWaitingInk()
    lassoPoints.clear()
    lassoPoints += p
    mode = Mode.LASSO
  }

  private fun finishLasso(end: Pt?) {
    val d = doc ?: return
    val pts = lassoPoints.toList()
    lassoPoints.clear()
    val lengthPx = Geo.pathLength(pts) * camera.scale
    if (settings.lassoShot) {
      if (lengthPx >= 12f * density) takeScreenshot(pts)
      return
    }
    if (lengthPx < 12f * density) {
      // A tap with the lasso picks up whatever is under it, never a whole PDF page.
      hitTest(end ?: pts.firstOrNull() ?: return, pages = false, anyLock = settings.lassoLocked, filter = settings::lassoHits)?.let { selectGroup(it) }
      return
    }
    val area = Rect.around(pts) ?: return
    val free = settings.lassoFree
    // Free form: whatever the path passes over or touches, plus everything in a loop if it closes.
    val reach = FREE_SELECT_DP * density / camera.scale
    val loop = !free || loopCloses(pts)
    val near = Rect(area.left - reach, area.top - reach, area.right + reach, area.bottom + reach)
    val picked = withFills(d.all.filter { item ->
      (!item.locked || settings.lassoLocked) && settings.lassoHits(item.pen) && item.bounds.intersects(near) &&
        ((loop && item.bounds.intersects(area) && lassoTouches(item, pts)) || (free && touchedBy(item, pts, reach)))
    })
    setSelection(picked, menu = true)
    if (picked.isNotEmpty()) host.onTip("lasso", if (canRotateSelection()) tr(R.string.drag_to_move_corner_dot_to) else tr(R.string.drag_to_move_corner_dot_to_2))
    if (picked.size == 1 && picked[0] is ShapeItem) activeShapeId = picked[0].id
  }

  // ---- Screenshot lasso ----

  /** The area just captured, flashing briefly like a camera, and when. */
  private var shotFlash: Rect? = null
  private var shotFlashAt = 0L
  private var shooting = false
  private val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG)

  /**
   * The screenshot lasso: a picture of the rectangle around what she drew, as the page shows it
   * (paper, pages and everything on them; no selection marks or toolbars), sharp enough to zoom
   * into, rendered off the main thread and handed to the screen to save or share.
   */
  private fun takeScreenshot(pts: List<Pt>) {
    val d = doc ?: return
    val area = Rect.around(pts) ?: return
    if (area.width * camera.scale < 16f * density || area.height * camera.scale < 16f * density || shooting) return
    shotFlash = area
    shotFlashAt = SystemClock.uptimeMillis()
    postInvalidateOnAnimation()
    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    // At least twice the screen's sharpness, up to about 16 million pixels.
    val px = max(camera.scale, 2f * density).let { min(it, kotlin.math.sqrt(SHOT_MAX_PIXELS / (area.width * area.height))) }
    val items = d.intersecting(area).toList()
    val n = pageCount
    val layout = pageLayout
    val between = settings.inkBetweenPages
    val clip: ((CanvasItem) -> Rect?)? = n?.let { count -> { item: CanvasItem -> layout.clipFor(item, count, between) } }
    val style = paperStyle
    val dark = darkPaper
    val custom = customPaper
    val desk = deskColor
    val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
    shooting = true
    fillWorker.execute {
      val file = runCatching {
        val bmp = com.tali.notes.export.Exporter.snapshot(items, area, px, images, style, dark, custom, layout, n, desk, clip)
        val name = "Tali's Notes " + java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.UK).format(java.util.Date()) + ".png"
        dir.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.delete() }
        java.io.File(dir, name).also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle() }
      }.onFailure { com.tali.notes.CrashLog.recovered(context, it) }.getOrNull()
      post {
        shooting = false
        if (file != null) {
          shotAreas[file.path] = area
          host.onScreenshot(file)
        } else host.onHint(tr(R.string.couldn_t_take_the_screenshot))
      }
    }
  }

  /** Where each screenshot was taken from, so a copy pastes back at the same size. */
  private val shotAreas = HashMap<String, Rect>()

  /**
   * Copy on a screenshot: the picture also goes on the app's own clipboard, so holding on the page
   * pastes it there like anything copied (at the size it was taken, in this notebook or another).
   */
  fun copyScreenshot(file: java.io.File) {
    val area = shotAreas[file.path] ?: return
    fillWorker.execute {
      val name = runCatching { android.graphics.BitmapFactory.decodeFile(file.path)?.let { bmp -> images.save(bmp, "shot-") } }.getOrNull()
      post { if (name != null) Clipboard.items = listOf(ImageItem("clip", 0, name, Rect(0f, 0f, area.width, area.height), front = true)) }
    }
  }

  /** The captured area flashes white and fades, so she sees what was taken. */
  private fun drawShotFlash(canvas: Canvas) {
    val area = shotFlash ?: return
    val t = (SystemClock.uptimeMillis() - shotFlashAt) / SHOT_FLASH_MS.toFloat()
    if (t >= 1f) {
      shotFlash = null
      return
    }
    val r = screenRect(area, Affine.IDENTITY)
    flashPaint.style = Paint.Style.FILL
    flashPaint.color = 0xFFFFFFFF.toInt()
    flashPaint.alpha = (170 * (1f - easeOut(t))).toInt()
    canvas.drawRoundRect(r, 6f * density, 6f * density, flashPaint)
    flashPaint.style = Paint.Style.STROKE
    flashPaint.strokeWidth = 2f * density
    flashPaint.color = accentColor
    flashPaint.alpha = (255 * (1f - t)).toInt()
    canvas.drawRoundRect(r, 6f * density, 6f * density, flashPaint)
    postInvalidateOnAnimation()
  }

  /** Whether a free-form path comes back round near where it started (it then picks up what's inside too). */
  private fun loopCloses(pts: List<Pt>): Boolean {
    if (pts.size < 8) return false
    val gap = camera.toScreen(pts.first()).dist(camera.toScreen(pts.last()))
    return gap < LOOP_CLOSE_DP * density && Geo.pathLength(pts) * camera.scale > 3 * LOOP_CLOSE_DP * density
  }

  /** Free form: whether the traced [path] passes over or within [r] of [item]. A slide (PDF page) isn't picked by tracing over it. */
  private fun touchedBy(item: CanvasItem, path: List<Pt>, r: Float): Boolean =
    when (item) {
      is InkItem -> Lasso.nearPath(StrokeFactory.points(item.stroke), path, r)
      is ShapeItem -> item.geometry.paths(item.size).any { Lasso.nearPath(it, path, r) }
      is ImageItem -> !item.pdfPage && Lasso.nearRect(item.rect, path, r)
      is TextItem -> Lasso.nearRect(item.bounds, path, r)
      is AudioItem -> false
    }

  /**
   * Whether the lasso loop [poly] picks up [item]: anything it touches (see [Lasso]). A whole PDF
   * page (or a picture item's page) is never picked up, however much of it the loop holds: it's the
   * page itself, and lassoing writing on a slide kept taking the slide (a tester's video).
   */
  private fun lassoTouches(item: CanvasItem, poly: List<Pt>): Boolean =
    when (item) {
      is InkItem -> Lasso.touchesPath(StrokeFactory.points(item.stroke), poly)
      is ShapeItem -> item.geometry.paths(item.size).any { Lasso.touchesPath(it, poly) }
      is ImageItem -> !item.pdfPage && Lasso.touchesRect(item.rect, poly)
      is TextItem -> Lasso.touchesRect(item.bounds, poly)
      is AudioItem -> false
    }

  /**
   * The item under [p]. Writing wins over pictures, since pictures are drawn underneath it; imported
   * PDF pages count only when [pages] is true, so tapping a slide doesn't pick up the whole slide.
   */
  private fun hitTest(p: Pt, pages: Boolean = true, locked: Boolean = false, anyLock: Boolean = false, filter: (PenType?) -> Boolean): CanvasItem? {
    val d = doc ?: return null
    val r = HIT_RADIUS_DP / camera.zoom
    val para = parallelogram(p, p, r)
    // Locked items can't be picked up (only found again to unlock them).
    val near = d.intersecting(Rect(p.x - r, p.y - r, p.x + r, p.y + r)).asReversed().filter { (anyLock || it.locked == locked) && filter(it.pen) }
    return near.firstOrNull { item -> item !is ImageItem && item !is TextItem && strokesOf(item).any { para.intersects(it.shape, AffineTransform.IDENTITY) } }
      ?: near.firstOrNull { item -> item is TextItem && item.bounds.contains(p) }
      ?: near.firstOrNull { item -> item is ImageItem && (pages || !item.pdfPage) && item.rect.contains(p) }
  }

  /** The text box under a pen tap that barely moved, if any (writing over a text box still writes). */
  private fun textTappedByPen(): TextItem? {
    if (strokePoints.isEmpty()) return null
    val first = camera.toScreen(strokePoints.first())
    if (strokePoints.any { camera.toScreen(it).dist(first) > touchSlop / 2 }) return null
    return textAt(strokePoints.first())
  }

  /** The (unlocked) text box at [p], topmost first. */
  fun textAt(p: Pt): TextItem? = doc?.all?.filterIsInstance<TextItem>()?.filter { !it.locked && it.bounds.contains(p) }?.maxByOrNull { it.z }

  /** The shape under a pen tap that barely moved, if any (writing on top of a shape still writes). */
  private fun shapeTappedByPen(): ShapeItem? {
    if (strokePoints.isEmpty()) return null
    val first = camera.toScreen(strokePoints.first())
    if (strokePoints.any { camera.toScreen(it).dist(first) > touchSlop / 2 }) return null
    return hitTest(strokePoints.first(), pages = false) { true } as? ShapeItem
  }

  private fun selectShape(shape: ShapeItem, soft: Boolean = false) {
    selectSingle(shape)
    softSelection = soft
    host.onTip("shape-tap", tr(R.string.drag_a_dot_to_reshape_it))
  }

  /** Picks up [item], with the fill it outlines or the outline around it if it's a fill. */
  private fun selectGroup(item: CanvasItem) {
    val all = withFills(listOf(item))
    if (all.size == 1) selectSingle(item) else setSelection(all, menu = true)
  }

  private fun isAreaFill(item: CanvasItem) = item is ImageItem && item.file.startsWith(FILL_PREFIX)

  /**
   * A fill and the drawing around it move as one: adds to [items] every area fill outlined by
   * something in it, and every outline stroke of any fill in it (so a box drawn in four strokes
   * comes along whole). The outline is recorded when the fill is made (`outlineOf`); writing inside
   * the filled area, or anything just touching it, isn't part of it. Only the eraser still treats them apart, so she can erase just a fill or a line.
   */
  private fun withFills(items: List<CanvasItem>, locked: Boolean = settings.lassoLocked): List<CanvasItem> {
    val d = doc ?: return items
    // Only fills that know their outline (recorded when they were made) group with it.
    val fills = d.all.filterIsInstance<ImageItem>().filter { isAreaFill(it) && it.walls.isNotEmpty() && (!it.locked || locked) }
    if (fills.isEmpty() || items.isEmpty()) return items
    fun walls(f: ImageItem): List<CanvasItem> = f.walls.mapNotNull { d[it] }.filter { !it.locked || locked }
    val group = LinkedHashMap<String, CanvasItem>()
    items.forEach { group[it.id] = it }
    var changed = true
    var rounds = 0
    while (changed && rounds++ < 4) {
      changed = false
      for (f in fills) {
        if (f.id in group || f.walls.any { it in group }) {
          if (group.put(f.id, f) == null) changed = true
          walls(f).forEach { if (group.put(it.id, it) == null) changed = true }
        }
      }
    }
    return group.values.toList()
  }

  private fun selectSingle(item: CanvasItem) {
    setSelection(listOf(item), menu = true)
    activeShapeId = (item as? ShapeItem)?.id
    invalidate()
  }

  /**
   * The selection menu steps aside while she edits (moves, resizes, turns, drags a dot) and comes
   * back on its own once she's stopped for [MENU_BACK_MS], still beside the selection ([menuBack]).
   */
  private var menuClosed = false

  private val menuBack = Runnable {
    if (menuClosed && mode == Mode.NONE && selection.isNotEmpty()) {
      menuClosed = false
      publishSelection()
    }
  }

  /**
   * [menu]: true shows the selection menu (she picked these up), false keeps it away for a moment
   * (she just moved, turned or reshaped them; it returns after [MENU_BACK_MS] without another
   * edit), null leaves it as it was (a change from the menu itself, an undo); something new picked
   * up shows it.
   */
  private fun setSelection(items: List<CanvasItem>, menu: Boolean? = null) {
    val same = items.map { it.id } == selection.map { it.id }
    if (!same) softSelection = false
    menuClosed = when (menu) {
      null -> same && menuClosed
      else -> !menu
    }
    removeCallbacks(menuBack)
    if (menuClosed) postDelayed(menuBack, MENU_BACK_MS)
    selection = items
    selectionBounds = items.map { it.bounds }.reduceOrNull(Rect::union)
    publishSelection()
    invalidate()
  }

  private fun publishSelection() {
    val b = selectionBounds
    if (b == null || selection.isEmpty() || menuClosed) {
      host.onSelectionChanged(null)
      return
    }
    host.onSelectionChanged(
      SelectionInfo(
        screenRect(b, Affine.IDENTITY),
        selection.size,
        selection.any { it is ShapeItem },
        selection.any { it is ImageItem },
        selection.any { it !is ImageItem && it !is TextItem },
        canRotateSelection(),
        textId = (selection.singleOrNull() as? TextItem)?.id,
        color = selection.firstNotNullOfOrNull { (it as? InkItem)?.color ?: (it as? ShapeItem)?.color?.or(0xFF shl 24) ?: (it as? TextItem)?.color },
        lineSize = selection.firstNotNullOfOrNull { (it as? InkItem)?.size ?: (it as? ShapeItem)?.size },
        line = selection.firstNotNullOfOrNull { (it as? InkItem)?.line ?: (it as? ShapeItem)?.line },
        locked = selection.all { it.locked },
      )
    )
  }

  fun clearSelection() {
    endThicknessPreview()
    val had = selection.isNotEmpty() || activeShapeId != null
    selection = emptyList()
    selectionBounds = null
    activeShapeId = null
    softSelection = false
    menuClosed = false
    removeCallbacks(menuBack)
    if (had) host.onSelectionChanged(null)
    invalidate()
  }

  private fun finishSelectionDrag() {
    val d = doc ?: return
    val t = dragTransform
    tiles.hidden = emptySet()
    dragTransform = Affine.IDENTITY
    if (t.isIdentity) {
      // A tap on the selection: the menu again.
      menuClosed = false
      publishSelection()
      return
    }
    var moved = selection.map { it.transformed(t) }
    // Pictures land back on a page (in the same undo step).
    pageNudge(moved)?.let { nudge -> moved = moved.map { it.transformed(nudge) } }
    d.commit(Change(selection, moved))
    setSelection(moved, menu = false)
    if (moved.size == 1 && moved[0] is ShapeItem) activeShapeId = moved[0].id
  }

  /** The selection as one clean shape, for saving it to My shapes. */
  fun selectionGeometry(): ShapeGeometry? = CustomShape.geometryFrom(selection)

  fun deleteSelection() {
    val d = doc ?: return
    val sel = selection.mapNotNull { d[it.id] }
    clearSelection()
    d.commit(Change(sel, emptyList()))
  }

  /** Cuts the selection: onto the clipboard, then off the page (undo brings it back). */
  fun cutSelection() {
    if (selection.isEmpty()) return
    val n = selection.size
    Clipboard.items = selection
    deleteSelection()
    host.onHint(if (n == 1) tr(R.string.cut_hold_anywhere_to_paste_it_2) else tr(R.string.cut_n_items_hold_anywhere_to, n))
  }

  fun copySelection() {
    if (selection.isEmpty()) return
    Clipboard.items = selection
    host.onHint(if (selection.size == 1) tr(R.string.copied_hold_anywhere_to_paste_it) else tr(R.string.copied_n_items_hold_anywhere_to, selection.size))
  }

  /** Starts waiting for a hold at [screen] ([p] in the world) to offer Paste, if something's copied and it isn't on a locked item. */
  private fun armPasteHold(screen: Pt, p: Pt) {
    removeCallbacks(pasteHold)
    if (!canPaste()) return
    if (hitTest(p, locked = true) { true }?.locked == true) return
    pasteHoldAt = screen
    postDelayed(pasteHold, PASTE_HOLD_MS)
  }

  /** The hold came: whatever the touch had started (a dot of ink, a lasso, a shape) is dropped, and Paste is offered. */
  private fun offerPaste() {
    if (!canPaste()) return
    when (mode) {
      Mode.FINGER_PENDING -> {
        if (maxPointers != 1) return
        longPressed = true
      }
      Mode.INK -> {
        dropStroke(null)
        strokePoints.clear()
        mode = Mode.IGNORE
      }
      Mode.LASSO, Mode.SHAPE_CREATE, Mode.FILL_TAP, Mode.STRAIGHT -> {
        lassoPoints.clear()
        shapePreview = null
        straightPreview = null
        mode = Mode.IGNORE
      }
      else -> return
    }
    removeCallbacks(holdRunnable)
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    host.onPasteOffer(PasteOffer(pasteHoldAt.x, pasteHoldAt.y, camera.toWorld(pasteHoldAt.x, pasteHoldAt.y), if (tabletClipNewer()) 1 else Clipboard.items.size))
    invalidate()
  }

  private fun canPaste() = Clipboard.items.isNotEmpty() || tabletClipNewer()

  /**
   * Whether the tablet's own clipboard holds a picture, PDF or text copied in another app after
   * anything copied here (and within [TABLET_CLIP_MS], so a hold doesn't offer something copied
   * long ago). Only its description is read, so Android doesn't announce a paste.
   */
  private fun tabletClipNewer(): Boolean {
    if (viewOnly) return false
    val desc = runCatching { context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClipDescription }.getOrNull() ?: return false
    if (listOf("image/*", "application/pdf", "text/plain", "text/html").none { desc.hasMimeType(it) }) return false
    val at = desc.timestamp
    return at > Clipboard.copiedAt && System.currentTimeMillis() - at < TABLET_CLIP_MS
  }

  /** Pastes what's on the tablet's clipboard at [at], the way a drop from another app lands. Whether there was something. */
  private fun pasteFromTablet(at: Pt): Boolean {
    val clip = runCatching { context.getSystemService(android.content.ClipboardManager::class.java)?.primaryClip }.getOrNull() ?: return false
    val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    val text = if (uris.isEmpty()) (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).coerceToText(context)?.toString() }.joinToString("\n").ifBlank { null } else null
    if (uris.isEmpty() && text == null) return false
    host.onDropped(Dropped(uris, text, at))
    return true
  }

  /** Pastes the clipboard centred on [at] (the middle of the screen if null), selected so it can be moved into place. */
  fun paste(at: Pt? = null) {
    val d = doc ?: return
    // Copied in another app since: that comes in (a picture, a PDF's pages, text).
    if (tabletClipNewer() && pasteFromTablet(at ?: camera.visibleWorld(width, height).center)) return
    val items = Clipboard.items
    val bounds = items.map { it.bounds }.reduceOrNull(Rect::union) ?: return
    val c = at ?: camera.visibleWorld(width, height).center
    val move = Affine.translate(c.x - bounds.center.x, c.y - bounds.center.y)
    val copies = copiesOf(items.sortedBy { it.z }, move, d)
    d.commit(Change(emptyList(), copies))
    setSelection(copies)
  }

  /**
   * Copies of [items] moved by [m], with new ids. A fill copied with its outline keeps outlining
   * the copies, so the copy is one filled shape too.
   */
  private fun copiesOf(items: List<CanvasItem>, m: Affine, d: Document): List<CanvasItem> {
    val ids = items.associate { it.id to d.newId() }
    return items.map { item ->
      val copy = item.transformed(m, ids.getValue(item.id), d.nextZ())
      if (copy is ImageItem && item is ImageItem && item.walls.isNotEmpty()) copy.withWalls(item.walls.mapNotNull { ids[it] }.toSet()) else copy
    }
  }

  /**
   * Mirrors the selection in place, left to right ([horizontal]) or upside down, around its middle
   * (one undo step). Writing, shapes and area fills flip; pictures and text move to their mirrored
   * places but keep facing the right way.
   */
  fun mirrorSelection(horizontal: Boolean) {
    val d = doc ?: return
    val b = selectionBounds ?: return
    val sel = selection.mapNotNull { d[it.id] }
    if (sel.isEmpty()) return
    val m = if (horizontal) Affine.scaleAround(b.center, -1f, 1f) else Affine.scaleAround(b.center, 1f, -1f)
    val mirrored = sel.map { item ->
      when (item) {
        is ImageItem, is TextItem -> {
          val c = item.bounds.center
          val to = m.apply(c)
          val moved = item.transformed(Affine.translate(to.x - c.x, to.y - c.y))
          // An area fill flips with its outline; other pictures and text keep facing the right way.
          if (moved is ImageItem && isAreaFill(moved)) moved.flipped(horizontal) else moved
        }
        else -> item.transformed(m)
      }
    }
    d.commit(Change(sel, mirrored))
    setSelection(mirrored)
    if (mirrored.size == 1 && mirrored[0] is ShapeItem) activeShapeId = mirrored[0].id
  }

  fun duplicateSelection() {
    val d = doc ?: return
    val offset = Affine.translate(24f, 24f)
    val copies = copiesOf(selection, offset, d)
    d.commit(Change(emptyList(), copies))
    setSelection(copies)
  }

  /**
   * Brings the selection in front of everything or sends it behind everything, keeping its own
   * order. Pictures brought forward can cover writing; sent back, they sit behind it like paper.
   */
  fun arrangeSelection(toFront: Boolean) {
    val d = doc ?: return
    val sel = selection.mapNotNull { d[it.id] }.sortedBy { it.z }
    if (sel.isEmpty()) return
    val ids = sel.map { it.id }.toSet()
    val all = d.all.toList()
    val others = all.filter { it.id !in ids }
    val alreadyThere =
      if (toFront) sel.none { it is ImageItem && !it.front } && others.none { it.z > sel.first().z && !(it is ImageItem && !it.front) }
      else sel.none { it is ImageItem && it.front } && others.none { it.z < sel.last().z }
    if (alreadyThere) {
      host.onHint(if (toFront) tr(R.string.already_in_front) else tr(R.string.already_at_the_back))
      return
    }
    val bottom = all.firstOrNull()?.z ?: 0L
    val moved =
      sel.mapIndexed { i, it ->
        val z = if (toFront) d.nextZ() else bottom - sel.size + i
        if (it is ImageItem) it.inFront(toFront, z) else it.withZ(z)
      }
    d.commit(Change(sel, moved))
    setSelection(moved)
    host.onHint(if (toFront) tr(R.string.brought_to_the_front) else tr(R.string.sent_to_the_back))
  }

  fun recolorSelection(color: Int) {
    val d = doc ?: return
    val recolored =
      selection.map {
        when (it) {
          is InkItem -> it.recolored(color)
          is ShapeItem -> it.recolored(color)
          is TextItem -> it.recolored(color)
          is ImageItem, is AudioItem -> it
        }
      }
    d.commit(Change(selection, recolored))
    setSelection(recolored)
  }

  /**
   * Changes how the selected writing and shapes are drawn: [size] thick and/or in [line] style
   * (null leaves that as it is). Pictures and text keep theirs.
   */
  /** The selection as it looks at a thickness still being slid (not saved until the slider is let go). */
  private var restylePreview: List<CanvasItem>? = null
  private var pendingThickness: Float? = null

  /** The selection menu's thickness slider is moving: show the selection [size] thick straight away. */
  fun previewThickness(size: Float) {
    if (selection.isEmpty()) return
    pendingThickness = size
    tiles.hidden = selection.map { it.id }.toSet()
    invalidate()
  }

  private fun endThicknessPreview() {
    if (restylePreview == null && pendingThickness == null) return
    restylePreview = null
    pendingThickness = null
    tiles.hidden = emptySet()
    invalidate()
  }

  fun restyleSelection(size: Float? = null, line: LineStyle? = null) {
    endThicknessPreview()
    val d = doc ?: return
    // Slid back to where it was: nothing to save.
    if (size == null && line == null) return
    val changed =
      selection.map {
        when (it) {
          is InkItem -> it.restyled(size ?: it.size, line ?: it.line)
          is ShapeItem -> it.restyled(size ?: it.size, line ?: it.line)
          else -> it
        }
      }
    d.commit(Change(selection, changed))
    setSelection(changed)
    if (changed.size == 1 && changed[0] is ShapeItem) activeShapeId = changed[0].id
  }

  /** Puts a picture in the middle of the current view, sized to fit comfortably. */
  /**
   * Adds a picture in the middle of the view, or centred on [at] (where it was dropped). [index]
   * steps each of several pictures added at once a little down and right, so none hides another.
   */
  fun insertImage(file: String, bitmap: android.graphics.Bitmap, at: Pt? = null, index: Int = 0) {
    val d = doc ?: return
    val view = camera.visibleWorld(width, height)
    val maxW = view.width * 0.6f
    val maxH = view.height * 0.6f
    val s = minOf(maxW / bitmap.width, maxH / bitmap.height, 1f / density * 2f)
    val w = bitmap.width * s
    val h = bitmap.height * s
    val step = index * 36f / camera.zoom
    val c = (at ?: view.center).let { Pt(it.x + step, it.y + step) }
    images.put(file, bitmap)
    val placed = ImageItem(d.newId(), d.nextZ(), file, Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2))
    // On A4 pages it lands on the page in view, not half in a gap.
    val item = pageNudge(listOf(placed))?.let { placed.transformed(it) } ?: placed
    d.commit(Change(emptyList(), listOf(item)))
    // Selected right away so it can be dragged and resized into place.
    selectSingle(item)
  }

  /**
   * Adds imported PDF pages as one tall stack below everything already in the notebook, at their
   * real size (an A4 page exports back onto an A4 page), and scrolls to the first one.
   */
  fun insertPages(pages: List<NotesRepository.ImportedPage>) {
    val d = doc ?: return
    if (pages.isEmpty()) return
    pageCount?.let {
      insertPdfOnPages(d, pages, it)
      return
    }
    val content = d.contentBounds()
    val widths = pages.map { it.widthPt / PT_PER_DP }
    val widest = widths.max()
    val left = content?.let { it.center.x - widest / 2f } ?: camera.visibleWorld(width, height).let { it.center.x - widest / 2f }
    var top = content?.let { it.bottom + 160f } ?: (camera.visibleWorld(width, height).top + 40f)
    val first = top
    val items =
      pages.mapIndexed { i, p ->
        val w = widths[i]
        val h = p.heightPt / PT_PER_DP
        val x = left + (widest - w) / 2f
        ImageItem(d.newId(), d.nextZ() + i, p.file, Rect(x, top, x + w, top + h), pdfPage = true).also { top += h + PAGE_GAP }
      }
    d.commit(Change(emptyList(), items))
    // Show the first page across the width of the screen.
    if (width > 0 && height > 0) {
      val zoom = (width * 0.9f / (widest * density)).coerceIn(Camera.MIN_ZOOM, 1.5f)
      val scale = zoom * density
      camera.set(Viewport(left + widest / 2f - width / 2f / scale, first - topClearance / zoom, zoom))
      settleCamera()
      afterCameraMove()
    }
    invalidate()
  }

  /**
   * In a paged notebook each PDF page gets an A4 page of its own, fitted to the width at the top,
   * so a landscape slide leaves room for notes below it. An empty last page is used first.
   */
  private fun insertPdfOnPages(d: Document, pdf: List<NotesRepository.ImportedPage>, n: Int) {
    val start = if (d.intersecting(pageLayout.rect(n - 1)).isEmpty()) n - 1 else n
    val items =
      pdf.mapIndexed { i, p ->
        val r = pageLayout.rect(start + i)
        val w = p.widthPt / PT_PER_DP
        val h = p.heightPt / PT_PER_DP
        val s = minOf(r.width / w, r.height / h)
        val x = r.left + (r.width - w * s) / 2f
        ImageItem(d.newId(), d.nextZ() + i, p.file, Rect(x, r.top, x + w * s, r.top + h * s), pdfPage = true)
      }
    setPages(start + pdf.size)
    d.commit(Change(emptyList(), items))
    goToPage(start)
  }

  // ---- Voice memos ----

  /**
   * Where a memo's round button goes on screen (its centre, in pixels), [radius] and [gap] in
   * pixels. On A4 pages: just right of the page, at the memo's height, over the desk; when the desk
   * there is off screen or too narrow, it keeps to the screen's right edge instead. On an endless
   * canvas it stays where it was recorded.
   */
  fun memoCenter(memo: AudioItem, radius: Float, gap: Float): Pt {
    val p = camera.toScreen(memo.at)
    if (pageCount == null) return p
    // Always the same margin from the paper (memos only move up and down, on either side).
    val out = AudioItem.EDGE * density
    val x =
      if (memo.side == AudioItem.LEFT) camera.toScreen(Pt(0f, memo.at.y)).x - out
      else camera.toScreen(Pt(pageLayout.width, memo.at.y)).x + out
    return Pt(x.coerceIn(gap + radius, maxOf(gap + radius, width - gap - radius)), p.y)
  }

  /**
   * Where a memo dropped at screen point [sx], [sy] goes: on A4 pages, beside the side of the page
   * it's nearer to, at the usual margin, at that height (kept within the pages); on an endless
   * canvas, right there.
   */
  fun memoDrop(memo: AudioItem, sx: Float, sy: Float): AudioItem {
    val w = camera.toWorld(sx, sy)
    val n = pageCount ?: return memo.movedTo(w, memo.side, memo.edge)
    val y = w.y.coerceIn(0f, (n - 1) * pageLayout.pitch + pageLayout.height)
    val left = camera.toScreen(Pt(0f, y)).x
    val right = camera.toScreen(Pt(pageLayout.width, y)).x
    return if (sx >= (left + right) / 2) memo.movedTo(Pt(pageLayout.width + AudioItem.SIZE, y), AudioItem.RIGHT, AudioItem.EDGE)
    else memo.movedTo(Pt(-AudioItem.SIZE, y), AudioItem.LEFT, AudioItem.EDGE)
  }

  /**
   * Where a new memo goes, in the world: beside the page that's on screen, near the top of what
   * shows of it (below the toolbars), and below any memos already there so none overlap. [spacing]
   * is the screen distance between two memos, in pixels.
   */
  fun newMemoSpot(memos: Collection<AudioItem>, spacing: Float): Pt {
    val visible = camera.visibleWorld(width, height)
    val step = spacing / camera.scale
    val top = visible.top + (topClearance + 12f) / camera.zoom + step / 2
    val n = pageCount
    if (n == null) {
      val x = visible.right - (spacing / 2 + 16f * density) / camera.scale
      var y = top
      while (memos.any { kotlin.math.abs(it.at.y - y) < step * 0.9f && kotlin.math.abs(it.at.x - x) < step }) y += step
      return Pt(x, y)
    }
    val page = pageLayout.rect(pageLayout.indexAt(visible.top + visible.height * 0.4f).coerceIn(0, n - 1))
    val x = page.right + AudioItem.SIZE
    var y = top.coerceIn(page.top + step / 2, maxOf(page.top + step / 2, page.bottom - step / 2))
    while (memos.any { kotlin.math.abs(it.at.y - y) < step * 0.9f } && y + step < page.bottom) y += step
    return Pt(x, y)
  }

  // ---- Typed text ----

  /** Where a world point is on screen, in pixels (for the text editor laid over the page). */
  fun screenOf(p: Pt): Pt = camera.toScreen(p)

  /** The world point under screen pixel [x], [y]. */
  fun worldOf(x: Float, y: Float): Pt = camera.toWorld(x, y)

  /** Screen pixels per world unit. */
  val pixelsPerUnit: Float
    get() = camera.scale

  /**
   * Starts a new text block where typing would go on a normal page: at the left margin of the
   * page on screen, just below what's already written there (or near the top of an empty page),
   * as wide as the page between its margins. On an endless canvas, at the top-left of the view.
   */
  fun startText(color: Int, fontSize: Float = TEXT_SIZE) {
    val d = doc ?: return
    clearSelection()
    val mm = 160f / 25.4f
    val view = camera.visibleWorld(width, height)
    val n = pageCount
    val edit =
      if (n != null) {
        val r = pageLayout.rect(currentPage().coerceIn(0, n - 1))
        val lowest = d.intersecting(r).filter { it.bounds.center.y < r.bottom }.maxOfOrNull { it.bounds.bottom }
        val top = (lowest?.plus(6 * mm) ?: (r.top + 22 * mm)).coerceAtMost(r.bottom - 30 * mm)
        TextEdit(null, Pt(r.left + 20 * mm, top), r.width - 40 * mm, fontSize, color, "")
      } else {
        val left = view.left + 48f / camera.zoom
        TextEdit(null, Pt(left, view.top + (topClearance + 32f) / camera.zoom), minOf(view.width * 0.7f, 1100f), fontSize, color, "")
      }
    showForTyping(edit.origin.y)
    host.onEditText(edit)
  }

  /** Text dragged in from another app (split screen) becomes a text box where it was dropped. */
  fun insertText(text: String, at: Pt, color: Int) {
    val d = doc ?: return
    val trimmed = text.trim().ifEmpty { return }
    val view = camera.visibleWorld(width, height)
    val w = minOf(view.width * 0.5f, 700f)
    val placed = TextItem(d.newId(), d.nextZ(), trimmed, Pt(at.x - w / 2f, at.y), w, TEXT_SIZE, color)
    val item = pageNudge(listOf(placed))?.let { placed.transformed(it) } ?: placed
    d.commit(Change(emptyList(), listOf(item)))
    selectSingle(item)
  }

  /** What was dragged in from another app: files (with their types) or plain text, and where it was let go. */
  class Dropped(val uris: List<android.net.Uri>, val text: String?, val at: Pt)

  private fun droppable(desc: android.content.ClipDescription?): Boolean =
    !viewOnly && desc != null && listOf("image/*", "application/pdf", "application/zip", "text/plain", "text/html").any { desc.hasMimeType(it) }

  /** Takes a drop from another app side by side (split screen): pictures, PDFs or text. */
  private fun acceptDrop(e: android.view.DragEvent) {
    val clip = e.clipData ?: return
    var ctx = context
    while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
    // Without this the other app's files can't be read.
    (ctx as? android.app.Activity)?.requestDragAndDropPermissions(e)
    val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
    val text = if (uris.isEmpty()) (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).coerceToText(context)?.toString() }.joinToString("\n").ifBlank { null } else null
    if (uris.isEmpty() && text == null) return
    host.onDropped(Dropped(uris, text, camera.toWorld(e.x, e.y)))
  }

  /** Picks up an existing text block in the editor (a second tap types); it's hidden here while the editor shows it. */
  fun editText(id: String) {
    val item = doc?.get(id) as? TextItem ?: return
    clearSelection()
    tiles.hidden = setOf(id)
    host.onEditText(TextEdit(id, item.origin, item.width, item.fontSize, item.color, item.text, item.height))
    invalidate()
  }

  /** Saves what was typed: adds, changes, or (when emptied) removes the block, in one undo step. */
  fun finishText(edit: TextEdit, text: String) {
    val d = doc ?: return
    tiles.hidden = emptySet()
    val old = edit.id?.let { d[it] as? TextItem }
    val trimmed = text.trimEnd()
    when {
      old == null && trimmed.isNotBlank() -> d.commit(Change(emptyList(), listOf(TextItem(d.newId(), d.nextZ(), trimmed, edit.origin, edit.width, edit.fontSize, edit.color, height = edit.height))))
      old != null && trimmed.isBlank() -> d.commit(Change(listOf(old), emptyList()))
      old != null && (old.text != trimmed || old.fontSize != edit.fontSize || old.color != edit.color || old.origin != edit.origin || old.width != edit.width || old.height != edit.height) ->
        d.commit(Change(listOf(old), listOf(TextItem(old.id, old.z, trimmed, edit.origin, edit.width, edit.fontSize, edit.color, old.locked, edit.height))))
    }
    host.onEditText(null)
    invalidate()
  }

  /** Scrolls so the line being typed sits comfortably below the toolbars, above the keyboard. */
  fun showForTyping(top: Float) {
    if (width == 0 || height == 0) return
    val view = camera.visibleWorld(width, height)
    val wantTop = view.top + (topClearance + 40f) / camera.zoom
    if (top < wantTop || top > view.top + height * 0.4f / camera.scale) animateCamera(Viewport(camera.x, top - (topClearance + 60f) / camera.zoom, camera.zoom))
  }

  // ---- Fingers: pan, pinch, tap to select, two-finger tap to undo ----

  // A one-finger pan keeps gliding after she lets go, slowing like any scrolling list.
  private var velocity: VelocityTracker? = null
  private val scroller = OverScroller(context)
  private var flingX = 0
  private var flingY = 0
  private val flingStep =
    object : Runnable {
      override fun run() {
        if (!scroller.computeScrollOffset()) {
          settleCamera()
          return
        }
        val before = camera.viewport()
        val dx = scroller.currX - flingX
        val dy = scroller.currY - flingY
        camera.pan(dx.toFloat(), dy.toFloat())
        flingX = scroller.currX
        flingY = scroller.currY
        afterCameraMove()
        // Stop at the edges of a paged notebook instead of pushing against them. (A frame that
        // wasn't meant to move, like the very first one, doesn't count.)
        if ((dx != 0 || dy != 0) && camera.viewport() == before) {
          scroller.forceFinished(true)
          settleCamera()
          return
        }
        postOnAnimation(this)
      }
    }

  private var zoomAtDown = 1f

  private fun stopFling() {
    if (!scroller.isFinished) scroller.forceFinished(true)
    removeCallbacks(flingStep)
  }

  private fun startFling(vx: Float, vy: Float) {
    if (hypot(vx, vy) < ViewConfiguration.get(context).scaledMinimumFlingVelocity) return
    flingX = 0
    flingY = 0
    scroller.fling(0, 0, vx.toInt(), vy.toInt(), Int.MIN_VALUE, Int.MAX_VALUE, Int.MIN_VALUE, Int.MAX_VALUE)
    postOnAnimation(flingStep)
  }

  private var longPressed = false

  /** The locked item a finger is being held on (to unlock it), or null. */
  private var holdTarget: CanvasItem? = null
  private var holdStart = 0L

  /** When the S Pen last touched or hovered: a hand resting while she writes mustn't unlock anything. */
  private var lastPenAt = 0L

  /**
   * A finger held still on a locked item for [LONG_PRESS_MS] unlocks it. Guarded against a resting
   * palm or a stray touch: it must be a single fingertip-sized touch (a palm's contact is much
   * larger), with no pen nearby in the last moment, that doesn't move or grow and isn't joined by
   * another finger; any of those calls it off.
   */
  private val longPress = Runnable {
    val target = holdTarget
    holdTarget = null
    invalidate()
    if (mode != Mode.FINGER_PENDING || maxPointers != 1 || target == null) return@Runnable
    if (SystemClock.uptimeMillis() - lastPenAt < PEN_NEARBY_MS) return@Runnable
    val d = doc ?: return@Runnable
    val item = hitTest(camera.toWorld(fingerStart.x, fingerStart.y), locked = true) { true } ?: return@Runnable
    if (item.id != target.id) return@Runnable
    longPressed = true
    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    // A fill and its outline unlock together, as they were locked together.
    val group = withFills(listOf(item), locked = true).filter { it.locked }
    val unlocked = group.map { it.withLocked(false) }
    d.commit(Change(group, unlocked))
    if (unlocked.size == 1) selectSingle(unlocked[0]) else setSelection(unlocked)
    // The padlock that was building up above her finger pops open and goes.
    // The padlock that filled up over it now plays the same unlock as the menu's, from the snap on.
    lockCue = LockCue(holdAt, locking = false, start = SystemClock.uptimeMillis() - CUE_APPEAR_MS, fromHold = true)
    postInvalidateOnAnimation()
    host.onHint(tr(R.string.unlocked_it_can_be_picked_up))
  }

  /** Locks the selection in place: the lasso, taps and the eraser leave it alone. */
  fun lockSelection() {
    val d = doc ?: return
    if (selection.isEmpty()) return
    if (selection.all { it.locked }) {
      // Picked up while locked (the lasso can be set to): the same button unlocks it.
      val unlocked = selection.map { it.withLocked(false) }
      d.commit(Change(selection, unlocked))
      setSelection(unlocked)
      selectionBounds?.let { playLockCue(it.center, locking = false) }
      host.onHint(if (unlocked.size == 1) tr(R.string.unlocked) else tr(R.string.n_items_unlocked, unlocked.size))
      return
    }
    selectionBounds?.let { playLockCue(it.center, locking = true) }
    val locked = selection.map { it.withLocked(true) }
    d.commit(Change(selection, locked))
    clearSelection()
    host.onHint(if (locked.size == 1) tr(R.string.locked_hold_it_to_unlock) else tr(R.string.n_items_locked_hold_one_to, locked.size))
  }

  /** How many things in this notebook are locked. */
  fun lockedCount(): Int = doc?.all?.count { it.locked } ?: 0

  /** Unlocks everything in the notebook (one undo step). */
  fun unlockAll() {
    val d = doc ?: return
    val locked = d.all.filter { it.locked }
    if (locked.isEmpty()) return
    d.commit(Change(locked, locked.map { it.withLocked(false) }))
    host.onHint(if (locked.size == 1) tr(R.string.t_1_item_unlocked) else tr(R.string.n_items_unlocked, locked.size))
  }

  private fun fingerDown(e: MotionEvent) {
    // Fingers usually move the page next: ask Ink to hand the last strokes over soon (see [shadowWaitingInk]).
    if (strokePens.isNotEmpty()) runCatching { inProgress.requestHandoff() }
    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
      velocity?.recycle()
      velocity = VelocityTracker.obtain().also { it.addMovement(e) }
      zoomAtDown = camera.zoom
      // Only a pull that starts at the end of the notebook can open space for a new page; a
      // fast scroll down just stops there.
      pulling = !viewOnly && pageCount != null && pullDp() >= REST_DP - 4f
      // Whatever already showed below the last page (zoomed out, it can be a lot) isn't a pull.
      pullOffset = if (pulling) max(0f, pullDp() - REST_DP) else 0f
      fingerStart = Pt(e.x, e.y)
      fingerDownTime = SystemClock.uptimeMillis()
      maxPointers = 1
      multiTapMoved = false
      longPressed = false
      mode = Mode.FINGER_PENDING
      cancelHold()
      if (!viewOnly && e.getToolType(0) == MotionEvent.TOOL_TYPE_FINGER && fingertip(e) && SystemClock.uptimeMillis() - lastPenAt >= PEN_NEARBY_MS) {
        holdTarget = hitTest(camera.toWorld(e.x, e.y), locked = true) { true }
        holdTarget?.let { target ->
          // The padlock shows over the item, unless the finger is covering that spot: then just above it.
          val centre = camera.toScreen(target.bounds.center)
          val clear = HOLD_LIFT_DP * density
          holdAt = if (centre.dist(Pt(e.x, e.y)) > clear) target.bounds.center else camera.toWorld(e.x, e.y - clear)
        }
        if (holdTarget != null) {
          holdStart = SystemClock.uptimeMillis()
          postDelayed(longPress, LONG_PRESS_MS)
          postInvalidateOnAnimation()
        }
      }
      if (!viewOnly && holdTarget == null && fingertip(e)) armPasteHold(Pt(e.x, e.y), camera.toWorld(e.x, e.y))
    } else {
      removeCallbacks(pasteHold)
      cancelHold()
      maxPointers = max(maxPointers, e.pointerCount)
      startPinch(e)
    }
  }

  /** Whether every touch in [e] is fingertip-sized; a palm or the side of a hand is much larger. */
  private fun fingertip(e: MotionEvent): Boolean {
    val limit = PALM_MM * resources.displayMetrics.xdpi / 25.4f
    // Some screens report no contact size (0): then only the other checks apply.
    return (0 until e.pointerCount).all { e.getTouchMajor(it) <= limit }
  }

  private fun cancelHold() {
    removeCallbacks(longPress)
    if (holdTarget != null) {
      // Let go early: whatever of the padlock had appeared fades away quickly.
      val shown = holdProgress()
      if (shown != null) holdFade = HoldFade(SystemClock.uptimeMillis(), shown.first, shown.second, holdAt)
      holdTarget = null
      invalidate()
    }
  }

  // ---- S Pen button presses ----

  private var penButtonDown = false
  private var penButtonAt = 0L
  private var penPresses = 0

  /** After the last quick press, a moment to see whether another follows; then 2 = next pen, 3 = back. */
  private val penPressesDone = Runnable {
    when (penPresses) {
      2 -> host.onPenCycle(1)
      3 -> host.onPenCycle(-1)
    }
    penPresses = 0
  }

  /**
   * The S Pen button while hovering: counts quick presses. A long press (holding it to erase)
   * doesn't count and starts the count over.
   */
  private fun penButton(down: Boolean) {
    val now = SystemClock.uptimeMillis()
    if (down && !penButtonDown) {
      penButtonDown = true
      penButtonAt = now
      removeCallbacks(penPressesDone)
    } else if (!down && penButtonDown) {
      penButtonDown = false
      if (now - penButtonAt <= PEN_PRESS_MAX_MS) {
        penPresses++
        postDelayed(penPressesDone, PEN_PRESS_GAP_MS)
      } else {
        penPresses = 0
      }
    }
  }

  // ---- The padlock cue ----

  /**
   * A small padlock in a frosted chip, shown when something is locked or unlocked. Minimal and
   * quick: it comes in from 90% (never from nothing) with a strong ease-out, the shackle clicks
   * shut or swings open, then it rises a touch and fades, a little faster out than in.
   */
  private class LockCue(val at: Pt, val locking: Boolean, val start: Long, val fromHold: Boolean = false)

  private var lockCue: LockCue? = null

  /** A hold let go early: the padlock fades from how it looked. */
  private class HoldFade(val start: Long, val alpha: Float, val progress: Float, val at: Pt)

  /** Where (world) the hold's padlock shows. */
  private var holdAt = Pt.ZERO

  private var holdFade: HoldFade? = null


  private fun playLockCue(world: Pt, locking: Boolean) {
    lockCue = LockCue(world, locking, SystemClock.uptimeMillis())
    postInvalidateOnAnimation()
  }

  private fun easeOut(t: Float) = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it }

  /** While a finger is held on a locked item: the padlock's (alpha, hold progress 0..1), or null before it shows. */
  private fun holdProgress(): Pair<Float, Float>? {
    if (holdTarget == null) return null
    val since = SystemClock.uptimeMillis() - holdStart - HOLD_RING_DELAY_MS
    if (since <= 0) return null
    val t = (since.toFloat() / (LONG_PRESS_MS - HOLD_RING_DELAY_MS)).coerceAtMost(1f)
    return easeOut(since / 200f) to t
  }

  private fun drawLockCues(canvas: Canvas) {
    val now = SystemClock.uptimeMillis()
    var animating = false
    // Holding: the closed padlock over the item, with a ring round its chip filling as she holds.
    holdProgress()?.let { (alpha, t) ->
      val sp = camera.toScreen(holdAt)
      drawPadlock(canvas, sp.x, sp.y, alpha, 0.9f + 0.1f * alpha, 0f, t)
      animating = true
    }
    holdFade?.let { f ->
      val t = (now - f.start) / 120f
      if (t >= 1f) holdFade = null
      else {
        val sp = camera.toScreen(f.at)
        drawPadlock(canvas, sp.x, sp.y, f.alpha * (1f - t), 1f - 0.04f * t, 0f, f.progress)
        animating = true
      }
    }
    lockCue?.let { cue ->
      val t = (now - cue.start).toFloat()
      if (t >= CUE_TOTAL) lockCue = null
      else {
        val appear = if (cue.fromHold) 1f else easeOut(t / 200f)
        val leave = easeOut((t - CUE_LEAVE_FROM) / (CUE_TOTAL - CUE_LEAVE_FROM))
        // The shackle moves with a small overshoot, like a real lock snapping.
        val k = ((t - CUE_SHACKLE_FROM) / CUE_SHACKLE_MS).coerceIn(0f, 1f)
        val click = 1f + 2.2f * (k - 1f).let { it * it * it } + 1.2f * (k - 1f).let { it * it }
        val open = if (cue.locking) 1f - click else click
        // A slight pulse of the chip as it clicks.
        val pulseT = ((t - CUE_SHACKLE_FROM - CUE_SHACKLE_MS * 0.6f) / 200f).coerceIn(0f, 1f)
        val pulse = 0.06f * kotlin.math.sin(PI.toFloat() * pulseT)
        val sp = camera.toScreen(cue.at)
        drawPadlock(canvas, sp.x, sp.y - 6f * density * leave, appear * (1f - leave), (0.9f + 0.1f * appear) + pulse - 0.05f * leave, open)
        animating = true
      }
    }
    if (animating) postInvalidateOnAnimation()
  }

  private fun drawPadlock(canvas: Canvas, cx: Float, cy: Float, alpha: Float, scale: Float, open: Float, progress: Float = -1f) =
    LockGlyph.draw(canvas, density, darkPaper || deskIsDark(), cx, cy, alpha, scale, open, progress)

  private fun startPinch(e: MotionEvent) {
    mode = Mode.PINCH
    val (fx, fy, span) = focusAndSpan(e, -1)
    lastFocusX = fx
    lastFocusY = fy
    lastSpan = span
    tapStartFocus = Pt(fx, fy)
    tapStartSpan = span
  }

  private fun focusAndSpan(e: MotionEvent, skipIndex: Int): Triple<Float, Float, Float> {
    var sx = 0f
    var sy = 0f
    var n = 0
    for (i in 0 until e.pointerCount) {
      if (i == skipIndex) continue
      sx += e.getX(i)
      sy += e.getY(i)
      n++
    }
    if (n == 0) return Triple(0f, 0f, 0f)
    val fx = sx / n
    val fy = sy / n
    var dev = 0f
    for (i in 0 until e.pointerCount) {
      if (i == skipIndex) continue
      dev += hypot(e.getX(i) - fx, e.getY(i) - fy)
    }
    return Triple(fx, fy, dev / n)
  }

  private fun fingerMove(e: MotionEvent) {
    velocity?.addMovement(e)
    // A hold to unlock must stay put and stay fingertip-sized (a palm rolling down grows).
    if (holdTarget != null && (hypot(e.x - fingerStart.x, e.y - fingerStart.y) > touchSlop / 2 || !fingertip(e) || e.pointerCount > 1)) cancelHold()
    when (mode) {
      Mode.FINGER_PENDING -> {
        if (hypot(e.x - fingerStart.x, e.y - fingerStart.y) > touchSlop) {
          removeCallbacks(pasteHold)
          mode = Mode.PAN
          lastFocusX = e.x
          lastFocusY = e.y
        }
      }
      Mode.PAN -> {
        camera.pan(e.x - lastFocusX, resisted(e.y - lastFocusY))
        lastFocusX = e.x
        lastFocusY = e.y
        afterCameraMove()
      }
      Mode.PINCH -> {
        val (fx, fy, span) = focusAndSpan(e, -1)
        // Measured from where the fingers landed, so a slow drag still counts as moving.
        if (hypot(fx - tapStartFocus.x, fy - tapStartFocus.y) > touchSlop || kotlin.math.abs(span - tapStartSpan) > touchSlop) multiTapMoved = true
        camera.pan(fx - lastFocusX, resisted(fy - lastFocusY))
        if (lastSpan > 10f && span > 10f) camera.zoomAround(fx, fy, span / lastSpan)
        // Zooming out shows more below the last page, which isn't pulling: once the fingers zoom, this
        // gesture can't show the add-page sign or add a page (only sliding does).
        if (pulling && kotlin.math.abs(camera.zoom - zoomAtDown) > zoomAtDown * 0.04f) {
          pulling = false
          invalidate()
        }
        lastFocusX = fx
        lastFocusY = fy
        lastSpan = span
        host.onZoomChanged(camera.zoom)
        afterCameraMove()
      }
      else -> Unit
    }
  }

  private fun fingerUp(e: MotionEvent) {
    cancelHold()
    removeCallbacks(pasteHold)
    val quick = SystemClock.uptimeMillis() - fingerDownTime < MULTI_TAP_MS
    when {
      e.actionMasked == MotionEvent.ACTION_POINTER_UP -> {
        // Keep pinching with the remaining fingers; re-anchor so nothing jumps.
        val (fx, fy, span) = focusAndSpan(e, e.actionIndex)
        lastFocusX = fx
        lastFocusY = fy
        lastSpan = span
        if (e.pointerCount - 1 == 1 && mode == Mode.PINCH) {
          mode = Mode.PAN
        }
        return
      }
      longPressed -> Unit
      viewOnly -> Unit
      mode == Mode.FINGER_PENDING && maxPointers == 1 -> tapAt(camera.toWorld(e.x, e.y))
      maxPointers == 2 && quick && !multiTapMoved && settings.smartUndo -> undo()
      maxPointers == 3 && quick && !multiTapMoved && settings.smartUndo -> redo()
    }
    if (mode == Mode.PAN || mode == Mode.PINCH) {
      settleCamera()
      val wasPulling = pulling
      pulling = false
      // The add-page sign goes as soon as her fingers lift.
      if (wasPulling) invalidate()
      // Pulled past the last page: add one or spring back. Otherwise a one-finger flick keeps gliding.
      // One finger or two both glide, unless the fingers were zooming.
      val zoomed = kotlin.math.abs(camera.zoom - zoomAtDown) > zoomAtDown * 0.04f
      if (!(wasPulling && finishPull()) && maxPointers <= 2 && !zoomed) {
        velocity?.let { v ->
          v.addMovement(e)
          v.computeCurrentVelocity(1000, ViewConfiguration.get(context).scaledMaximumFlingVelocity.toFloat())
          val id = e.getPointerId(e.actionIndex)
          startFling(v.getXVelocity(id), v.getYVelocity(id))
        }
      }
    }
    velocity?.recycle()
    velocity = null
    mode = Mode.NONE
  }

  private fun tapAt(p: Pt) {
    // With the fill tool, a finger tap fills too.
    if (tool == Tool.FILL) {
      fillAt(p)
      return
    }
    // A finger tap on a text box opens it for typing, like any text box.
    if (tool != Tool.LASSO) textAt(p)?.let {
      editText(it.id)
      return
    }
    // A finger tap only picks something up with the lasso tool (she found taps picking things up by
    // accident); elsewhere it just lets go of the selection. A pen tap on a shape still picks it up.
    val item = if (tool == Tool.LASSO && !settings.lassoShot) hitTest(p, pages = false, anyLock = settings.lassoLocked, filter = settings::lassoHits) else null
    if (item == null) clearSelection() else selectGroup(item)
  }

  fun undo() {
    commitWaitingInk()
    clearSelection()
    doc?.undo()
  }

  fun redo() {
    commitWaitingInk()
    clearSelection()
    doc?.redo()
  }

  private fun afterCameraMove() {
    shadowWaitingInk()
    refreshSnapTargets()
    clampCamera()
    host.onCameraMoved()
    if (selection.isNotEmpty()) publishSelection()
    updatePage()
    scheduleSharp()
    invalidate()
  }

  // ---- Imported PDF pages: which one is showing, and jumping between them ----

  private var pages: List<Rect> = emptyList()
  private var pageIndex = -2

  private fun refreshPages() {
    val n = pageCount
    pages = if (n != null) pageLayout.all(n) else doc?.all?.filter { it is ImageItem && it.pdfPage }?.map { (it as ImageItem).rect }?.sortedBy { it.top }.orEmpty()
    pageIndex = -2
    updatePage()
  }

  /** The page under the upper-middle of the screen, where her eyes usually are. */
  private fun updatePage() {
    if (width == 0) return
    val index =
      if (pages.isEmpty()) -1
      else {
        val view = camera.visibleWorld(width, height)
        val y = view.top + view.height * 0.4f
        pages.indices.minBy { i -> val r = pages[i]; if (y < r.top) r.top - y else if (y > r.bottom) y - r.bottom else 0f }
      }
    if (index != pageIndex) {
      pageIndex = index
      host.onPageChanged(index, pages.size)
    }
  }

  // ---- A4 pages ----

  private fun setPages(count: Int) {
    // The page column's bottom moves with the count: what's shown past it changes.
    if (count != pageCount) tiles.invalidateAll()
    pageCount = count
    host.onPagesChanged(count)
  }

  /**
   * Keeps a paged notebook on its pages: centred across when it fits, otherwise no further out than
   * a little past either edge, and down to about half of the next (not yet added) page.
   */
  private fun clampCamera() {
    val n = pageCount ?: return
    if (width == 0 || height == 0) return
    val viewW = width / camera.scale
    val viewH = height / camera.scale
    val margin = 24f / camera.zoom
    val x =
      if (pageLayout.width + 2 * margin <= viewW) pageLayout.width / 2f - viewW / 2f
      else camera.x.coerceIn(-margin, pageLayout.width + margin - viewW)
    val minY = -topClearance / camera.zoom
    // A little room under the last page; more while her fingers pull at it (or it springs back).
    val room = if (pulling || cameraAnimator?.isRunning == true) MAX_PULL_DP else REST_DP
    val maxY = maxOf(minY, lastPageBottom(n) + room / camera.zoom - viewH)
    val y = camera.y.coerceIn(minY, maxY)
    if (x != camera.x || y != camera.y) camera.set(Viewport(x, y, camera.zoom))
  }

  /**
   * Past the last page the page follows the finger at less than half speed, like pulling against
   * a rubber band, so a new page is only added on purpose.
   */
  private fun resisted(dy: Float): Float {
    if (pageCount == null || dy >= 0f) return dy
    return if (effectivePull() > REST_DP) dy * PULL_RESISTANCE else dy
  }

  /** Fingers are on the page and started at its end (so it may be pulled past the last page). */
  private var pulling = false

  /** How much showed below the last page already when the pull began (not part of the pull). */
  private var pullOffset = 0f

  /** How far she has pulled past the last page, counting the room always left there. */
  private fun effectivePull() = pullDp() - pullOffset

  private fun lastPageBottom(n: Int) = (n - 1) * pageLayout.pitch + pageLayout.height

  /** How far below the last page the screen reaches, in dp on screen. */
  private fun pullDp(): Float {
    val n = pageCount ?: return 0f
    return (camera.visibleWorld(width, height).bottom - lastPageBottom(n)) * camera.zoom
  }

  /**
   * Her fingers left the page after pulling past the last page: far enough adds a page (it takes
   * the space she pulled open), otherwise the page springs back. Returns whether it handled that.
   */
  private fun finishPull(): Boolean {
    val n = pageCount ?: return false
    val pull = effectivePull()
    if (pull <= REST_DP + 1f) return false
    if (pull >= ADD_PULL_DP) {
      setPages(n + 1)
      performHapticFeedback(HapticFeedbackConstants.CONFIRM)
      invalidate()
      return true
    }
    val viewH = height / camera.scale
    animateCamera(Viewport(camera.x, lastPageBottom(n) + REST_DP / camera.zoom - viewH, camera.zoom))
    return true
  }

  /** Shows page [index] across the width of the screen, its top just under the toolbars. */
  private fun showPageWidth(index: Int) {
    if (width == 0 || height == 0) return
    val zoom = (width * 0.94f / (pageLayout.width * density)).coerceIn(Camera.MIN_ZOOM, 1.5f)
    val scale = zoom * density
    camera.set(Viewport(pageLayout.width / 2f - width / 2f / scale, pageLayout.rect(index).top - topClearance / zoom, zoom))
    settleCamera()
    afterCameraMove()
  }

  /** The page under the upper-middle of the screen. */
  fun currentPage(): Int = pageIndex.coerceAtLeast(0)

  /** Adds an empty page right after page [index], moving everything below it down a page. */
  fun insertPageAfter(index: Int) {
    val d = doc ?: return
    val n = pageCount ?: return
    val below = d.all.filter { it.bounds.center.y > pageLayout.rect(index).bottom }
    val shift = Affine.translate(0f, pageLayout.pitch)
    setPages(n + 1)
    if (below.isNotEmpty()) d.commit(Change(below, below.map { it.transformed(shift) }))
    goToPage(index + 1)
  }

  /**
   * Removes page [index] with everything on it, and moves the pages below up. The writing comes
   * back with undo; the last page left is only cleared.
   */
  /**
   * Moves page [from] to position [to]: everything on it goes there, and the pages in between move
   * up or down one. One undo step.
   */
  fun movePage(from: Int, to: Int) {
    val d = doc ?: return
    val n = pageCount ?: return
    if (from == to || from !in 0 until n || to !in 0 until n) return
    val pitch = pageLayout.pitch
    val before = ArrayList<CanvasItem>()
    val after = ArrayList<CanvasItem>()
    for (item in d.all + d.memos) {
      val p = floor(item.bounds.center.y / pitch).toInt().coerceIn(0, n - 1)
      val q = Pages.movedIndex(p, from, to)
      if (q != p) {
        before += item
        after += item.transformed(Affine.translate(0f, (q - p) * pitch))
      }
    }
    clearSelection()
    if (before.isNotEmpty()) d.commit(Change(before, after))
  }

  /**
   * Lays the pages out again as [order] (old page numbers, top to bottom): a page left out is
   * deleted with everything on it, a page listed again is copied (its writing too). One undo step.
   * With nothing left, one empty page stays.
   */
  fun rearrangePages(order: List<Int>) {
    val d = doc ?: return
    val n = pageCount ?: return
    val pitch = pageLayout.pitch
    // Voice memos go with their pages too.
    val byPage = (d.all + d.memos).groupBy { floor(it.bounds.center.y / pitch).toInt().coerceIn(0, n - 1) }
    val before = ArrayList<CanvasItem>()
    val after = ArrayList<CanvasItem>()
    val placed = HashSet<Int>()
    order.forEachIndexed { j, p ->
      val shift = Affine.translate(0f, (j - p) * pitch)
      val items = byPage[p].orEmpty()
      if (placed.add(p)) {
        if (j != p) items.forEach { before += it; after += it.transformed(shift) }
      } else {
        items.forEach { after += it.transformed(shift, d.newId(), d.nextZ()) }
      }
    }
    (0 until n).filterNot { it in placed }.forEach { p -> before += byPage[p].orEmpty() }
    clearSelection()
    setPages(order.size.coerceAtLeast(1))
    if (before.isNotEmpty() || after.isNotEmpty()) d.commit(Change(before, after))
    goToPage(currentPage().coerceAtMost(order.size.coerceAtLeast(1) - 1))
  }

  fun deletePage(index: Int) {
    val d = doc ?: return
    val n = pageCount ?: return
    val top = index * pageLayout.pitch
    val on = (d.all + d.memos).filter { it.bounds.center.y in top..<top + pageLayout.pitch }
    if (n == 1) {
      if (on.isNotEmpty()) d.commit(Change(on, emptyList()))
      return
    }
    val below = (d.all + d.memos).filter { it.bounds.center.y >= top + pageLayout.pitch }
    val shift = Affine.translate(0f, -pageLayout.pitch)
    setPages(n - 1)
    clearSelection()
    if (on.isNotEmpty() || below.isNotEmpty()) d.commit(Change(on + below, below.map { it.transformed(shift) }))
    goToPage(index.coerceAtMost(n - 2))
  }

  /**
   * Turns an endless canvas into A4 pages. Writing that sits left of or above the first page is
   * moved onto the pages (in one undoable step), then there are as many pages as it needs.
   */
  fun convertToPages() {
    val d = doc ?: return
    val content = d.contentBounds()
    if (content != null && (content.left < 0f || content.right > pageLayout.width || content.top < 0f)) {
      val dx =
        if (content.width <= pageLayout.width - 2 * PAGE_MARGIN) (pageLayout.width - content.width) / 2f - content.left
        else PAGE_MARGIN - content.left
      val dy = if (content.top < PAGE_MARGIN) PAGE_MARGIN - content.top else 0f
      val all = d.all.toList()
      val shift = Affine.translate(dx, dy)
      d.commit(Change(all, all.map { it.transformed(shift) }))
    }
    setPages(pageLayout.needed(d.contentBounds()))
    showPageWidth(currentPage().coerceAtMost((pageCount ?: 1) - 1))
  }

  /** Glides to a page, fitting its width to the screen with the top just under the toolbar. */
  /** Where the view is in the whole notebook, for the side scroller (world units). */
  data class ScrollSpan(val top: Float, val bottom: Float, val viewTop: Float, val viewHeight: Float)

  /** Null when the notebook fits on screen (nothing to scroll). */
  fun scrollSpan(): ScrollSpan? {
    if (width == 0 || height == 0) return null
    val viewH = height / camera.scale
    val (top, bottom) =
      pageCount?.let { n -> 0f to lastPageBottom(n) }
        ?: (doc?.contentBounds()?.let { b -> minOf(b.top, camera.y) to maxOf(b.bottom, camera.y + viewH) } ?: return null)
    if (bottom - top <= viewH * 1.05f) return null
    return ScrollSpan(top, bottom, camera.y, viewH)
  }

  /** The side scroller dragged: the view's top to [y] (world units). */
  fun scrollViewTo(y: Float) {
    stopFling()
    camera.set(Viewport(camera.x, y, camera.zoom))
    afterCameraMove()
  }

  fun goToPage(index: Int) {
    val r = pages.getOrNull(index) ?: return
    if (width == 0 || height == 0) return
    val zoom = (width * 0.9f / (r.width * density)).coerceIn(Camera.MIN_ZOOM, 1.5f)
    val scale = zoom * density
    animateCamera(Viewport(r.center.x - width / 2f / scale, r.top - topClearance / zoom, zoom))
  }

  private var cameraAnimator: android.animation.ValueAnimator? = null

  private fun animateCamera(target: Viewport) {
    stopFling()
    cameraAnimator?.cancel()
    val from = camera.viewport()
    cameraAnimator =
      android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 320
        interpolator = android.view.animation.DecelerateInterpolator(1.6f)
        addUpdateListener {
          val t = it.animatedValue as Float
          // Interpolate zoom geometrically so the glide feels even at every scale.
          val zoom = from.zoom * Math.pow((target.zoom / from.zoom).toDouble(), t.toDouble()).toFloat()
          camera.set(Viewport(from.x + (target.x - from.x) * t, from.y + (target.y - from.y) * t, zoom))
          afterCameraMove()
        }
        addListener(object : android.animation.AnimatorListenerAdapter() {
          override fun onAnimationEnd(animation: android.animation.Animator) = settleCamera()
        })
        start()
      }
  }

  private fun settleCamera() {
    host.onZoomChanged(camera.zoom)
    invalidate()
  }

  fun setZoom(zoom: Float) {
    camera.zoomAround(width / 2f, height / 2f, zoom / camera.zoom)
    settleCamera()
    afterCameraMove()
  }

  /** Runs a menu action (delete, undo, insert…) so that a failure shows a hint instead of crashing. */
  fun runSafely(action: NoteCanvasView.() -> Unit) {
    try {
      action()
    } catch (t: Throwable) {
      com.tali.notes.CrashLog.recovered(context, t)
      host.onHint(tr(R.string.that_didn_t_work_try_again))
      invalidate()
    }
  }

  fun visibleWorld(): Rect = camera.visibleWorld(width, height)

  /** Zooms out (or in, up to 150%) so everything in the notebook is on screen. */
  fun fitContent() {
    // A paged notebook shows the whole of the current page.
    val b = (if (pageCount != null) pageLayout.rect(currentPage()).outset(24f) else doc?.contentBounds()?.outset(40f)) ?: return
    if (width == 0 || height == 0) return
    val zoom = minOf(width / (b.width * density), height / (b.height * density)).coerceIn(Camera.MIN_ZOOM, 1.5f)
    val scale = zoom * density
    camera.set(Viewport(b.center.x - width / 2f / scale, b.center.y - height / 2f / scale, zoom))
    settleCamera()
    afterCameraMove()
  }

  companion object {
    private val DRAW_MODES =
      setOf(Mode.INK, Mode.LIVE_SHAPE, Mode.ERASE, Mode.LASSO, Mode.MOVE_SELECTION, Mode.SCALE_SELECTION, Mode.ROTATE_SELECTION, Mode.HANDLE_DRAG, Mode.SHAPE_CREATE, Mode.FILL_TAP, Mode.IGNORE, Mode.STRAIGHT, Mode.LASER)
    private const val HOLD_TOLERANCE_DP = 7f

    /** PDF points per world unit (dp), matching the PDF export so pages keep their real size. */
    private const val PT_PER_DP = 72f / 160f
    private const val PAGE_GAP = 48f

    /** Typed text: about 11 pt when printed on A4. */
    const val TEXT_SIZE = 26f

    /** How far into the next page outline she can scroll, and how far before letting go adds it (fractions of a page). */
    /** Space left under the last page at rest, how far it can be pulled, and how far adds a page (screen dp). */
    private const val REST_DP = 32f
    private const val MAX_PULL_DP = 210f
    private const val ADD_PULL_DP = 150f
    private const val PULL_RESISTANCE = 0.45f

    /** Room left around writing moved onto pages. */
    private const val PAGE_MARGIN = 60f
    /** One background thread for working out fills, shared by every notebook. */
    private val fillWorker = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "fill").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 } }

    /** Fill pictures are saved with this prefix, so the fill tool can recognise (and replace) them. */
    const val FILL_PREFIX = "fill-"

    /** Something copied in another app is offered by a hold for this long (an hour). */
    private const val TABLET_CLIP_MS = 60 * 60 * 1000L

    /** [fillPicture]'s answer when the area is nearly the whole shape it's in (that shape gets the fill). */
    private val WHOLE_SHAPE = Triple("", Rect(0f, 0f, 0f, 0f), emptySet<String>())
    /** How far from the tap a fill looks for the edges of its area, in world units. */
    private const val FILL_REACH = 1100f
    /** The largest mask side, in pixels. */
    private const val FILL_PIXELS = 1400f
    /** Gaps up to about this wide (world units, ~1 mm) in an outline still count as closed. */
    private const val FILL_BRIDGE = 3f
    private const val HANDLE_HIT_DP = 24f
    /** The screenshot lasso's largest picture, in pixels. */
    private const val SHOT_MAX_PIXELS = 16_000_000f
    /** How long the captured area flashes. */
    private const val SHOT_FLASH_MS = 260L
    /** How close (dp on screen) a shape comes to another before it locks on. */
    private const val SNAP_DP = 14f

    /** How close (dp on screen) sides line up from at a distance: softer than touching (middles lining up use [SNAP_DP]). */
    private const val ALIGN_DP = 8f
    /** At most this many things around the view are considered for snapping. */
    private const val MAX_SNAP_TARGETS = 250
    /** A shape smaller than this on screen (dp) shows no dots: zoomed in, they come back. */
    private const val TINY_SHAPE_DP = 32f
    /** The least a shape's dot reaches (dp), however small the shape. */
    private const val MIN_HANDLE_HIT_DP = 9f
    private const val BUBBLE_H = 24f
    /** How long the laser stays lit after it was last lifted, so several strokes can be drawn. */
    private const val LASER_IDLE_MS = 1000L
    /** How long the laser's strokes take to fade once it's been left. */
    private const val LASER_FADE_MS = 450L
    /** A straight highlighter line this close to level or upright snaps to it. */
    private const val STRAIGHT_SNAP_DEG = 4.0
    /** Free-form selecting picks up anything the path passes within this distance of (dp on screen). */
    private const val FREE_SELECT_DP = 10f
    /** A free-form path ending within this distance of its start (dp on screen) closes into a loop. */
    private const val LOOP_CLOSE_DP = 40f
    /** How far (dp, each way) the resize chip sits out from the selection's padded corner. */
    private const val RESIZE_CHIP_OFFSET_DP = 14f
    /** How far below the selection (or the lowest dot under its middle) the rotate handle hangs. */
    private const val ROTATE_DROP_DP = 30f

    /** How far (dp on screen) the Shapes tool has to be dragged before a shape appears; a tap draws nothing. */
    private const val SHAPE_START_DP = 12f
    /** How long the resize chip takes to come back after a move or a dot drag. */
    private const val CHIP_BACK_MS = 160L

    /** How long after an edit, with nothing else done, the selection menu comes back. */
    private const val MENU_BACK_MS = 450L
    /** Carrying something this close to an edge (dp) scrolls that way, after [EDGE_DWELL_MS]. */
    private const val EDGE_ZONE_DP = 100f
    private const val EDGE_DWELL_MS = 120L
    /** A carried voice memo: a deeper zone and a shorter wait (it's a small button, held by a fingertip). */
    private const val MEMO_EDGE_ZONE_DP = 140f
    private const val MEMO_EDGE_DWELL_MS = 60L
    /** The fastest edge scroll, deep in the zone (dp per second); it eases up from nothing. */
    private const val EDGE_MAX_DP_S = 900f
    /** How close a turn has to come to a 45° step to snap onto it (5°). */
    private const val SNAP_ROTATE_RAD = 0.087f
    /** A pen touch shorter than this that doesn't move is a tap. */
    private const val PEN_TAP_MS = 250L

    /** How long a pen or finger stays still on the page before Paste is offered (after hold-to-shape's 450 ms, which needs a stroke). */
    private const val PASTE_HOLD_MS = 600L
    /** How long a finger is held on a locked item to unlock it. */
    private const val LONG_PRESS_MS = 1500L
    /** The S Pen's side button, as different pens report it. */
    private const val PEN_BUTTONS = MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY
    /** A press of the S Pen button no longer than this counts towards a double or triple press. */
    private const val PEN_PRESS_MAX_MS = 350L
    /** How long to wait after a press for another before acting on the count. */
    private const val PEN_PRESS_GAP_MS = 320L
    /** The unlock padlock starts showing only after this, so a passing touch shows nothing. */
    private const val HOLD_RING_DELAY_MS = 500L
    /** The lock cue's fade-in; a completed hold starts the cue just after it (the padlock is already showing). */
    private const val CUE_APPEAR_MS = 200L
    /** The hold's padlock shows over the item, or this far above the fingertip when the finger covers it. */
    private const val HOLD_LIFT_DP = 64f
    /** The lock cue's timeline (ms): in by 200, the shackle snaps from 260 to 560, it leaves from 1050, gone at 1300. */
    private const val CUE_SHACKLE_FROM = 260L
    private const val CUE_SHACKLE_MS = 300f
    private const val CUE_LEAVE_FROM = 1050f
    private const val CUE_TOTAL = 1300f
    /** A touch wider than this (mm) is a palm or the side of a hand, not a fingertip. */
    private const val PALM_MM = 14f
    /** No unlocking while the S Pen touched or hovered this recently: her hand is resting to write. */
    private const val PEN_NEARBY_MS = 1500L
    private const val HIT_RADIUS_DP = 10f
    private const val MULTI_TAP_MS = 300L
    /** How long the view rests before PDF pages are drawn sharp for it. */
    private const val SHARP_WAIT_MS = 140L
  }
}

private fun Affine.toMatrix() = android.graphics.Matrix().apply { setValues(floatArrayOf(a, c, tx, b, d, ty, 0f, 0f, 1f)) }
