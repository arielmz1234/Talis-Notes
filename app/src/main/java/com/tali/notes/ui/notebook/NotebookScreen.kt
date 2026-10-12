package com.tali.notes.ui.notebook

import com.tali.notes.ui.components.detailRes
import com.tali.notes.ui.components.nameRes
import com.tali.notes.tr
import androidx.compose.ui.res.stringResource
import com.tali.notes.R
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.outlined.Fullscreen
import com.tali.notes.ui.components.TaliIcons
import com.tali.notes.ui.components.BuiltInPaperDialog
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerticalAlignCenter
import androidx.compose.material.icons.outlined.LockOpen
import com.tali.notes.model.LineStyle
import com.tali.notes.ui.components.rememberSlidingSelection
import com.tali.notes.ui.components.colorRing
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.mutableFloatStateOf
import com.tali.notes.ui.components.StepSlider
import android.app.Application
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.outlined.LineWeight
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material.icons.outlined.Description
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.ContentPaste
import com.tali.notes.canvas.Clipboard
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tali.notes.canvas.CanvasHost
import com.tali.notes.canvas.NoteCanvasView
import com.tali.notes.canvas.SelectionInfo
import com.tali.notes.export.PdfLayout
import com.tali.notes.export.PdfQuality
import com.tali.notes.ui.components.NewPaperDialog
import com.tali.notes.ui.components.PaperPicker
import androidx.compose.material.icons.outlined.AllInclusive
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.Tool
import com.tali.notes.theme.Tali
import com.tali.notes.ui.library.CardBody
import com.tali.notes.theme.TaliColorsScope
import com.tali.notes.theme.notebookColors
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DarkMode
import com.tali.notes.ui.components.CloudCrown
import com.tali.notes.ui.components.ColorDot
import com.tali.notes.ui.components.FloatingBar
import com.tali.notes.ui.components.Panel
import com.tali.notes.ui.components.PaperSwatch
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.Segmented
import com.tali.notes.ui.components.SectionLabel
import com.tali.notes.ui.components.Sparkle
import com.tali.notes.ui.components.cloudSurface
import com.tali.notes.ui.components.ToolButton
import com.tali.notes.ui.library.DialogCard
import com.tali.notes.model.ShapeGeometry
import com.tali.notes.ui.components.GeometryIcon
import androidx.compose.material.icons.outlined.AutoAwesome
import com.tali.notes.ui.library.TitleField
import androidx.compose.ui.text.input.TextFieldValue
import com.tali.notes.ui.library.RenameDialog
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.Save
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.FlipToFront
import androidx.compose.material.icons.outlined.FlipToBack
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat

private class CanvasHolder {
  // State, so what lays itself over the canvas (the voice memos) appears once the canvas exists.
  var view: NoteCanvasView? by mutableStateOf(null)
}

@Composable
fun NotebookScreen(notebookId: String, onBack: () -> Unit, onSwitch: (String) -> Unit = {}) {
  val app = LocalContext.current.applicationContext as Application
  val vm: NotebookViewModel = viewModel(key = notebookId) { NotebookViewModel(app, notebookId) }
  val meta by vm.meta.collectAsState()
  val systemDark = Tali.colors.isDark
  val darkPaper = meta?.dark == true
  // The status bar sits over the paper, or over the desk around the pages: light icons when that's dark.
  val darkTop = darkPaper || (systemDark && vm.pageCount != null)
  val view = LocalView.current
  // Her choice to hide the system bars (the clock bar at the top and the navigation bar) holds in
  // every notebook; they come back in the library.
  val hideNavBar = vm.settings.collectAsState().value.hideNavBar
  val navOwner = LocalLifecycleOwner.current
  DisposableEffect(hideNavBar, navOwner) {
    val window = (view.context as? android.app.Activity)?.window
    val bars = window?.let { WindowCompat.getInsetsController(it, view) }
    val nav = androidx.core.view.WindowInsetsCompat.Type.systemBars()
    fun apply() {
      if (hideNavBar) {
        bars?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(nav)
      } else {
        bars?.show(nav)
      }
    }
    apply()
    // Coming back to the app can bring the bar back; hide it again. Leaving the app keeps a
    // recording going on (it's saved as a memo) and pauses one playing.
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) apply()
      // Leaving the app (or the tablet switching off) never loses a recording: one going on, or one
      // not yet added, goes beside the page. One playing pauses.
      if (event == Lifecycle.Event.ON_STOP) {
        if (vm.voice.recording) vm.stopMemo()
        vm.keepDraft()
        vm.voice.pause()
      }
    }
    navOwner.lifecycle.addObserver(observer)
    // The bars come back in the library (it shows them itself), not here: switching straight to
    // another notebook (quick open) closes this one after the next has hidden them again.
    onDispose { navOwner.lifecycle.removeObserver(observer) }
  }
  DisposableEffect(darkTop) {
    val window = (view.context as? android.app.Activity)?.window
    val bars = window?.let { WindowCompat.getInsetsController(it, view) }
    bars?.isAppearanceLightStatusBars = !darkTop
    onDispose { bars?.isAppearanceLightStatusBars = !systemDark }
  }
  TaliColorsScope(notebookColors(systemDark, darkPaper)) {
    // Dialogs opened from here keep full screen when it's on.
    androidx.compose.runtime.CompositionLocalProvider(com.tali.notes.ui.components.LocalFullScreen provides hideNavBar) {
      NotebookContent(vm, systemDark, onBack, onSwitch)
    }
  }
}

