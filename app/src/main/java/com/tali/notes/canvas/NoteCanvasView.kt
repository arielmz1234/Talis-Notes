package com.tali.notes.canvas

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.InputDevice
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.widget.OverScroller
import android.widget.FrameLayout
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
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeItem
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.Tool
import com.tali.notes.model.TextItem
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.abs
import kotlin.math.PI
import com.tali.notes.geometry.Lasso

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
}

/** A text block being typed: [id] is null for a new one. Positions and sizes are in world units. */
data class TextEdit(val id: String?, val origin: Pt, val width: Float, val fontSize: Float, val color: Int, val text: String)

/** Selection summary in screen pixels, for placing the selection menu. */
data class SelectionInfo(val screenRect: RectF, val count: Int, val hasShape: Boolean, val hasImage: Boolean, val hasDrawing: Boolean = true, val rotatable: Boolean = false, val textId: String? = null)

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
    invalidate()
  }
  private val tiles = TileCache(itemRenderer)
  private val paper = PaperRenderer()
  private val predictor = MotionEventPredictor.newInstance(this)
  private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

  private var doc: Document? = null
  private val docListener: (Change) -> Unit = ::onDocChanged

  var settings = AppSettings()
  var tool = Tool.PEN
    set(value) {
      if (field != value) {
        field = value
        clearSelection()
        hoverPoint = null
        invalidate()
      }
    }

  var shapeKind = ShapeKind.LINE

  /** How far down the toolbars reach (dp), so jumps to a page land just below them. */
  var topClearance = 124f

  /** When set, the shape tool draws this saved shape instead of [shapeKind]. */
  var customShape: CustomShape? = null
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
    FINGER_PENDING,
    PAN,
    PINCH,

    /** The pen came down off the pages of a paged notebook: nothing to draw there. */
    IGNORE,
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
  private var liveShape: ShapeItem? = null

  // Eraser
  private var erasePrev = Pt.ZERO
  private val erasedOriginals = LinkedHashMap<String, CanvasItem>()
  private val erasePieces = LinkedHashMap<String, InkItem>()
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
  private var handleIndex = -1
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

  private var hoverPoint: Pt? = null

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
  }

  fun detach() {
    doc?.listeners?.remove(docListener)
    images.removeListener(onImageLoaded)
    StrokeBuilds.removeListener(onStrokesBuilt)
    doc = null
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    super.onSizeChanged(w, h, oldw, oldh)
    post {
      clampCamera()
      updatePage()
      invalidate()
    }
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    cameraAnimator?.cancel()
    stopFling()
    removeCallbacks(holdRunnable)
    detach()
  }

  private fun onDocChanged(c: Change) {
    if (pageCount == null && (c.added + c.removed).any { it is ImageItem && it.pdfPage }) refreshPages()
    c.removed.forEach { tiles.invalidate(it.bounds) }
    c.added.forEach { tiles.invalidate(it.bounds) }
    // Writing (or an undo) that reaches below the last page brings the pages it needs.
    val n = pageCount
    if (n != null && c.added.isNotEmpty()) {
      val need = Pages.needed(c.added.map { it.bounds }.reduce(Rect::union))
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

  override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
    val d = doc
    if (d != null) {
      try {
        val added =
          strokes.map { (id, s) ->
            val pen = strokePens.remove(id) ?: PenType.FINELINER
            // On dark paper the live stroke used the light version of the colour; store the real one.
            val real = strokeColors.remove(id)?.let { Brushes.inkColor(pen, it) }
            InkItem(d.newId(), d.nextZ(), pen, if (real != null && real != s.brush.colorIntArgb) s.copy(s.brush.copyWithColorIntArgb(real)) else s)
          }
        d.commit(Change(emptyList(), added))
      } catch (t: Throwable) {
        com.tali.notes.CrashLog.recovered(context, t)
      }
    }
    // Hand-off in the same frame the tiles pick the strokes up, so there's no flicker.
    inProgress.removeFinishedStrokes(strokes.keys)
    invalidate()
  }

  fun viewport(): Viewport = camera.viewport()

  // ---- Drawing ----

  private val guidePaint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      style = Paint.Style.STROKE
      color = 0x669B7BE0.toInt()
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
    val first = Pages.indexAt(view.top).coerceAtLeast(0)
    val last = Pages.indexAt(view.bottom).coerceAtMost(n - 1)
    pageShadow.color = deskColor
    pageShadow.setShadowLayer(6f * density, 0f, 2f * density, if (darkPaper || deskIsDark()) 0x66000000 else 0x2E3A2A70)
    for (i in first..last) {
      val r = Pages.rect(i)
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
    val pull = pullDp()
    if (pull < 24f) return
    val lastBottom = camera.toScreen(Pt(0f, lastPageBottom(n))).y
    val ready = pull >= ADD_PULL_DP
    val alpha = ((pull - 24f) / 40f).coerceIn(0f, 1f)
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
    canvas.drawText(if (ready) "Release to add a page" else "Drag for a new page", cx, cy + r + 18f * density, ghostText)
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
    liveShape?.let { itemRenderer.draw(canvas, it, camera.worldToScreen) }
    liveEdit?.let { itemRenderer.draw(canvas, it, camera.worldToScreen) }
    shapePreview?.let { itemRenderer.draw(canvas, it, camera.worldToScreen) }
    if (isDraggingSelection() && selection.isNotEmpty()) {
      canvas.save()
      canvas.concat(dragTransform.toMatrix())
      itemRenderer.drawAll(canvas, selection, camera.worldToScreen)
      canvas.restore()
    }
    canvas.restore()

    drawLasso(canvas)
    drawSelection(canvas)
    drawShapeHandles(canvas)
    drawLineReadout(canvas)
    drawEraserCursor(canvas)
  }

  private fun drawLasso(canvas: Canvas) {
    if (mode != Mode.LASSO || lassoPoints.size < 2) return
    lassoPath.reset()
    lassoPoints.forEachIndexed { i, p ->
      val s = camera.toScreen(p)
      if (i == 0) lassoPath.moveTo(s.x, s.y) else lassoPath.lineTo(s.x, s.y)
    }
    lassoPath.close()
    canvas.drawPath(lassoPath, lassoFill)
    canvas.drawPath(lassoPath, lassoPaint)
  }

  private fun isDraggingSelection() = mode == Mode.MOVE_SELECTION || mode == Mode.SCALE_SELECTION || mode == Mode.ROTATE_SELECTION

  /** Pictures can't be turned (they stay upright), so the rotate handle only shows for drawings. */
  private fun canRotateSelection() = selection.isNotEmpty() && selection.none { it is ImageItem || it is TextItem }

  /** The rotate handle hangs below the middle of the selection (the menu sits above it). */
  private fun rotateHandle(r: RectF) = Pt(r.centerX(), r.bottom + 30f * density)

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
    if (activeShapeId != null && selection.size == 1 && mode != Mode.ROTATE_SELECTION) {
      // The shape's own handles are enough; just add the rotate handle.
      drawRotateHandle(canvas, screenRect(b, Affine.IDENTITY).apply { inset(-pad, -pad) })
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
    drawHandle(canvas, r.right, r.bottom, 7f)
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
   * While a straight line is being drawn or adjusted: its angle (counter-clockwise from the
   * horizontal, as in class) and its length at print size, next to the end that's moving.
   */
  private fun drawLineReadout(canvas: Canvas) {
    val (line, movingEnd) =
      when (mode) {
        Mode.LIVE_SHAPE -> (liveShape?.geometry as? LineShape)?.let { it to it.b }
        Mode.SHAPE_CREATE -> (shapePreview?.geometry as? LineShape)?.let { it to it.b }
        Mode.HANDLE_DRAG -> (liveEdit?.geometry as? LineShape)?.let { it to if (handleIndex == 0) it.a else it.b }
        else -> null
      } ?: return
    val v = line.b - line.a
    if (v.length() < 1f) return
    var degrees = Math.round(-Math.toDegrees(kotlin.math.atan2(v.y, v.x).toDouble())).toInt()
    if (degrees < 0) degrees += 180
    if (degrees == 180) degrees = 0
    // World units are dp; exported PDFs print them at 72/160 pt each.
    val cm = line.length * (72f / 160f) / 72f * 2.54f
    val label = "$degrees°  ·  ${String.format(java.util.Locale.US, "%.1f", cm)} cm"
    val s = camera.toScreen(movingEnd)
    angleText.textSize = 12.5f * density
    angleText.color = 0xFFFFFFFF.toInt()
    angleBubble.color = handleColor
    val w = angleText.measureText(label) + 18f * density
    val h = 24f * density
    val cx = (s.x + 22f * density + w / 2).coerceIn(w / 2 + 4f, width - w / 2 - 4f)
    val cy = (s.y - 26f * density).coerceIn(h / 2 + 4f, height - h / 2 - 4f)
    canvas.drawRoundRect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, h / 2, h / 2, angleBubble)
    canvas.drawText(label, cx, cy + 4.5f * density, angleText)
  }

  private fun drawShapeHandles(canvas: Canvas) {
    if (isDraggingSelection()) return
    val shape = currentActiveShape() ?: return
    shape.geometry.handles().forEach { p ->
      val s = camera.toScreen(p)
      drawHandle(canvas, s.x, s.y, 6.5f)
    }
  }

  private fun drawHandle(canvas: Canvas, x: Float, y: Float, radiusDp: Float) {
    canvas.drawCircle(x, y, radiusDp * density, handleFill)
    canvas.drawCircle(x, y, radiusDp * density, handleStroke)
  }

  private fun drawEraserCursor(canvas: Canvas) {
    val showing = mode == Mode.ERASE || (hoverPoint != null && (tool == Tool.ERASER || eraserOverride))
    if (!showing) return
    val p = if (mode == Mode.ERASE) erasePrev else hoverPoint ?: return
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

  private fun drawsWith(toolType: Int) = isStylus(toolType) || toolType == MotionEvent.TOOL_TYPE_MOUSE || (toolType == MotionEvent.TOOL_TYPE_FINGER && fingerDraws())

  private fun isDrawingMode() = mode in DRAW_MODES

  private fun world(e: MotionEvent, index: Int) = camera.toWorld(e.getX(index), e.getY(index))

  @SuppressLint("ClickableViewAccessibility")
  override fun dispatchTouchEvent(e: MotionEvent): Boolean {
    if (doc == null) return true
    try {
      handleTouch(e)
    } catch (t: Throwable) {
      // One bad gesture must never close the app or lose the notebook; drop the gesture instead.
      com.tali.notes.CrashLog.recovered(context, t)
      runCatching { abortDrawing() }
      mode = Mode.NONE
      host.onHint("That didn't work. Try again.")
    }
    return true
  }

  private fun handleTouch(e: MotionEvent) {
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
          fingerDown(e)
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
        if (isDrawingMode()) abortDrawing()
        mode = Mode.NONE
      }
    }
  }

  override fun dispatchGenericMotionEvent(e: MotionEvent): Boolean {
    when (e.actionMasked) {
      MotionEvent.ACTION_HOVER_ENTER,
      MotionEvent.ACTION_HOVER_MOVE -> {
        hoverPoint = camera.toWorld(e.x, e.y)
        eraserOverride = e.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER || e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0
        if (tool == Tool.ERASER || eraserOverride) invalidate()
        return true
      }
      MotionEvent.ACTION_HOVER_EXIT -> {
        hoverPoint = null
        invalidate()
        return true
      }
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
    drawPointer = e.getPointerId(idx)
    drawIsFinger = e.getToolType(idx) == MotionEvent.TOOL_TYPE_FINGER
    requestUnbufferedDispatch(e)
    val p = world(e, idx)
    val screen = Pt(e.getX(idx), e.getY(idx))
    eraserOverride = e.getToolType(idx) == MotionEvent.TOOL_TYPE_ERASER || e.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY != 0

    // 1. Handles of the shape being edited win over everything.
    currentActiveShape()?.let { shape ->
      val hit = shape.geometry.handles().indexOfFirst { h -> camera.toScreen(h).dist(screen) < HANDLE_HIT_DP * density }
      if (hit >= 0) {
        handleIndex = hit
        liveEdit = shape
        tiles.hidden = setOf(shape.id)
        mode = Mode.HANDLE_DRAG
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
      val nearCorner = hypot(screen.x - r.right, screen.y - r.bottom) < HANDLE_HIT_DP * density
      if (nearCorner || r.contains(screen.x, screen.y)) {
        dragStart = p
        dragTransform = Affine.IDENTITY
        tiles.hidden = selection.map { it.id }.toSet()
        mode = if (nearCorner) Mode.SCALE_SELECTION else Mode.MOVE_SELECTION
        host.onSelectionChanged(null)
        invalidate()
        return
      }
    }
    if (selection.isNotEmpty() || activeShapeId != null) clearSelection()

    val effective = if (eraserOverride) Tool.ERASER else tool
    val n = pageCount
    if (n != null && (effective == Tool.PEN || effective == Tool.SHAPE) && !Pages.contains(n, p.x, p.y)) {
      mode = Mode.IGNORE
      return
    }
    when (effective) {
      Tool.PEN -> startInk(e, idx, p, d)
      Tool.ERASER -> startErase(p)
      Tool.LASSO -> startLasso(p)
      Tool.SHAPE -> {
        shapeStart = p
        shapePreview = null
        mode = Mode.SHAPE_CREATE
      }
    }
  }

  private fun startInk(e: MotionEvent, idx: Int, p: Pt, d: Document) {
    val slot = settings.activeSlot
    val brush = Brushes.brush(slot.type, if (darkPaper) Brushes.onDarkPaper(slot.color) else slot.color, slot.size)
    predictor.record(e)
    val id = inProgress.startStroke(e, e.getPointerId(idx), brush, camera.screenToWorld)
    strokeId = id
    strokePens[id] = slot.type
    if (darkPaper) strokeColors[id] = slot.color
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
    when (mode) {
      Mode.INK -> {
        predictor.record(e)
        val prediction = predictor.predict()
        strokeId?.let { inProgress.addToStroke(e, drawPointer, it, prediction) }
        prediction?.recycle()
        for (h in 0 until e.historySize) addInkPoint(camera.toWorld(e.getHistoricalX(idx, h), e.getHistoricalY(idx, h)))
        addInkPoint(world(e, idx))
      }
      Mode.LIVE_SHAPE -> {
        val shape = liveShape ?: return
        val g = shape.geometry
        // While still holding, a snapped line follows the pen so its length and angle can be set.
        if (g is LineShape && !g.arrowEnd) {
          liveShape = shape.withGeometry(ShapeRecognizer.snappedLine(g.a, world(e, idx)))
          invalidate()
        }
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
        val p = world(e, idx)
        dragTransform = Affine.translate(p.x - dragStart.x, p.y - dragStart.y)
        invalidate()
      }
      Mode.ROTATE_SELECTION -> {
        val start = (dragStart - rotatePivot).angle()
        var angle = (world(e, idx) - rotatePivot).angle() - start
        // Snap to 15° steps (and straight) when close, so angles like 30° and 45° are exact.
        val step = (PI / 12).toFloat()
        val snapped = Math.round(angle / step) * step
        if (abs(angle - snapped) < 0.06f) angle = snapped
        rotateAngle = angle
        dragTransform = Affine.rotateAround(rotatePivot, angle)
        invalidate()
      }
      Mode.SCALE_SELECTION -> {
        val b = selectionBounds ?: return
        val anchor = Pt(b.left, b.top)
        val corner = Pt(b.right, b.bottom)
        val s = (world(e, idx).dist(anchor) / max(1f, dragStart.dist(anchor))).coerceIn(0.1f, 10f)
        dragTransform = Affine.scaleAround(anchor, s)
        if (corner.dist(anchor) * s < 4f) dragTransform = Affine.IDENTITY
        invalidate()
      }
      Mode.HANDLE_DRAG -> {
        val shape = liveEdit ?: return
        val base = doc?.get(shape.id) as? ShapeItem ?: return
        var g = base.geometry.withHandle(handleIndex, world(e, idx))
        // A line end being dragged snaps to the same tidy angles as a freshly drawn line.
        if (g is LineShape) g = if (handleIndex == 1) g.copy(b = ShapeRecognizer.snappedLine(g.a, g.b).b) else g.copy(a = ShapeRecognizer.snappedLine(g.b, g.a).b)
        liveEdit = base.withGeometry(g)
        invalidate()
      }
      Mode.SHAPE_CREATE -> {
        val p = world(e, idx)
        if (camera.toScreen(p).dist(camera.toScreen(shapeStart)) < touchSlop) return
        shapePreview = buildShape(shapeStart, p)
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

  private fun snapToShape() {
    if (mode != Mode.INK) return
    val d = doc ?: return
    val id = strokeId ?: return
    if (Geo.pathLength(strokePoints) * camera.scale < 24f * density) return
    val geometry = ShapeRecognizer.recognize(strokePoints, settings.customShapes) ?: return
    val slot = settings.activeSlot
    val pen = strokePens[id] ?: slot.type
    inProgress.cancelStroke(id, null)
    strokePens.remove(id)
    strokeColors.remove(id)
    strokeId = null
    liveShape = ShapeItem(d.newId(), d.nextZ(), pen, slot.color, slot.size, geometry)
    mode = Mode.LIVE_SHAPE
    performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    invalidate()
  }

  private fun buildShape(p0: Pt, p1: Pt): ShapeItem {
    val slot = settings.shapeSlot
    var geometry = customShape?.fitInto(p0, p1) ?: shapeKind.create(p0, p1)
    if (geometry is LineShape) {
      val snapped = ShapeRecognizer.snappedLine(geometry.a, geometry.b)
      geometry = geometry.copy(b = snapped.b)
    }
    return ShapeItem("preview", 0, slot.type, slot.color, slot.size, geometry)
  }

  private fun endDraw(e: MotionEvent) {
    val d = doc
    val idx = e.findPointerIndex(drawPointer)
    removeCallbacks(holdRunnable)
    when (mode) {
      Mode.INK -> {
        strokeId?.let { inProgress.finishStroke(e, drawPointer, it) }
        strokeId = null
      }
      Mode.LIVE_SHAPE -> {
        val shape = liveShape
        if (shape != null && d != null) {
          d.commit(Change(emptyList(), listOf(shape)))
          // No handles yet: the next line often starts right at this one's end, and should draw,
          // not grab it. Tapping the shape (or the lasso) brings up its dots.
          host.onTip("shape-dots", "Tap a shape with your finger to adjust its ends")
        }
        liveShape = null
      }
      Mode.ERASE -> finishErase()
      Mode.LASSO -> finishLasso(if (idx >= 0) world(e, idx) else null)
      Mode.MOVE_SELECTION,
      Mode.SCALE_SELECTION,
      Mode.ROTATE_SELECTION -> finishSelectionDrag()
      Mode.HANDLE_DRAG -> {
        val edited = liveEdit
        val original = edited?.let { d?.get(it.id) }
        if (edited != null && original != null && d != null) d.commit(Change(listOf(original), listOf(edited)))
        liveEdit = null
        tiles.hidden = emptySet()
        if (selection.size == 1) setSelection(listOfNotNull(d?.get(selection[0].id)))
      }
      Mode.SHAPE_CREATE -> {
        if (d != null) {
          val preview = shapePreview ?: buildShape(shapeStart - Pt(60f, 40f), shapeStart + Pt(60f, 40f))
          val shape = ShapeItem(d.newId(), d.nextZ(), preview.pen, preview.color, preview.size, preview.geometry)
          d.commit(Change(emptyList(), listOf(shape)))
        }
        shapePreview = null
      }
      else -> Unit
    }
    mode = Mode.NONE
    drawPointer = -1
    invalidate()
  }

  private fun abortDrawing() {
    removeCallbacks(holdRunnable)
    strokeId?.let { inProgress.cancelStroke(it, null) }
    strokeId = null
    liveShape = null
    liveEdit = null
    shapePreview = null
    lassoPoints.clear()
    erasedOriginals.clear()
    erasePieces.clear()
    dragTransform = Affine.IDENTITY
    tiles.hidden = emptySet()
    if (selection.isNotEmpty()) setSelection(selection)
    mode = Mode.NONE
    drawPointer = -1
    invalidate()
  }

  // ---- Eraser ----

  private fun startErase(p: Pt) {
    erasedOriginals.clear()
    erasePieces.clear()
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
    val candidates = d.intersecting(region).filter { it.id !in erasedOriginals && settings.eraserHits(it.pen) } + erasePieces.values.filter { it.bounds.intersects(region) }
    for (item in candidates) {
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
      for ((ink, parts) in splits) {
        if (parts == null) {
          erasePieces[ink.id] = ink
        } else {
          parts.forEach { s ->
            val id = d.newId()
            erasePieces[id] = InkItem(id, ink.z, ink.pen, s)
          }
        }
      }
    }
    tiles.hidden = erasedOriginals.keys.toSet()
  }

  private fun finishErase() {
    val d = doc ?: return
    val change = Change(erasedOriginals.values.toList(), erasePieces.values.toList())
    erasePieces.clear()
    erasedOriginals.clear()
    d.commit(change)
    tiles.hidden = emptySet()
  }

  private fun strokesOf(item: CanvasItem): List<Stroke> =
    when (item) {
      is InkItem -> listOf(item.stroke)
      is ShapeItem -> item.strokes
      is ImageItem, is TextItem -> emptyList()
    }

  private fun parallelogram(a: Pt, b: Pt, padding: Float): Parallelogram {
    val end = if (a.dist(b) < 0.01f) Pt(a.x + 0.01f, a.y) else b
    return ImmutableParallelogram.fromSegmentAndPadding(ImmutableSegment(ImmutableVec(a.x, a.y), ImmutableVec(end.x, end.y)), padding)
  }

  // ---- Lasso and selection ----

  private fun startLasso(p: Pt) {
    lassoPoints.clear()
    lassoPoints += p
    mode = Mode.LASSO
  }

  private fun finishLasso(end: Pt?) {
    val d = doc ?: return
    val pts = lassoPoints.toList()
    lassoPoints.clear()
    val lengthPx = Geo.pathLength(pts) * camera.scale
    if (lengthPx < 12f * density) {
      // A tap with the lasso picks up whatever is under it.
      hitTest(end ?: pts.firstOrNull() ?: return, filter = settings::lassoHits)?.let { selectSingle(it) }
      return
    }
    val area = Rect.around(pts) ?: return
    val picked = d.all.filter { item -> settings.lassoHits(item.pen) && item.bounds.intersects(area) && lassoTouches(item, pts) }
    setSelection(picked)
    if (picked.isNotEmpty()) host.onTip("lasso", if (canRotateSelection()) "Drag to move · corner dot to resize · bottom dot to rotate" else "Drag to move · corner dot to resize")
    if (picked.size == 1 && picked[0] is ShapeItem) activeShapeId = picked[0].id
  }

  /**
   * Whether the lasso loop [poly] picks up [item]: anything it touches (see [Lasso]). Imported PDF
   * pages still need to be mostly inside, so lassoing writing on a slide doesn't take the slide.
   */
  private fun lassoTouches(item: CanvasItem, poly: List<Pt>): Boolean =
    when (item) {
      is InkItem -> Lasso.touchesPath(StrokeFactory.points(item.stroke), poly)
      is ShapeItem -> item.geometry.paths(item.size).any { Lasso.touchesPath(it, poly) }
      is ImageItem -> if (item.pdfPage) insideFraction(item, poly) >= 0.5f else Lasso.touchesRect(item.rect, poly)
      is TextItem -> Lasso.touchesRect(item.bounds, poly)
    }

  private fun insideFraction(item: CanvasItem, poly: List<Pt>): Float {
    val samples: List<Pt> =
      when (item) {
        is InkItem -> StrokeFactory.points(item.stroke).let { pts -> if (pts.size > 40) pts.filterIndexed { i, _ -> i % (pts.size / 40) == 0 } else pts }
        is ShapeItem -> item.geometry.paths(item.size).flatten()
        is ImageItem -> item.rect.let { r -> listOf(Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom), r.center) }
        is TextItem -> item.bounds.let { r -> listOf(Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom), r.center) }
      }
    if (samples.isEmpty()) return 0f
    return samples.count { Geo.pointInPolygon(it, poly) }.toFloat() / samples.size
  }

  /**
   * The item under [p]. Writing wins over pictures, since pictures are drawn underneath it; imported
   * PDF pages count only when [pages] is true, so tapping a slide doesn't pick up the whole slide.
   */
  private fun hitTest(p: Pt, pages: Boolean = true, filter: (PenType?) -> Boolean): CanvasItem? {
    val d = doc ?: return null
    val r = HIT_RADIUS_DP / camera.zoom
    val para = parallelogram(p, p, r)
    val near = d.intersecting(Rect(p.x - r, p.y - r, p.x + r, p.y + r)).asReversed().filter { filter(it.pen) }
    return near.firstOrNull { item -> item !is ImageItem && item !is TextItem && strokesOf(item).any { para.intersects(it.shape, AffineTransform.IDENTITY) } }
      ?: near.firstOrNull { item -> item is TextItem && item.bounds.contains(p) }
      ?: near.firstOrNull { item -> item is ImageItem && (pages || !item.pdfPage) && item.rect.contains(p) }
  }

  private fun selectSingle(item: CanvasItem) {
    setSelection(listOf(item))
    activeShapeId = (item as? ShapeItem)?.id
    invalidate()
  }

  private fun setSelection(items: List<CanvasItem>) {
    selection = items
    selectionBounds = items.map { it.bounds }.reduceOrNull(Rect::union)
    publishSelection()
    invalidate()
  }

  private fun publishSelection() {
    val b = selectionBounds
    if (b == null || selection.isEmpty()) {
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
      )
    )
  }

  fun clearSelection() {
    val had = selection.isNotEmpty() || activeShapeId != null
    selection = emptyList()
    selectionBounds = null
    activeShapeId = null
    if (had) host.onSelectionChanged(null)
    invalidate()
  }

  private fun finishSelectionDrag() {
    val d = doc ?: return
    val t = dragTransform
    tiles.hidden = emptySet()
    dragTransform = Affine.IDENTITY
    if (t.isIdentity) {
      publishSelection()
      return
    }
    val moved = selection.map { it.transformed(t) }
    d.commit(Change(selection, moved))
    setSelection(moved)
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

  fun copySelection() {
    if (selection.isEmpty()) return
    Clipboard.items = selection
    host.onHint(if (selection.size == 1) "Copied \u2014 paste it from \u22EF in any notebook" else "Copied ${selection.size} items \u2014 paste them from \u22EF in any notebook")
  }

  /** Pastes the clipboard in the middle of the screen, selected so it can be moved into place. */
  fun paste() {
    val d = doc ?: return
    val items = Clipboard.items
    val bounds = items.map { it.bounds }.reduceOrNull(Rect::union) ?: return
    val c = camera.visibleWorld(width, height).center
    val move = Affine.translate(c.x - bounds.center.x, c.y - bounds.center.y)
    val copies = items.sortedBy { it.z }.map { it.transformed(move, d.newId(), d.nextZ()) }
    d.commit(Change(emptyList(), copies))
    setSelection(copies)
  }

  fun duplicateSelection() {
    val d = doc ?: return
    val offset = Affine.translate(24f, 24f)
    val copies = selection.map { it.transformed(offset, d.newId(), d.nextZ()) }
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
      host.onHint(if (toFront) "Already in front" else "Already at the back")
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
    host.onHint(if (toFront) "Brought to the front" else "Sent to the back")
  }

  fun recolorSelection(color: Int) {
    val d = doc ?: return
    val recolored =
      selection.map {
        when (it) {
          is InkItem -> it.recolored(color)
          is ShapeItem -> it.recolored(color)
          is TextItem -> it.recolored(color)
          is ImageItem -> it
        }
      }
    d.commit(Change(selection, recolored))
    setSelection(recolored)
  }

  /** Puts a picture in the middle of the current view, sized to fit comfortably. */
  fun insertImage(file: String, bitmap: android.graphics.Bitmap) {
    val d = doc ?: return
    val view = camera.visibleWorld(width, height)
    val maxW = view.width * 0.6f
    val maxH = view.height * 0.6f
    val s = minOf(maxW / bitmap.width, maxH / bitmap.height, 1f / density * 2f)
    val w = bitmap.width * s
    val h = bitmap.height * s
    val c = view.center
    images.put(file, bitmap)
    val item = ImageItem(d.newId(), d.nextZ(), file, Rect(c.x - w / 2, c.y - h / 2, c.x + w / 2, c.y + h / 2))
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
    val start = if (d.intersecting(Pages.rect(n - 1)).isEmpty()) n - 1 else n
    val items =
      pdf.mapIndexed { i, p ->
        val r = Pages.rect(start + i)
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

  // ---- Typed text ----

  /** Where a world point is on screen, in pixels (for the text editor laid over the page). */
  fun screenOf(p: Pt): Pt = camera.toScreen(p)

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
        val r = Pages.rect(currentPage().coerceIn(0, n - 1))
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

  /** Opens an existing text block for typing; it's hidden here while the editor shows it. */
  fun editText(id: String) {
    val item = doc?.get(id) as? TextItem ?: return
    clearSelection()
    tiles.hidden = setOf(id)
    showForTyping(item.origin.y)
    host.onEditText(TextEdit(id, item.origin, item.width, item.fontSize, item.color, item.text))
    invalidate()
  }

  /** Saves what was typed: adds, changes, or (when emptied) removes the block, in one undo step. */
  fun finishText(edit: TextEdit, text: String) {
    val d = doc ?: return
    tiles.hidden = emptySet()
    val old = edit.id?.let { d[it] as? TextItem }
    val trimmed = text.trimEnd()
    when {
      old == null && trimmed.isNotBlank() -> d.commit(Change(emptyList(), listOf(TextItem(d.newId(), d.nextZ(), trimmed, edit.origin, edit.width, edit.fontSize, edit.color))))
      old != null && trimmed.isBlank() -> d.commit(Change(listOf(old), emptyList()))
      old != null && (old.text != trimmed || old.fontSize != edit.fontSize || old.color != edit.color) ->
        d.commit(Change(listOf(old), listOf(TextItem(old.id, old.z, trimmed, old.origin, old.width, edit.fontSize, edit.color))))
    }
    host.onEditText(null)
    invalidate()
  }

  /** Scrolls so the line being typed sits comfortably below the toolbars, above the keyboard. */
  private fun showForTyping(top: Float) {
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

  private fun fingerDown(e: MotionEvent) {
    if (e.actionMasked == MotionEvent.ACTION_DOWN) {
      velocity?.recycle()
      velocity = VelocityTracker.obtain().also { it.addMovement(e) }
      zoomAtDown = camera.zoom
      // Only a pull that starts at the end of the notebook can open space for a new page; a
      // fast scroll down just stops there.
      pulling = pageCount != null && pullDp() >= REST_DP - 4f
      fingerStart = Pt(e.x, e.y)
      fingerDownTime = SystemClock.uptimeMillis()
      maxPointers = 1
      multiTapMoved = false
      mode = Mode.FINGER_PENDING
    } else {
      maxPointers = max(maxPointers, e.pointerCount)
      startPinch(e)
    }
  }

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
    when (mode) {
      Mode.FINGER_PENDING -> {
        if (hypot(e.x - fingerStart.x, e.y - fingerStart.y) > touchSlop) {
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
      mode == Mode.FINGER_PENDING && maxPointers == 1 -> tapAt(camera.toWorld(e.x, e.y))
      maxPointers == 2 && quick && !multiTapMoved && settings.smartUndo -> undo()
      maxPointers == 3 && quick && !multiTapMoved && settings.smartUndo -> redo()
    }
    if (mode == Mode.PAN || mode == Mode.PINCH) {
      settleCamera()
      pulling = false
      // Pulled past the last page: add one or spring back. Otherwise a one-finger flick keeps gliding.
      // One finger or two both glide, unless the fingers were zooming.
      val zoomed = kotlin.math.abs(camera.zoom - zoomAtDown) > zoomAtDown * 0.04f
      if (!finishPull() && maxPointers <= 2 && !zoomed) {
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
    val item = hitTest(p, pages = false) { true }
    if (item == null) clearSelection() else selectSingle(item)
  }

  fun undo() {
    clearSelection()
    doc?.undo()
  }

  fun redo() {
    clearSelection()
    doc?.redo()
  }

  private fun afterCameraMove() {
    clampCamera()
    host.onCameraMoved()
    if (selection.isNotEmpty()) publishSelection()
    updatePage()
    invalidate()
  }

  // ---- Imported PDF pages: which one is showing, and jumping between them ----

  private var pages: List<Rect> = emptyList()
  private var pageIndex = -2

  private fun refreshPages() {
    val n = pageCount
    pages = if (n != null) Pages.all(n) else doc?.all?.filter { it is ImageItem && it.pdfPage }?.map { (it as ImageItem).rect }?.sortedBy { it.top }.orEmpty()
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
      if (Pages.WIDTH + 2 * margin <= viewW) Pages.WIDTH / 2f - viewW / 2f
      else camera.x.coerceIn(-margin, Pages.WIDTH + margin - viewW)
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
    return if (pullDp() > REST_DP) dy * PULL_RESISTANCE else dy
  }

  /** Fingers are on the page and started at its end (so it may be pulled past the last page). */
  private var pulling = false

  private fun lastPageBottom(n: Int) = (n - 1) * Pages.PITCH + Pages.HEIGHT

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
    val pull = pullDp()
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
    val zoom = (width * 0.94f / (Pages.WIDTH * density)).coerceIn(Camera.MIN_ZOOM, 1.5f)
    val scale = zoom * density
    camera.set(Viewport(Pages.WIDTH / 2f - width / 2f / scale, Pages.rect(index).top - topClearance / zoom, zoom))
    settleCamera()
    afterCameraMove()
  }

  /** The page under the upper-middle of the screen. */
  fun currentPage(): Int = pageIndex.coerceAtLeast(0)

  /** Adds an empty page right after page [index], moving everything below it down a page. */
  fun insertPageAfter(index: Int) {
    val d = doc ?: return
    val n = pageCount ?: return
    val below = d.all.filter { it.bounds.center.y > Pages.rect(index).bottom }
    val shift = Affine.translate(0f, Pages.PITCH)
    setPages(n + 1)
    if (below.isNotEmpty()) d.commit(Change(below, below.map { it.transformed(shift) }))
    goToPage(index + 1)
  }

  /**
   * Removes page [index] with everything on it, and moves the pages below up. The writing comes
   * back with undo; the last page left is only cleared.
   */
  fun deletePage(index: Int) {
    val d = doc ?: return
    val n = pageCount ?: return
    val top = index * Pages.PITCH
    val on = d.all.filter { it.bounds.center.y in top..<top + Pages.PITCH }
    if (n == 1) {
      if (on.isNotEmpty()) d.commit(Change(on, emptyList()))
      return
    }
    val below = d.all.filter { it.bounds.center.y >= top + Pages.PITCH }
    val shift = Affine.translate(0f, -Pages.PITCH)
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
    if (content != null && (content.left < 0f || content.right > Pages.WIDTH || content.top < 0f)) {
      val dx =
        if (content.width <= Pages.WIDTH - 2 * PAGE_MARGIN) (Pages.WIDTH - content.width) / 2f - content.left
        else PAGE_MARGIN - content.left
      val dy = if (content.top < PAGE_MARGIN) PAGE_MARGIN - content.top else 0f
      val all = d.all.toList()
      val shift = Affine.translate(dx, dy)
      d.commit(Change(all, all.map { it.transformed(shift) }))
    }
    setPages(Pages.needed(d.contentBounds()))
    showPageWidth(currentPage().coerceAtMost((pageCount ?: 1) - 1))
  }

  /** Glides to a page, fitting its width to the screen with the top just under the toolbar. */
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
      host.onHint("That didn't work. Try again.")
      invalidate()
    }
  }

  fun visibleWorld(): Rect = camera.visibleWorld(width, height)

  /** Zooms out (or in, up to 150%) so everything in the notebook is on screen. */
  fun fitContent() {
    // A paged notebook shows the whole of the current page.
    val b = (if (pageCount != null) Pages.rect(currentPage()).outset(24f) else doc?.contentBounds()?.outset(40f)) ?: return
    if (width == 0 || height == 0) return
    val zoom = minOf(width / (b.width * density), height / (b.height * density)).coerceIn(Camera.MIN_ZOOM, 1.5f)
    val scale = zoom * density
    camera.set(Viewport(b.center.x - width / 2f / scale, b.center.y - height / 2f / scale, zoom))
    settleCamera()
    afterCameraMove()
  }

  companion object {
    private val DRAW_MODES =
      setOf(Mode.INK, Mode.LIVE_SHAPE, Mode.ERASE, Mode.LASSO, Mode.MOVE_SELECTION, Mode.SCALE_SELECTION, Mode.ROTATE_SELECTION, Mode.HANDLE_DRAG, Mode.SHAPE_CREATE, Mode.IGNORE)
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
    private const val HANDLE_HIT_DP = 24f
    private const val HIT_RADIUS_DP = 10f
    private const val MULTI_TAP_MS = 300L
  }
}

private fun Affine.toMatrix() = android.graphics.Matrix().apply { setValues(floatArrayOf(a, c, tx, b, d, ty, 0f, 0f, 1f)) }