@Composable
private fun NotebookContent(vm: NotebookViewModel, systemDark: Boolean, onBack: () -> Unit, onSwitch: (String) -> Unit) {
  val context = LocalContext.current
  val settings by vm.settings.collectAsState()
  val meta by vm.meta.collectAsState()
  val doc = vm.document
  val c = Tali.colors
  val holder = remember { CanvasHolder() }
  val scope = rememberCoroutineScope()
  // Held upright (or on a small tablet) the top bar splits into two lines.
  val narrow = LocalConfiguration.current.screenWidthDp < 900
  val darkPaper = meta?.dark == true
  val paged = vm.pageCount != null
  var pagesOpen by remember { mutableStateOf(false) }
  var makingPaper by remember { mutableStateOf(false) }
  // The paper in use, opened for editing from the ⋯ menu.
  var editingPaper by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var exporting by remember { mutableStateOf(false) }
  var savingShape by remember { mutableStateOf<ShapeGeometry?>(null) }
  var pasteOffer by remember { mutableStateOf<com.tali.notes.canvas.PasteOffer?>(null) }
  // A picture just taken with the screenshot lasso, waiting to be saved or shared.
  var screenshot by remember { mutableStateOf<java.io.File?>(null) }
  var pendingPdf by remember { mutableStateOf<File?>(null) }

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val result = vm.importImage(uri)
          if (result == null) vm.showHint(tr(R.string.that_image_couldn_t_be_opened)) else holder.view?.runSafely { insertImage(result.first, result.second) }
        }
      }
    }
  // Photos (the board, a textbook page) or scanned pages from the app's own camera, put on the page.
  var camera by remember { mutableStateOf(false) }
  if (camera) {
    com.tali.notes.ui.camera.CameraScreen(
      start = com.tali.notes.ui.camera.CameraMode.PHOTO,
      onClose = { camera = false },
      onSave = { _, uris ->
        camera = false
        scope.launch {
          for (uri in uris) {
            val result = vm.importImage(uri)
            if (result == null) vm.showHint(tr(R.string.that_photo_couldn_t_be_opened)) else holder.view?.runSafely { insertImage(result.first, result.second) }
          }
        }
      },
    )
  }
  // Where the mic button is (the recording bar hangs just below it). Not state: it moves with the toolbar.
  val micAnchor = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
  // A voice memo goes beside the page on screen, below any already there.
  val memoSpacing = memoSpacingPx()
  val startMemo: () -> Unit = {
    holder.view?.let { canvas -> vm.startMemo(canvas.newMemoSpot(vm.memos, memoSpacing)) }
  }
  val micPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      if (granted) startMemo() else vm.showHint(tr(R.string.to_record_voice_memos_allow_the))
    }
  val pdfPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          val pages = vm.importPdf(uri) ?: return@launch
          holder.view?.runSafely { insertPages(pages) }
          vm.showHint(if (pages.size == 1) tr(R.string.added_1_page_write_right_on) else tr(R.string.added_n_pages_write_right_on, pages.size))
        }
      }
    }
  // PDFs and pictures from other apps (opened with, shared to, or dragged onto the app).
  suspend fun addFiles(kind: com.tali.notes.Incoming.Kind, uris: List<android.net.Uri>, at: com.tali.notes.geometry.Pt?) {
    val canvas = holder.view ?: return
    when (kind) {
      com.tali.notes.Incoming.Kind.PDF ->
        for (uri in uris) {
          val pages = vm.importPdf(uri) ?: continue
          canvas.runSafely { insertPages(pages) }
          vm.showHint(if (pages.size == 1) tr(R.string.added_1_page_write_right_on) else tr(R.string.added_n_pages_write_right_on, pages.size))
        }
      com.tali.notes.Incoming.Kind.IMAGE ->
        uris.forEachIndexed { i, uri ->
          val result = vm.importImage(uri)
          if (result == null) vm.showHint(tr(R.string.that_picture_couldn_t_be_opened_2)) else canvas.runSafely { insertImage(result.first, result.second, at, i) }
        }
      // A backup dropped on the page asks to restore it, as when it's opened with the app.
      com.tali.notes.Incoming.Kind.BACKUP -> com.tali.notes.Incoming.offer.value = com.tali.notes.Incoming.Files(kind, uris, uris.firstOrNull()?.let { com.tali.notes.Incoming.nameOf(context, it) })
    }
  }
  val incoming by com.tali.notes.Incoming.forNotebook.collectAsState()
  val waiting = incoming[vm.notebookId] != null
  LaunchedEffect(waiting, holder.view, doc != null) {
    val canvas = holder.view
    if (!waiting || doc == null || canvas == null) return@LaunchedEffect
    while (canvas.width == 0) delay(16)
    val files = com.tali.notes.Incoming.take(vm.notebookId) ?: return@LaunchedEffect
    // Not in this effect: taking the files restarts it.
    scope.launch { addFiles(files.kind, files.uris, null) }
  }
  val savePdf =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
      val file = pendingPdf
      if (uri != null && file != null) {
        runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } }
          .onSuccess { vm.showHint(tr(R.string.pdf_saved)) }
          .onFailure { vm.showHint(tr(R.string.couldn_t_save_the_pdf_try)) }
      }
      pendingPdf = null
    }

  BackHandler(enabled = vm.panel != null) { vm.panel = null }

  // A PDF opened from another app is only being looked at until she saves it: leaving asks first.
  val unsaved = meta?.draft == true && vm.unsaved
  var savingPdf by remember { mutableStateOf<(() -> Unit)?>(null) }
  var leavingPdf by remember { mutableStateOf<(() -> Unit)?>(null) }
  val leave: (() -> Unit) -> Unit = { go -> if (vm.unsaved) leavingPdf = go else go() }
  BackHandler(enabled = unsaved && vm.panel == null) { leavingPdf = onBack }
  // Only looking (scrolling and zooming): a PDF until it's saved, and a PDF, picture or scan until
  // she taps Write.
  val viewing = unsaved || vm.viewOnlyIn(settings, meta)
  LaunchedEffect(viewing, holder.view) { holder.view?.viewOnly = viewing }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_STOP) holder.view?.let { vm.persistView(it.viewport(), it.visibleWorld()) }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      holder.view?.let { if (it.width > 0) vm.persistView(it.viewport(), it.visibleWorld()) }
    }
  }

  // Also refresh the library preview and saved position a little after she stops writing, in case
  // Android closes the app in the background before she leaves the notebook.
  var edits by remember { mutableIntStateOf(0) }
  DisposableEffect(doc) {
    val listener: (com.tali.notes.canvas.Change) -> Unit = { edits++ }
    doc?.listeners?.add(listener)
    onDispose { doc?.listeners?.remove(listener) }
  }
  LaunchedEffect(edits) {
    if (edits == 0) return@LaunchedEffect
    delay(15_000)
    holder.view?.let { if (it.width > 0) vm.persistView(it.viewport(), it.visibleWorld()) }
  }

  Box(Modifier.fillMaxSize().background(if (darkPaper) Color(com.tali.notes.canvas.PaperRenderer.DARK_PAPER) else c.paper)) {
    if (doc != null) {
      AndroidView(
        factory = { ctx ->
          NoteCanvasView(
              ctx,
              object : CanvasHost {
                override fun onSelectionChanged(selection: SelectionInfo?) {
                  vm.selection = selection
                }

                override fun onZoomChanged(zoom: Float) {
                  vm.zoom = zoom
                }

                override fun onHint(message: String) = vm.showHint(message)

                override fun onPenCycle(step: Int) = vm.cyclePen(step)

                override fun onPasteOffer(offer: com.tali.notes.canvas.PasteOffer?) {
                  pasteOffer = offer
                }

                override fun onDropped(drop: NoteCanvasView.Dropped) {
                  val text = drop.text
                  if (text != null) {
                    holder.view?.runSafely { insertText(text, drop.at, settings.activeSlot.color) }
                    return
                  }
                  val files = com.tali.notes.Incoming.classify(context, drop.uris)
                  if (files == null) vm.showHint(tr(R.string.that_can_t_go_on_a)) else scope.launch { addFiles(files.kind, files.uris, drop.at) }
                }

                override fun onScreenshot(file: java.io.File) {
                  screenshot = file
                }

                override fun onTip(id: String, message: String) = vm.tip(id, message)

                override fun onPageChanged(index: Int, count: Int) {
                  vm.page = index to count
                }

                override fun onPagesChanged(count: Int) = vm.setPages(count)

                override fun onEditText(edit: com.tali.notes.canvas.TextEdit?) {
                  vm.textEdit = edit
                }

                override fun onCameraMoved() {
                  vm.cameraTick++
                }
              },
            )
            .also {
              holder.view = it
              it.settings = settings
              it.pageLayout = vm.pageLayout
              it.pageCount = vm.pageCount
              it.darkPaper = darkPaper
              it.attach(doc, vm.initialViewport)
            }
        },
        update = { v ->
          v.settings = settings
          v.tool = vm.tool
          v.shapeKind = vm.shapeKind
          v.topClearance = if (narrow) 196f else 124f
          v.customShape = settings.customShapes.firstOrNull { it.id == vm.customShapeId }
          v.packShape = vm.packShapeId?.let(com.tali.notes.model.ShapePacks::shape)
          v.paperStyle = meta?.paper ?: PaperStyle.DOTS
          v.customPaper = vm.customPaperOf(meta, settings)
          v.pageLayout = vm.pageLayout
          v.pageCount = vm.pageCount
          v.darkPaper = darkPaper
          v.deskColor = (if (darkPaper) DESK_DARK_PAPER else if (systemDark) DESK_DARK else DESK_LIGHT).toArgb()
          v.accentColor = c.accent.toArgb()
          v.handleColor = c.blue.toArgb()
        },
        onRelease = { v ->
          v.detach()
          if (holder.view === v) holder.view = null
        },
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      Text(stringResource(R.string.opening), style = MaterialTheme.typography.bodyLarge, color = c.inkFaint, modifier = Modifier.align(Alignment.Center))
    }

    // Voice memos beside the pages, over the canvas and under the toolbars.
    holder.view?.let { canvas -> if (doc != null) VoiceMemoLayer(vm, canvas) }

    // Tapping the page closes an open panel instead of drawing.
    if (vm.panel != null) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { vm.panel = null } })

    // The toolbars slide in as the notebook opens (no cloud: she opens notebooks all day). Hiding
    // or showing them puffs a cloud where each bar is, measured from the bars themselves.
    val chrome = remember { androidx.compose.animation.core.MutableTransitionState(false) }
    // Where each bar last sat. A plain map, not state: bars report while they slide, and that
    // mustn't recompose anything. The puff copies it when it starts.
    val bars = remember { HashMap<String, androidx.compose.ui.geometry.Rect>() }
    val barLift = with(LocalDensity.current) { 10.dp.roundToPx() }
    // Not while they're leaving: they'd be recorded mid-slide, off their place.
    fun recordBar(key: String, at: androidx.compose.ui.layout.LayoutCoordinates) {
      if (chrome.targetState) bars[key] = at.boundsInRoot()
    }
    var ready by remember { mutableStateOf(false) }
    // Wait for the notebook and the screen's slide-in, so the hello is seen.
    LaunchedEffect(doc != null) {
      if (doc == null) return@LaunchedEffect
      delay(260)
      ready = true
    }
    // Just looking at a PDF from another app: no toolbars until it's saved (the viewer bar instead).
    chrome.targetState = ready && !vm.focus && !viewing
    var puffs by remember { mutableIntStateOf(0) }
    var shownFocus by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(vm.focus, ready) {
      if (!ready) return@LaunchedEffect
      if (shownFocus != null && shownFocus != vm.focus) puffs++
      shownFocus = vm.focus
    }
    ToolbarPuffs(puffs, showing = !vm.focus, bars = bars)
    AnimatedVisibility(
      visibleState = chrome,
      // Each bar animates itself (barEnter / barExit), so they can follow one another.
      enter = androidx.compose.animation.EnterTransition.None,
      exit = androidx.compose.animation.ExitTransition.None,
    ) {
    // A bar grows from its own top edge, lifting in; the bars follow one another by 40 ms and leave
    // together, quicker. Driven by this visibility's own transition (so it waits for them), and
    // faded draw by draw rather than through an offscreen layer: a layer would clip the cloud bumps
    // above the bar until it was fully shown, so the bar would arrive first and its bumps after.
    @Composable
    fun Modifier.bar(key: String, order: Int, fromBelow: Boolean = false): Modifier {
      val shown by transition.animateFloat(
        transitionSpec = {
          if (targetState == androidx.compose.animation.EnterExitState.Visible) tween(260, delayMillis = 40 * order, easing = com.tali.notes.ui.components.EaseOutStrong)
          else tween(170, easing = com.tali.notes.ui.components.EaseOutStrong)
        },
        label = "bar$order",
      ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
      val lift = (if (fromBelow) barLift else -barLift).toFloat()
      return graphicsLayer {
          alpha = shown
          compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
          translationY = (1f - shown) * lift
          val sc = 0.96f + 0.04f * shown
          scaleX = sc
          scaleY = sc
          transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, if (fromBelow) 1f else 0f)
        }
        .then(if (key.isEmpty()) Modifier else Modifier.onGloballyPositioned { recordBar(key, it) })
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
      // Back, quick open (her recent notebooks), and undo/redo: far from the eye at the other end,
      // which she kept pressing by accident when it sat next to redo.
      val recent by vm.recent.collectAsState()
      val titlePill: @Composable () -> Unit = {
        Box(Modifier.bar("title", 0)) {
          TitlePill(
            onBack = { leave(onBack) },
            canUndo = vm.canUndo,
            canRedo = vm.canRedo,
            onUndo = { holder.view?.runSafely { undo() } },
            onRedo = { holder.view?.runSafely { redo() } },
          ) {
            QuickOpen(recent, vm::thumbnailFile) { id ->
              vm.panel = null
              leave { onSwitch(id) }
            }
          }
        }
      }
      val tools: @Composable () -> Unit = {
        Column(Modifier.bar("", 1), horizontalAlignment = Alignment.CenterHorizontally) {
          MainToolbar(
            modifier = Modifier.onGloballyPositioned { recordBar("tools", it) },
            settings = settings,
            darkPaper = darkPaper,
            tool = vm.tool,
            shapeKind = vm.shapeKind,
            customShape = settings.customShapes.firstOrNull { it.id == vm.customShapeId },
            packShape = vm.packShapeId?.let(com.tali.notes.model.ShapePacks::shape),
            onPen = vm::onPenTapped,
            penSettingsOpen = vm.panel == PanelKind.PEN,
            onPenSettings = vm::togglePenSettings,
            onQuickColor = vm::pickPenColor,
            onQuickSize = vm::pickPenSize,
            onSetSize = vm::setPenPreset,
            onTool = vm::onToolTapped,
            onImage = {
              vm.panel = null
              imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onCamera = {
              vm.panel = null
              camera = true
            },
            onKeyboard = {
              vm.panel = null
              holder.view?.runSafely { startText(settings.activeSlot.color) }
            },
            onPdf = {
              vm.panel = null
              pdfPicker.launch(arrayOf("application/pdf"))
            },
            recording = vm.voice.recording || vm.draft != null,
            onVoiceAnchor = { micAnchor[0] = it },
            onVoice = {
              vm.panel = null
              if (vm.voice.recording) vm.stopMemo()
              else if (vm.draft != null) Unit
              else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startMemo()
              else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
            },
          )
          AnimatedVisibility(
            visible = vm.panel != null,
            enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { -it / 12 } + scaleIn(tween(180), initialScale = 0.97f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.98f),
          ) {
            // The panel that's showing, kept while it fades out: switching to nothing at once left
            // an empty card that shrank into a small round blob as it faded.
            val last = remember { arrayOfNulls<PanelKind>(1) }
            vm.panel?.let { last[0] = it }
            val shown = last[0]
            Column {
              Spacer(Modifier.height(10.dp))
              Panel {
                when (shown) {
                  PanelKind.PEN -> PenPanel(settings, vm)
                  PanelKind.ERASER -> EraserPanel(settings, vm)
                  PanelKind.LASSO -> LassoPanel(settings, vm)
                  PanelKind.SHAPES -> ShapesPanel(settings, vm.shapeKind, vm)
                  PanelKind.FILL -> FillPanel(settings, vm)
                  null -> Unit
                }
              }
            }
          }
        }
      }
      val undoBar: @Composable () -> Unit = {
        // The notebook's name (it shortens first when space is short), then the eye, then ⋯ always
        // in the top-right corner.
        Row(Modifier.bar("undo", 2), verticalAlignment = Alignment.CenterVertically) {
          NamePill(meta?.title ?: "", Modifier.weight(1f, fill = false)) { renaming = true }
          Spacer(Modifier.width(10.dp))
          FloatingBar(Modifier.height(52.dp), crown = CloudCrown.SOFT_EDGE) {
            ToolButton(Icons.Outlined.VisibilityOff, stringResource(R.string.hide_toolbars)) {
              vm.panel = null
              vm.focus = true
            }
            MoreMenu(
              paged = paged,
              darkPaper = darkPaper,
              onDarkPaper = vm::setDark,
              onPages = { pagesOpen = true },
              onEndless = { endless ->
                if (endless) vm.setPages(null) else holder.view?.runSafely { convertToPages() }
                vm.showHint(if (endless) tr(R.string.endless_canvas_write_anywhere) else tr(R.string.a4_pages))
              },
              paperPicker = { close ->
                PaperPicker(
                  style = meta?.paper ?: PaperStyle.DOTS,
                  customId = vm.customPaperOf(meta, settings)?.id,
                  customs = settings.customPapers,
                  onStyle = vm::setPaper,
                  onCustom = { vm.setCustomPaper(it.id) },
                  onAdd = {
                    close()
                    makingPaper = true
                  },
                  onRemove = { vm.deleteCustomPaper(it.id) },
                  onEditCurrent = {
                    close()
                    editingPaper = true
                  },
                  dark = darkPaper || c.isDark,
                  looks = settings.paperLooks,
                )
              },
              fingerDraws = settings.fingerDraws,
              onFit = { holder.view?.runSafely { fitContent() } },
              onExport = { exporting = true },
              onFingerDraws = vm::setFingerDraws,
              pageGuides = settings.pageGuides,
              onPageGuides = vm::setPageGuides,
              smartUndo = settings.smartUndo,
              onSmartUndo = vm::setSmartUndo,
              inkBetweenPages = settings.inkBetweenPages,
              onInkBetweenPages = vm::setInkBetweenPages,
              lockedCount = { holder.view?.lockedCount() ?: 0 },
              onUnlockAll = { holder.view?.runSafely { unlockAll() } },
              hideNavBar = settings.hideNavBar,
              onHideNavBar = vm::setHideNavBar,
              onViewOnly = { vm.setViewOnly(true) },
            )
          }
        }
      }
      if (narrow) {
        // Held upright: title and undo share the first line, the tools get a line of their own.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          titlePill()
          Spacer(Modifier.width(12.dp))
          Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { undoBar() }
        }
        // A little more room between the two lines when the tablet is held upright.
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) { tools() }
      } else {
        // The tools sit in the middle when there's room; when they grow (more colours) they shift
        // over rather than run into the title side, and the notebook's name shortens before
        // anything overlaps.
        androidx.compose.ui.layout.Layout(content = { titlePill(); tools(); undoBar() }, modifier = Modifier.fillMaxWidth()) { parts, cons ->
          val gap = 12.dp.roundToPx()
          val loose = cons.copy(minWidth = 0, minHeight = 0)
          val title = parts[0].measure(loose)
          val toolbar = parts[1].measure(loose)
          // The name in the corner gives way when space is short.
          val corner = parts[2].measure(loose.copy(maxWidth = (cons.maxWidth - title.width - toolbar.width - 2 * gap).coerceAtLeast(0)))
          val from = title.width + gap
          val to = (cons.maxWidth - corner.width - gap - toolbar.width).coerceAtLeast(from)
          val x = ((cons.maxWidth - toolbar.width) / 2).coerceIn(from, to)
          // Everything sits on the main toolbar's middle line (it's the tallest bar), not along the
          // top. Measured from the bar itself: the tools part grows downwards when a panel opens.
          val mid = (MAIN_TOOLBAR_HEIGHT / 2).roundToPx()
          val top = maxOf(0, title.height / 2 - mid, corner.height / 2 - mid)
          val h = maxOf(top + toolbar.height, top + mid + title.height / 2, top + mid + corner.height / 2)
          layout(cons.maxWidth, h) {
            title.place(0, top + mid - title.height / 2)
            toolbar.place(x, top)
            corner.place(cons.maxWidth - corner.width, top + mid - corner.height / 2)
          }
        }
      }
      Spacer(Modifier.weight(1f))
      Box(Modifier.fillMaxWidth().bar("", 3, fromBelow = true)) {
        HintPill(vm.hint, onDone = { vm.hint = null }, modifier = Modifier.align(Alignment.BottomCenter))
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          val (page, pages) = vm.page
          if (page >= 0) PageChip(page, pages, onOverview = if (paged) ({ pagesOpen = true }) else null) { target -> holder.view?.runSafely { goToPage(target) } }
          ZoomChip(vm.zoom) { holder.view?.runSafely { setZoom(1f) } }
        }
      }
    }
    }
    // Recording a voice memo: the bar hangs just below the mic button, over everything.
    RecorderPanel(vm, anchor = { micAnchor[0] }, onAdd = vm::keepDraft)
    PdfViewerBar(
      visible = ready && viewing,
      title = meta?.title ?: "",
      saved = !unsaved,
      kind = meta?.kind ?: com.tali.notes.data.DocKind.PDF,
      onWrite = vm::startWriting,
      pages = vm.pageCount,
      darkPaper = darkPaper,
      onBack = { leave(onBack) },
      onSave = { savingPdf = {} },
      onPages = if (paged) ({ pagesOpen = true }) else null,
      onFit = { holder.view?.runSafely { fitContent() } },
      onDarkPaper = vm::setDark,
    ) {
      HintPill(vm.hint, onDone = { vm.hint = null }, modifier = Modifier.align(Alignment.BottomCenter))
      Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        val (page, pages) = vm.page
        if (page >= 0) PageChip(page, pages, onOverview = if (paged) ({ pagesOpen = true }) else null) { target -> holder.view?.runSafely { goToPage(target) } }
        ZoomChip(vm.zoom) { holder.view?.runSafely { setZoom(1f) } }
      }
    }
    ShowToolbarsButton(vm.focus && !viewing, Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 26.dp, end = 22.dp)) { vm.focus = false }

    pasteOffer?.let { offer ->
      PasteBubble(offer, onDismiss = { pasteOffer = null }) {
        pasteOffer = null
        holder.view?.runSafely { paste(offer.at) }
      }
    }

    screenshot?.let { file -> ScreenshotSheet(file, onDone = { screenshot = null }, onHint = vm::showHint, onCopy = { holder.view?.copyScreenshot(file) }) }

    vm.selection?.let { sel ->
      if (vm.panel == null) {
        SelectionMenu(
          sel,
          narrow = narrow,
          colors = settings.savedColors,
          onDelete = { holder.view?.runSafely { deleteSelection() } },
          onDuplicate = { holder.view?.runSafely { duplicateSelection() } },
          onCopy = { holder.view?.runSafely { copySelection() } },
          onCut = { holder.view?.runSafely { cutSelection() } },
          onColor = { col -> holder.view?.runSafely { recolorSelection(col) } },
          onRestyle = { size, line -> holder.view?.runSafely { restyleSelection(size, line) } },
          onThicknessLive = { size -> holder.view?.previewThickness(size) },
          onSaveShape = { savingShape = holder.view?.selectionGeometry() },
          onEditText = { id -> holder.view?.runSafely { editText(id) } },
          onArrange = { front -> holder.view?.runSafely { arrangeSelection(front) } },
          onMirror = { horizontal -> holder.view?.runSafely { mirrorSelection(horizontal) } },
          onLock = { holder.view?.runSafely { lockSelection() } },
        )
      }
    }

    // Typing: the editor sits over the text block, above everything else.
    vm.textEdit?.let { edit ->
      holder.view?.let { canvas ->
        TextEditorOverlay(
          edit,
          canvas,
          vm.cameraTick,
          colors = settings.savedColors,
          darkPaper = darkPaper,
          onChange = { vm.textEdit = it },
          onDone = { text -> canvas.runSafely { finishText(edit, text) } },
        )
      }
    }
  }

  savingShape?.let { geometry ->
    SaveShapeDialog(geometry, onDismiss = { savingShape = null }) { name ->
      vm.saveCustomShape(name, geometry)
      savingShape = null
      holder.view?.runSafely { clearSelection() }
      vm.showHint(tr(R.string.saved_to_my_shapes_hold_the))
    }
  }
  savingPdf?.let { then ->
    SavePdfDialog(meta?.title ?: "", onDismiss = { savingPdf = null }) { name ->
      vm.saveAsNotebook(name)
      savingPdf = null
      vm.showHint(tr(R.string.saved_to_your_notebooks))
      then()
    }
  }
  leavingPdf?.let { go ->
    LeavePdfDialog(
      title = meta?.title ?: "",
      onStay = { leavingPdf = null },
      onSave = {
        leavingPdf = null
        savingPdf = go
      },
      onLeave = {
        leavingPdf = null
        vm.discardOnLeave()
        go()
      },
    )
  }
  if (renaming) {
    RenameDialog(meta?.title ?: "", onDismiss = { renaming = false }) {
      vm.rename(it)
      renaming = false
    }
  }
  if (pagesOpen && doc != null && vm.pageCount != null) {
    PagesOverview(
      vm = vm,
      doc = doc,
      count = vm.pageCount ?: 1,
      current = vm.page.first.coerceAtLeast(0),
      version = edits,
      onDismiss = { pagesOpen = false },
      onGo = { index ->
        pagesOpen = false
        holder.view?.runSafely { goToPage(index) }
      },
      onAdd = { holder.view?.runSafely { insertPageAfter((vm.pageCount ?: 1) - 1) } },
      onRearrange = { order, message ->
        holder.view?.runSafely { rearrangePages(order) }
        vm.showHint(message)
      },
      onDelete = { index ->
        holder.view?.runSafely { deletePage(index) }
        vm.showHint(tr(R.string.page_n_deleted_undo_brings_the, index + 1))
      },
    )
  }
  if (editingPaper) {
    val current = vm.customPaperOf(meta, settings)
    if (current != null) {
      NewPaperDialog(onDismiss = { editingPaper = false }, importImage = vm::importPaperImage, editing = current) {
        vm.updateCustomPaper(it)
        editingPaper = false
      }
    } else {
      val style = meta?.paper ?: PaperStyle.DOTS
      BuiltInPaperDialog(style, settings.paperWeights[style.name] ?: settings.paperLineWeight, { vm.setPaperWeight(style, it) }, settings.paperLooks[style.name], { vm.setPaperLook(style, it) }) { editingPaper = false }
    }
  }
  if (makingPaper) {
    NewPaperDialog(onDismiss = { makingPaper = false }, importImage = vm::importPaperImage) {
      vm.addCustomPaper(it)
      makingPaper = false
      vm.showHint(tr(R.string.n_saved_to_your_papers, it.name))
    }
  }
  if (exporting) {
    ExportDialog(
      paged = paged,
      hasPdfPages = remember(doc) { doc?.all?.any { it is com.tali.notes.model.ImageItem && it.pdfPage } == true },
      onDismiss = { exporting = false },
      prepare = vm::exportPdf,
      onExport = { layout, quality, share ->
        exporting = false
        vm.showHint(tr(R.string.preparing_pdf))
        scope.launch {
          val file = vm.exportPdf(layout, quality)
          if (file == null) {
            vm.showHint(tr(R.string.couldn_t_create_the_pdf))
            return@launch
          }
          vm.hint = null
          if (share) {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
              type = "application/pdf"
              putExtra(Intent.EXTRA_STREAM, uri)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, tr(R.string.share_pdf)))
          } else {
            pendingPdf = file
            savePdf.launch(file.name)
          }
        }
      },
    )
  }
}

@Composable
private fun TitlePill(onBack: () -> Unit, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, quickOpen: @Composable () -> Unit) {
  // Back on a little cloud of its own, quick open on another, then undo and redo.
  Row(verticalAlignment = Alignment.CenterVertically) {
    FloatingBar(Modifier.height(52.dp), crown = CloudCrown.LITTLE) {
      ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back_to_notebooks), onClick = onBack)
    }
    Spacer(Modifier.width(10.dp))
    quickOpen()
    Spacer(Modifier.width(10.dp))
    FloatingBar(Modifier.height(52.dp), crown = CloudCrown.SOFT_EDGE) {
      ToolButton(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo), enabled = canUndo, onClick = onUndo)
      ToolButton(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo), enabled = canRedo, onClick = onRedo)
    }
  }
}

/**
 * Looking at a PDF opened from another app before it's saved, or at a PDF, picture or scan from her
 * library: no toolbars, just Back, its name, Save (or Write, which brings the toolbars) and a few options, with the page and zoom chips below. The pieces arrive like the toolbars
 * (lifting in from their top edge, 40 ms apart); when it's saved they leave and the toolbars come.
 */
@Composable
private fun PdfViewerBar(
  visible: Boolean,
  title: String,
  saved: Boolean,
  kind: com.tali.notes.data.DocKind,
  onWrite: () -> Unit,
  pages: Int?,
  darkPaper: Boolean,
  onBack: () -> Unit,
  onSave: () -> Unit,
  onPages: (() -> Unit)?,
  onFit: () -> Unit,
  onDarkPaper: (Boolean) -> Unit,
  bottom: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit,
) {
  val c = Tali.colors
  val lift = with(LocalDensity.current) { 10.dp.toPx() }
  AnimatedVisibility(visible, enter = androidx.compose.animation.EnterTransition.None, exit = androidx.compose.animation.ExitTransition.None) {
    @Composable
    fun Modifier.arrive(order: Int, fromBelow: Boolean = false): Modifier {
      val shown by transition.animateFloat(
        transitionSpec = {
          if (targetState == androidx.compose.animation.EnterExitState.Visible) tween(260, delayMillis = 40 * order, easing = com.tali.notes.ui.components.EaseOutStrong)
          else tween(170, easing = com.tali.notes.ui.components.EaseOutStrong)
        },
        label = "viewer$order",
      ) { if (it == androidx.compose.animation.EnterExitState.Visible) 1f else 0f }
      return graphicsLayer {
        alpha = shown
        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
        translationY = (1f - shown) * (if (fromBelow) lift else -lift)
        val sc = 0.96f + 0.04f * shown
        scaleX = sc
        scaleY = sc
        transformOrigin = TransformOrigin(0.5f, if (fromBelow) 1f else 0f)
      }
    }
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 12.dp)) {
      Row(Modifier.fillMaxWidth().align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
        FloatingBar(Modifier.height(52.dp).arrive(0), crown = CloudCrown.LITTLE) {
          ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back), onClick = onBack)
        }
        Spacer(Modifier.width(10.dp))
        FloatingBar(Modifier.height(52.dp).widthIn(max = 380.dp).arrive(1), crown = CloudCrown.NONE) {
          Column(Modifier.padding(horizontal = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              listOfNotNull(
                if (saved) com.tali.notes.ui.library.kindLabel(kind) else null,
                pages?.let { if (it == 1) stringResource(R.string.t_1_page) else stringResource(R.string.n_pages, it) },
                if (saved) null else stringResource(R.string.not_saved),
              ).joinToString(" · "),
              style = MaterialTheme.typography.bodySmall,
              color = c.inkFaint,
              maxLines = 1,
            )
          }
        }
        Spacer(Modifier.weight(1f).widthIn(min = 10.dp))
        // Saved, it's one of hers: the pen brings the toolbars to write on it. Not saved yet: Save.
        if (saved) PrimaryButton(stringResource(R.string.write), icon = Icons.Outlined.Edit, modifier = Modifier.height(52.dp).arrive(2), onClick = onWrite)
        else PrimaryButton(stringResource(R.string.save), icon = Icons.Outlined.Save, modifier = Modifier.height(52.dp).arrive(2), onClick = onSave)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.arrive(3)) {
          var open by remember { mutableStateOf(false) }
          FloatingBar(Modifier.height(52.dp), crown = CloudCrown.SOFT_EDGE) { ToolButton(Icons.Outlined.MoreHoriz, stringResource(R.string.more)) { open = true } }
          DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface, offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)) {
            if (onPages != null) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.all_pages)) },
                leadingIcon = { Icon(Icons.Outlined.GridView, null) },
                onClick = {
                  open = false
                  onPages()
                },
              )
            }
            DropdownMenuItem(
              text = { Text(stringResource(R.string.fit_to_screen)) },
              leadingIcon = { Icon(Icons.Outlined.FitScreen, null) },
              onClick = {
                open = false
                onFit()
              },
            )
            DropdownMenuItem(
              text = { Text(stringResource(R.string.dark_paper)) },
              leadingIcon = { Icon(Icons.Outlined.DarkMode, null) },
              trailingIcon = { if (darkPaper) Icon(Icons.Outlined.Check, null, tint = c.accent) },
              onClick = { onDarkPaper(!darkPaper) },
            )
            // On while looking; turning it off is the same as Write.
            if (saved) {
              DropdownMenuItem(
                text = { Text(stringResource(R.string.view_only)) },
                leadingIcon = { Icon(Icons.Outlined.Visibility, null) },
                trailingIcon = { Icon(Icons.Outlined.Check, null, tint = c.accent) },
                onClick = {
                  open = false
                  onWrite()
                },
              )
            }
          }
        }
      }
      Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).arrive(4, fromBelow = true)) { bottom() }
    }
  }
}

/** Saving a PDF she opened from another app: its name to start with, ready to change. */
@Composable
private fun SavePdfDialog(title: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  val c = Tali.colors
  var name by remember { mutableStateOf(TextFieldValue(title, androidx.compose.ui.text.TextRange(0, title.length))) }
  com.tali.notes.ui.library.DialogCard(onDismiss, width = 460) {
    Text(stringResource(R.string.save_to_your_notebooks), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(6.dp))
    Text(stringResource(R.string.it_goes_into_your_notebooks_ready), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    Spacer(Modifier.height(16.dp))
    TitleField(name, { name = it }, placeholder = stringResource(R.string.notebook_name)) { onSave(name.text) }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
      QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
      PrimaryButton(stringResource(R.string.save)) { onSave(name.text) }
    }
  }
}

/** Leaving a PDF she only looked at: keep looking, save it, or let it go. */
@Composable
private fun LeavePdfDialog(title: String, onStay: () -> Unit, onSave: () -> Unit, onLeave: () -> Unit) {
  val c = Tali.colors
  com.tali.notes.ui.library.DialogCard(onStay, width = 500) {
    Text(stringResource(R.string.leave_without_saving), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(10.dp))
    Text(
      stringResource(R.string.n_isn_t_in_your_notebooks, title),
      style = MaterialTheme.typography.bodyMedium,
      color = c.inkMuted,
    )
    Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
      QuietButton(stringResource(R.string.keep_looking), onClick = onStay)
      Spacer(Modifier.weight(1f))
      QuietButton(stringResource(R.string.leave_without_saving_2), color = c.danger, onClick = onLeave)
      PrimaryButton(stringResource(R.string.save), onClick = onSave)
    }
  }
}

/** The notebook's name, with a pencil to rename it. */
@Composable
private fun NamePill(title: String, modifier: Modifier = Modifier, onRename: () -> Unit) {
  val c = Tali.colors
  FloatingBar(modifier.height(44.dp), crown = CloudCrown.NONE) {
    Row(
      Modifier.weight(1f, fill = false).clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = stringResource(R.string.rename), onClick = onRename).padding(start = 10.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).widthIn(max = 200.dp))
      Spacer(Modifier.width(8.dp))
      Icon(Icons.Outlined.Edit, stringResource(R.string.rename), tint = c.inkFaint, modifier = Modifier.size(16.dp))
    }
  }
}

/**
 * How tall a drop-down under an anchor may be. A DropdownMenu that doesn't fit below its button is
 * moved up over the button, and "fits" means ending [MENU_EDGE_DP] above the bottom of the area
 * inside the system bars. So: from the anchor's bottom, less the system bars and that margin.
 */
private class RoomBelow(private val density: androidx.compose.ui.unit.Density, private val windowHeight: () -> Int, private val bottomInset: () -> Int) {
  private var anchorBottom by mutableIntStateOf(0)

  fun anchor(c: androidx.compose.ui.layout.LayoutCoordinates) {
    anchorBottom = (c.positionInWindow().y + c.size.height).roundToInt()
  }

  val max: Dp
    get() = with(density) { (windowHeight() - bottomInset() - anchorBottom).toDp() - MENU_EDGE_DP.dp - 8.dp }.coerceAtLeast(160.dp)
}

/** The margin Material keeps between a drop-down menu and the edge of the window. */
private const val MENU_EDGE_DP = 48

@Composable
private fun rememberRoomBelow(): RoomBelow {
  val density = LocalDensity.current
  val view = LocalView.current
  // The status bar counts too: the menu's window is measured inside both bars.
  val bars = WindowInsets.systemBars
  return remember(density, view) { RoomBelow(density, { view.rootView.height }, { bars.getBottom(density) + bars.getTop(density) }) }
}

/**
 * Quick open: her recent notebooks in a drop-down, one tap to switch. The rows drift down into
 * place and fade in one after another (strong ease-out, 30 ms apart), so the list pours out of the
 * button. Copied writing stays copied across notebooks, so she can hold to paste in the next one.
 */
@Composable
private fun QuickOpen(recent: List<com.tali.notes.data.NotebookMeta>, thumbnail: (String) -> java.io.File, onOpen: (String) -> Unit) {
  val c = Tali.colors
  var open by remember { mutableStateOf(false) }
  val roomBelow = rememberRoomBelow()
  Box(Modifier.onGloballyPositioned { roomBelow.anchor(it) }) {
    // Two puffs, not the back button's three, so the two little clouds side by side don't match.
    FloatingBar(Modifier.height(52.dp), crown = CloudCrown.LITTLE_TWIN) {
      // No highlight while the menu is open: the menu itself shows it.
      ToolButton(TaliIcons.RecentNotebook, stringResource(R.string.recent_notebooks)) { open = true }
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = roomBelow.max - 6.dp), shape = RoundedCornerShape(16.dp), containerColor = c.surface, offset = androidx.compose.ui.unit.DpOffset(0.dp, 6.dp)) {
      Text(stringResource(R.string.recent_notebooks), style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp))
      if (recent.isEmpty()) {
        Text(
          stringResource(R.string.notebooks_you_open_show_up_here),
          style = MaterialTheme.typography.bodySmall,
          color = c.inkMuted,
          modifier = Modifier.widthIn(max = 260.dp).padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        )
      }
      recent.forEachIndexed { i, nb ->
        val shown = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
          delay(minOf(i, 6) * 30L)
          shown.animateTo(1f, tween(240, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
        }
        Row(
          Modifier
            .graphicsLayer {
              alpha = shown.value
              translationY = (1f - shown.value) * -10.dp.toPx()
            }
            .widthIn(min = 260.dp, max = 340.dp)
            .clickable(onClickLabel = stringResource(R.string.open_n, nb.title)) {
              open = false
              onOpen(nb.id)
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          val thumb = com.tali.notes.ui.components.rememberThumbnail(thumbnail(nb.id), nb.thumbVersion)
          Box(Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(7.dp)).background(Color(nb.cover).copy(alpha = 0.25f)).border(1.dp, c.line, RoundedCornerShape(7.dp))) {
            if (thumb != null) androidx.compose.foundation.Image(thumb, null, contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize())
            // Its cover colour down the spine, like on the shelf.
            Box(Modifier.fillMaxHeight().width(4.dp).background(Color(nb.cover)))
          }
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f, fill = false)) {
            Text(nb.title.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.edited) + com.tali.notes.ui.components.relativeTime(nb.updated).replaceFirstChar { it.lowercase() }, style = MaterialTheme.typography.bodySmall, color = c.inkFaint, maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun MoreMenu(
  paged: Boolean,
  darkPaper: Boolean,
  onDarkPaper: (Boolean) -> Unit,
  onPages: () -> Unit,
  onEndless: (Boolean) -> Unit,
  fingerDraws: Boolean?,
  paperPicker: @Composable (close: () -> Unit) -> Unit,
  onFit: () -> Unit,
  onExport: () -> Unit,
  onFingerDraws: (Boolean?) -> Unit,
  pageGuides: Boolean,
  onPageGuides: (Boolean) -> Unit,
  smartUndo: Boolean,
  onSmartUndo: (Boolean) -> Unit,
  inkBetweenPages: Boolean,
  onInkBetweenPages: (Boolean) -> Unit,
  lockedCount: () -> Int,
  onUnlockAll: () -> Unit,
  hideNavBar: Boolean,
  onHideNavBar: (Boolean) -> Unit,
  onViewOnly: () -> Unit,
) {
  val c = Tali.colors
  var open by remember { mutableStateOf(false) }
  // Counted when the menu opens, so it's always current.
  val locked = remember(open) { if (open) lockedCount() else 0 }
  val roomBelow = rememberRoomBelow()
  Box(Modifier.onGloballyPositioned { roomBelow.anchor(it) }) {
    ToolButton(Icons.Outlined.MoreHoriz, stringResource(R.string.more)) { open = true }
    // Kept to the room under the button (it scrolls): taller, the menu was moved up over the button.
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = roomBelow.max), shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
      // Kept compact so it fits under the button on a landscape tablet: the actions as one row of
      // tiles, the paper choices as chips, and the settings as one-line switches.
      Column(Modifier.width(330.dp).padding(horizontal = 10.dp, vertical = 4.dp)) {
        if (locked > 0) {
          MenuSwitchRow(Icons.Outlined.LockOpen, if (locked == 1) stringResource(R.string.unlock_all_one) else stringResource(R.string.unlock_all_n, locked), checked = null) {
            open = false
            onUnlockAll()
          }
          HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          if (paged) {
            MenuTile(Icons.Outlined.AutoStories, stringResource(R.string.pages), Modifier.weight(1f)) {
              open = false
              onPages()
            }
          }
          MenuTile(Icons.Outlined.FitScreen, if (paged) stringResource(R.string.whole_page) else stringResource(R.string.everything), Modifier.weight(1f)) {
            open = false
            onFit()
          }
          MenuTile(Icons.Outlined.IosShare, stringResource(R.string.export_pdf), Modifier.weight(1f)) {
            open = false
            onExport()
          }
        }
        Text(stringResource(R.string.paper), style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 6.dp))
        Box(Modifier.padding(horizontal = 2.dp)) { paperPicker { open = false } }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          MenuChip(Icons.Outlined.DarkMode, stringResource(R.string.dark_paper), darkPaper, Modifier.weight(1f)) { onDarkPaper(!darkPaper) }
          MenuChip(Icons.Outlined.AllInclusive, stringResource(R.string.endless_canvas), !paged, Modifier.weight(1f)) { onEndless(paged) }
        }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        val fingerLabel = when (fingerDraws) {
          null -> stringResource(R.string.auto)
          true -> stringResource(R.string.on)
          false -> stringResource(R.string.off)
        }
        MenuSwitchRow(Icons.Outlined.TouchApp, stringResource(R.string.draw_with_finger), checked = null, value = fingerLabel) {
          onFingerDraws(
            when (fingerDraws) {
              null -> true
              true -> false
              false -> null
            }
          )
        }
        MenuSwitchRow(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.tap_2_fingers_to_undo_3), smartUndo) { onSmartUndo(!smartUndo) }
        if (paged) MenuSwitchRow(Icons.Outlined.VerticalAlignCenter, stringResource(R.string.write_between_pages), inkBetweenPages) { onInkBetweenPages(!inkBetweenPages) }
        else MenuSwitchRow(Icons.Outlined.Description, stringResource(R.string.a4_page_guides), pageGuides) { onPageGuides(!pageGuides) }
        MenuSwitchRow(Icons.Outlined.Fullscreen, stringResource(R.string.full_screen), hideNavBar) { onHideNavBar(!hideNavBar) }
        // Just for looking from now on (the viewer's ⋯ menu turns it off again, or Write).
        MenuSwitchRow(Icons.Outlined.Visibility, stringResource(R.string.view_only), false) {
          open = false
          onViewOnly()
        }
      }
    }
  }
}

/** An action in the ⋯ menu's top row: icon over a short label. */
@Composable
private fun MenuTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = Tali.colors
  Column(
    modifier.clip(RoundedCornerShape(12.dp)).background(c.accentSoft.copy(alpha = 0.55f)).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, null, tint = c.accent, modifier = Modifier.size(20.dp))
    Spacer(Modifier.height(3.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink, maxLines = 1)
  }
}

/** An on/off paper choice in the ⋯ menu, as a chip that fills with the accent when on. */
@Composable
private fun MenuChip(icon: ImageVector, label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = Tali.colors
  val wash by animateColorAsState(if (on) c.accentSoft else Color.Transparent, tween(200), label = "chip")
  Row(
    modifier.height(38.dp).clip(RoundedCornerShape(12.dp)).background(wash).border(1.dp, if (on) Color.Transparent else c.line, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(if (on) Icons.Outlined.Check else icon, null, tint = if (on) c.accent else c.inkMuted, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) c.accent else c.ink, maxLines = 1)
  }
}

/** A one-line setting in the ⋯ menu: a tick when [checked] is on, or a [value] for a three-way one. */
@Composable
private fun MenuSwitchRow(icon: ImageVector, label: String, checked: Boolean?, value: String? = null, onClick: () -> Unit) {
  val c = Tali.colors
  Row(
    Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    Text(label, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(10.dp))
    when {
      value != null -> Text(value, style = MaterialTheme.typography.labelLarge, color = c.accent)
      checked == true -> Icon(Icons.Outlined.Check, null, tint = c.accent, modifier = Modifier.size(20.dp))
      else -> Spacer(Modifier.size(20.dp))
    }
  }
}

@Composable
private fun ZoomChip(zoom: Float, modifier: Modifier = Modifier, onReset: () -> Unit) {
  val c = Tali.colors
  Box(
    modifier.cloudSurface(12.dp, shadowDepth = 3.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = stringResource(R.string.reset_zoom), onClick = onReset).padding(horizontal = 12.dp, vertical = 7.dp)
  ) {
    Text(stringResource(R.string.n, (zoom * 100).roundToInt()), style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
  }
}

/** "3 / 12" with arrows, for notebooks with imported slides. */
@Composable
private fun PageChip(page: Int, count: Int, onOverview: (() -> Unit)?, onGo: (Int) -> Unit) {
  val c = Tali.colors
  Row(Modifier.cloudSurface(12.dp, shadowDepth = 3.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    PageArrow(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.previous_page), enabled = page > 0) { onGo(page - 1) }
    Text(
      stringResource(R.string.n_n_3, page + 1, count),
      style = MaterialTheme.typography.labelMedium,
      color = c.inkMuted,
      modifier = Modifier.clip(RoundedCornerShape(8.dp)).then(if (onOverview != null) Modifier.clickable(onClickLabel = stringResource(R.string.all_pages), onClick = onOverview) else Modifier).padding(horizontal = 6.dp, vertical = 6.dp),
    )
    PageArrow(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.next_page), enabled = page < count - 1) { onGo(page + 1) }
  }
}

@Composable
private fun PageArrow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
  val c = Tali.colors
  Box(Modifier.size(32.dp).clip(CircleShape).clickable(enabled = enabled, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
    Icon(icon, label, tint = if (enabled) c.blue else c.inkFaint.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
  }
}

@Composable
private fun HintPill(message: String?, onDone: () -> Unit, modifier: Modifier = Modifier) {
  val c = Tali.colors
  LaunchedEffect(message) {
    if (message != null && !message.endsWith("…")) {
      // Long enough to read: about a fifth of a second per word, never less than 2.4 s.
      delay(maxOf(2400L, message.split(' ').size * 260L))
      onDone()
    }
  }
  AnimatedVisibility(message != null, modifier = modifier, enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut()) {
    // A little cloud carrying the message.
    Row(
      Modifier.padding(top = 18.dp, bottom = 4.dp).cloudSurface(20.dp, CloudCrown.CENTER, shadowDepth = 5.dp).padding(start = 14.dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Sparkle(size = 12.dp, color = c.blue)
      Spacer(Modifier.width(8.dp))
      Text(message ?: "", style = MaterialTheme.typography.labelLarge, color = c.ink)
    }
  }
}

/**
 * Paste, offered above where she held the pen or finger (below it near the top). A tap anywhere
 * else takes it away.
 */
@Composable
private fun PasteBubble(offer: com.tali.notes.canvas.PasteOffer, onDismiss: () -> Unit, onPaste: () -> Unit) {
  val c = Tali.colors
  val density = LocalDensity.current
  val shown = remember(offer) { Animatable(0f) }
  LaunchedEffect(offer) { shown.animateTo(1f, tween(180, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))) }
  Box(Modifier.fillMaxSize().pointerInput(offer) { detectTapGestures(onPress = { onDismiss() }) }) {
    Box(
      Modifier.layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
        val gap = with(density) { 18.dp.roundToPx() }
        val edge = with(density) { 12.dp.roundToPx() }
        val x = (offer.screenX.roundToInt() - p.width / 2).coerceIn(edge, (constraints.maxWidth - p.width - edge).coerceAtLeast(edge))
        val above = offer.screenY.roundToInt() - p.height - gap
        val y = if (above >= with(density) { 96.dp.roundToPx() }) above else offer.screenY.roundToInt() + gap
        layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
      }
        .graphicsLayer {
          val t = shown.value
          alpha = t
          scaleX = 0.92f + 0.08f * t
          scaleY = 0.92f + 0.08f * t
          transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) {
      FloatingBar(Modifier.height(48.dp)) {
        Row(Modifier.fillMaxHeight().clip(RoundedCornerShape(18.dp)).clickable(onClick = onPaste).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Outlined.ContentPaste, null, tint = c.ink, modifier = Modifier.size(20.dp))
          Text(if (offer.count == 1) stringResource(R.string.paste) else stringResource(R.string.paste_n_items, offer.count), color = c.ink, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp, end = 2.dp))
        }
      }
    }
  }
}

@Composable
internal fun SelectionMenu(sel: SelectionInfo, narrow: Boolean, colors: List<Int>, onDelete: () -> Unit, onDuplicate: () -> Unit, onCopy: () -> Unit, onCut: () -> Unit, onColor: (Int) -> Unit, onRestyle: (Float?, LineStyle?) -> Unit, onSaveShape: () -> Unit, onEditText: (String) -> Unit, onArrange: (toFront: Boolean) -> Unit, onLock: () -> Unit, onMirror: (horizontal: Boolean) -> Unit, onThicknessLive: (Float) -> Unit = {}) {
  val c = Tali.colors
  val density = LocalDensity.current
  // Folded: just the everyday actions. ⋯ opens the bigger card with style and everything else.
  var more by remember(sel.count) { mutableStateOf(false) }
  // Under ⋯: the palette button opens just the colours, the thickness button just the thickness.
  var extra by remember(sel.count) { mutableStateOf<SelectionExtra?>(null) }
  // Width of the folded buttons (copy to ⋯): the menu is placed by it, so opening grows only to the right.
  var foldedWidth by remember { mutableIntStateOf(0) }
  // Width of the whole button row: the colours and thickness under it spread across exactly that.
  var rowWidth by remember { mutableIntStateOf(0) }
  Box(
    Modifier.fillMaxSize().layout { measurable, constraints ->
      val gap = with(density) { 14.dp.roundToPx() }
      // Keep clear of the toolbars along the top.
      val topLimit = with(density) { (if (narrow) 196 else 128).dp.roundToPx() }
      val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = (constraints.maxHeight - topLimit - gap).coerceAtLeast(0)))
      val bottomLimit = constraints.maxHeight - p.height - gap
      val cx = sel.screenRect.centerX().roundToInt()
      val folded = if (foldedWidth > 0) foldedWidth + with(density) { 12.dp.roundToPx() } else p.width
      // Opening keeps the side ⋯ starts from: the left in English, the right in Hebrew.
      val start = if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) cx + folded / 2 - p.width else cx - folded / 2
      val x = start.coerceIn(gap, (constraints.maxWidth - p.width - gap).coerceAtLeast(gap))
      val above = sel.screenRect.top.roundToInt() - p.height - gap
      // Leave room for the rotate handle hanging under the selection.
      val below = sel.screenRect.bottom.roundToInt() + gap + if (sel.rotatable) with(density) { 48.dp.roundToPx() } else 0
      val y = when {
        above >= topLimit -> above
        below <= bottomLimit -> below
        // A big selection: float the menu just inside its top edge.
        else -> (sel.screenRect.top.roundToInt() + gap).coerceIn(topLimit, bottomLimit.coerceAtLeast(topLimit))
      }
      layout(constraints.maxWidth, constraints.maxHeight) { p.place(x, y) }
    }
  ) {
    Column(
      Modifier.cloudSurface(22.dp, CloudCrown.SOFT_EDGE)
        .animateContentSize(tween(220, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
        .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
      // One row: ⋯ adds the rest straight after itself (scrolls sideways only if the screen is too narrow).
      Row(Modifier.height(44.dp).onSizeChanged { rowWidth = it.width }.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.onSizeChanged { foldedWidth = it.width }, verticalAlignment = Alignment.CenterVertically) {
          ToolButton(Icons.Outlined.ContentCopy, stringResource(R.string.copy_hold_anywhere_to_paste_it), onClick = onCopy)
          ToolButton(Icons.Outlined.ContentCut, stringResource(R.string.cut_hold_anywhere_to_paste_it), onClick = onCut)
          ToolButton(Icons.Outlined.LibraryAdd, stringResource(R.string.duplicate), onClick = onDuplicate)
          ToolButton(Icons.Outlined.DeleteOutline, stringResource(R.string.delete), onClick = onDelete)
          Box(Modifier.padding(horizontal = 2.dp).width(1.dp).height(24.dp).background(c.line))
          // Like the toolbar's chevron: > opens the rest to its right, and turns round into < to fold them away.
          val turn by androidx.compose.animation.core.animateFloatAsState(if (more) 180f else 0f, androidx.compose.animation.core.tween(220, easing = com.tali.notes.ui.components.EaseOutStrong), label = "moreArrow")
          Box(
            Modifier.size(30.dp, 44.dp).clip(RoundedCornerShape(10.dp))
              .clickable(onClickLabel = if (more) stringResource(R.string.fewer_options) else stringResource(R.string.more_options), role = Role.Button) { more = !more; extra = null },
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, if (more) stringResource(R.string.fewer_options) else stringResource(R.string.more_options), tint = c.inkMuted, modifier = Modifier.size(22.dp).rotate(turn))
          }
        }
        if (more) {
          // The rest of what the menu always had, as plain buttons; colour and thickness each open their own row.
          if (!sel.hasImage || sel.count > 1) {
            ToolButton(Icons.Outlined.Palette, stringResource(R.string.colour), selected = extra == SelectionExtra.COLOUR) { extra = if (extra == SelectionExtra.COLOUR) null else SelectionExtra.COLOUR }
          }
          if (sel.lineSize != null) {
            ToolButton(Icons.Outlined.LineWeight, stringResource(R.string.thickness), selected = extra == SelectionExtra.THICKNESS) { extra = if (extra == SelectionExtra.THICKNESS) null else SelectionExtra.THICKNESS }
          }
          ToolButton(Icons.Outlined.FlipToFront, stringResource(R.string.bring_to_front)) { onArrange(true) }
          ToolButton(Icons.Outlined.FlipToBack, stringResource(R.string.send_to_back)) { onArrange(false) }
          // Writing and shapes flip; pictures and text keep facing the right way.
          ToolButton(com.tali.notes.ui.components.TaliIcons.MirrorHorizontal, stringResource(R.string.mirror_left_to_right)) { onMirror(true) }
          ToolButton(com.tali.notes.ui.components.TaliIcons.MirrorVertical, stringResource(R.string.mirror_upside_down)) { onMirror(false) }
          // Locked things stay put: the lasso, taps and the eraser leave them alone.
          if (sel.locked) ToolButton(Icons.Outlined.LockOpen, stringResource(R.string.unlock), onClick = onLock) else ToolButton(Icons.Outlined.Lock, stringResource(R.string.lock_in_place), onClick = onLock)
          sel.textId?.let { id -> ToolButton(Icons.Outlined.EditNote, stringResource(R.string.edit_text)) { onEditText(id) } }
          if (sel.hasDrawing) ToolButton(Icons.Outlined.AutoAwesome, stringResource(R.string.save_as_shape), onClick = onSaveShape)
        }
      }
      if (more) {
        when (extra) {
          SelectionExtra.COLOUR -> SelectionColours(sel, colors, with(density) { rowWidth.toDp() }, onColor)
          SelectionExtra.THICKNESS -> sel.lineSize?.let { SelectionThickness(it, sel.line, with(density) { rowWidth.toDp() }, onRestyle, onThicknessLive) }
          null -> {}
        }
      }
    }
  }
}

private enum class SelectionExtra { COLOUR, THICKNESS }

/** The selection's colour, opened from the palette button: the dots spread evenly across the menu's [width], wrapping only when they don't fit. */
@Composable
private fun SelectionColours(sel: SelectionInfo, colors: List<Int>, width: Dp, onColor: (Int) -> Unit) {
  val ring = rememberSlidingSelection()
  val perRow = (width.value / 38f).toInt().coerceIn(1, colors.size.coerceAtLeast(1))
  Column(Modifier.width(width).padding(bottom = 4.dp).colorRing(ring, sel.color?.let { Color(it) })) {
    colors.chunked(perRow).forEach { row ->
      Row(Modifier.fillMaxWidth()) {
        row.forEach { col -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ColorDot(Color(col), selected = col == sel.color, size = 28.dp, selection = ring) { onColor(col) } } }
        // Keep the columns lined up on a shorter last row.
        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
      }
    }
  }
}

/** Thickness and line style of selected writing and shapes, opened from the thickness button: side by side when the menu is wide enough, else one above the other. */
@Composable
private fun SelectionThickness(size: Float, line: LineStyle?, width: Dp, onRestyle: (Float?, LineStyle?) -> Unit, onLive: (Float) -> Unit = {}) {
  val c = Tali.colors
  // Follows the finger here and changes the writing once let go (one undo step).
  var value by remember(size) { mutableFloatStateOf(size) }
  val slider = @Composable { modifier: Modifier ->
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
      // The drawing follows the slider as it moves; the change is saved (one undo step) when it's let go.
      StepSlider(value, 0.5f..40f, 0.5f, { value = it; onLive(it) }, Modifier.weight(1f), onChangeFinished = { onRestyle(if (value != size) value else null, null) })
      Text(String.format(java.util.Locale.US, "%.1f", value), style = MaterialTheme.typography.titleSmall, color = c.ink, modifier = Modifier.width(40.dp).padding(start = 8.dp))
    }
  }
  val styles = @Composable { modifier: Modifier -> CompactLineStylePicker(line ?: LineStyle.SOLID, { onRestyle(null, it) }, modifier) }
  if (width >= 500.dp) {
    Row(Modifier.width(width).padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
      slider(Modifier.weight(1f))
      Spacer(Modifier.width(8.dp))
      styles(Modifier.weight(1f))
    }
  } else {
    Column(Modifier.width(width).padding(start = 4.dp, bottom = 6.dp)) {
      slider(Modifier.fillMaxWidth())
      styles(Modifier.fillMaxWidth().padding(top = 2.dp))
    }
  }
}

/** Names a selection before it joins My shapes, with a preview of the clean shape. */
@Composable
private fun SaveShapeDialog(geometry: ShapeGeometry, onDismiss: () -> Unit, onSave: (String) -> Unit) {
  val c = Tali.colors
  var value by remember { mutableStateOf(TextFieldValue("")) }
  DialogCard(onDismiss, width = 480) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(88.dp).clip(RoundedCornerShape(20.dp)).background(c.blueSoft), contentAlignment = Alignment.Center) {
        GeometryIcon(geometry, c.blue, size = 72.dp)
      }
      Spacer(Modifier.width(18.dp))
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.save_as_shape), style = MaterialTheme.typography.titleLarge, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.it_will_appear_in_shapes_under), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
      }
    }
    Spacer(Modifier.height(18.dp))
    TitleField(value, { value = it }, stringResource(R.string.name_e_g_i_beam)) { onSave(value.text) }
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
      PrimaryButton(stringResource(R.string.save_shape)) { onSave(value.text) }
    }
  }
}

/** What the export dialog's main choice can be: a quality, or the whole canvas on one long page. */
private enum class ExportChoice(private val labelRes: Int, val quality: PdfQuality, private val detailRes: Int) {
  BEST(PdfQuality.BEST.nameRes, PdfQuality.BEST, PdfQuality.BEST.detailRes),
  BALANCED(PdfQuality.BALANCED.nameRes, PdfQuality.BALANCED, PdfQuality.BALANCED.detailRes),
  LIGHT(PdfQuality.LIGHT.nameRes, PdfQuality.LIGHT, PdfQuality.LIGHT.detailRes),
  LONG(R.string.one_long_page, PdfQuality.BEST, R.string.everything_on_one_long_page_sized);

  // Read when shown, so they're in the language of the moment.
  val label: String
    get() = tr(labelRes)
  val detail: String
    get() = tr(detailRes)
}

private fun sizeLabel(bytes: Long): String =
  when {
    bytes >= 1_000_000 -> String.format(java.util.Locale.UK, tr(R.string.file_size_1f_mb), bytes / 1_000_000.0)
    bytes >= 1_000 -> tr(R.string.file_size_n_kb, (bytes / 1_000.0).roundToInt())
    else -> tr(R.string.file_size_under_1_kb)
  }

/**
 * Export: Best, Balanced, Light or one long page, with the real file size of the choice shown
 * under it (the PDF is made in the background, and that same file is what gets shared or saved).
 * Notebooks with imported slides on an endless canvas can also export the slides' own pages.
 */
@Composable
private fun ExportDialog(paged: Boolean, hasPdfPages: Boolean, onDismiss: () -> Unit, prepare: suspend (PdfLayout, PdfQuality) -> File?, onExport: (PdfLayout, PdfQuality, share: Boolean) -> Unit) {
  val c = Tali.colors
  val pageLayouts = if (hasPdfPages && !paged) listOf(PdfLayout.PDF_PAGES, PdfLayout.A4_PAGES) else listOf(PdfLayout.A4_PAGES)
  var pagesLayout by remember { mutableStateOf(pageLayouts.first()) }
  var choice by remember { mutableStateOf(ExportChoice.BEST) }
  val layout = if (choice == ExportChoice.LONG) PdfLayout.ONE_PAGE else pagesLayout
  val sizes = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
  val key = "$layout-${choice.quality}"
  LaunchedEffect(key) { prepare(layout, choice.quality)?.let { sizes[key] = it.length() } }
  DialogCard(onDismiss, width = 560) {
    Text(stringResource(R.string.export_pdf), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(16.dp))
    CardBody {
    if (pageLayouts.size > 1 && choice != ExportChoice.LONG) {
      Segmented(pageLayouts, pagesLayout, { if (it == PdfLayout.PDF_PAGES) tr(R.string.slides_own_pages) else tr(R.string.a4_pages) }, { pagesLayout = it }, Modifier.fillMaxWidth())
      Spacer(Modifier.height(14.dp))
    }
    Segmented(ExportChoice.entries, choice, { it.label }, { choice = it }, Modifier.fillMaxWidth())
    Spacer(Modifier.height(12.dp))
    val size = sizes[key]
    Text(size?.let(::sizeLabel) ?: stringResource(R.string.making_the_pdf_to_measure_it), style = MaterialTheme.typography.titleSmall, color = if (size != null) c.ink else c.inkFaint)
    Spacer(Modifier.height(2.dp))
    Text(
      when {
        choice == ExportChoice.LONG -> choice.detail
        layout == PdfLayout.PDF_PAGES -> stringResource(R.string.each_slide_you_imported_comes_back) + choice.detail
        paged -> stringResource(R.string.every_page_of_the_notebook_as) + choice.detail
        else -> stringResource(R.string.the_canvas_cut_into_a4_pages) + choice.detail
      },
      style = MaterialTheme.typography.bodyMedium,
      color = c.inkMuted,
      minLines = 2,
    )
    }
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton(stringResource(R.string.cancel), onClick = onDismiss)
      QuietButton(stringResource(R.string.save_to_files)) { onExport(layout, choice.quality, false) }
      PrimaryButton(stringResource(R.string.share)) { onExport(layout, choice.quality, true) }
    }
  }
}

/**
 * While the toolbars are hidden, the one thing left: a small frosted circle in the corner with the
 * familiar "exit full screen" arrows. It springs in, and brings the toolbars back.
 */
@Composable
private fun ShowToolbarsButton(visible: Boolean, modifier: Modifier = Modifier, onShow: () -> Unit) {
  val c = Tali.colors
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    // Comes in as the bars go (from 90%, no bounce); leaves quicker than it came.
    enter = fadeIn(tween(200, delayMillis = 80, easing = com.tali.notes.ui.components.EaseOutStrong)) +
      scaleIn(tween(240, delayMillis = 80, easing = com.tali.notes.ui.components.EaseOutStrong), initialScale = 0.9f),
    exit = fadeOut(tween(120, easing = com.tali.notes.ui.components.EaseOutStrong)) + scaleOut(tween(140, easing = com.tali.notes.ui.components.EaseOutStrong), targetScale = 0.95f),
  ) {
    Box(
      Modifier.size(40.dp)
        .clip(CircleShape)
        .background(c.surface.copy(alpha = 0.72f))
        .border(1.dp, c.line, CircleShape)
        .clickable(onClickLabel = stringResource(R.string.show_toolbars), onClick = onShow),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.Visibility, stringResource(R.string.show_toolbars), tint = c.inkMuted, modifier = Modifier.size(20.dp))
    }
  }
}

/**
 * A puff of mist where each bar is (or was), whenever the toolbars are hidden or brought back:
 * full when they go, lighter when they come back. Progress is read while drawing, so the puff
 * never recomposes the screen while it plays.
 */
@Composable
private fun ToolbarPuffs(tick: Int, showing: Boolean, bars: Map<String, androidx.compose.ui.geometry.Rect>) {
  val p = remember { androidx.compose.animation.core.Animatable(1f) }
  var playing by remember { mutableStateOf(false) }
  var spots by remember { mutableStateOf(emptyList<androidx.compose.ui.geometry.Rect>()) }
  LaunchedEffect(tick) {
    if (tick == 0) return@LaunchedEffect
    spots = bars.values.toList()
    playing = true
    p.snapTo(0f)
    p.animateTo(1f, tween(com.tali.notes.ui.components.SUBTLE_PUFF_MS, easing = androidx.compose.animation.core.LinearEasing))
    playing = false
  }
  if (!playing) return
  var origin by remember { mutableStateOf(Offset.Zero) }
  val density = LocalDensity.current
  Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
    spots.forEach { r ->
      with(density) {
        com.tali.notes.ui.components.CloudPuff(
          { p.value },
          Modifier.offset { IntOffset((r.left - origin.x).roundToInt(), (r.top - origin.y).roundToInt()) }.size(r.width.toDp(), r.height.toDp()),
          strength = if (showing) 0.5f else 0.85f,
        )
      }
    }
  }
}

private val DESK_LIGHT = Color(0xFFE9E4F2)
private val DESK_DARK = Color(0xFF2A2338)
private val DESK_DARK_PAPER = Color(0xFF0E0C13)

/** The main toolbar's height ([MainToolbar]); the bars beside it centre on its middle. */
val MAIN_TOOLBAR_HEIGHT = 60.dp
