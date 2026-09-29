package com.tali.notes.ui.notebook

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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.Tool
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.CloudCrown
import com.tali.notes.ui.components.ColorDot
import com.tali.notes.ui.components.FloatingBar
import com.tali.notes.ui.components.Panel
import com.tali.notes.ui.components.PaperSwatch
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.Segmented
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

private class CanvasHolder {
  var view: NoteCanvasView? = null
}

@Composable
fun NotebookScreen(notebookId: String, onBack: () -> Unit) {
  val context = LocalContext.current
  val app = context.applicationContext as Application
  val vm: NotebookViewModel = viewModel(key = notebookId) { NotebookViewModel(app, notebookId) }
  val settings by vm.settings.collectAsState()
  val meta by vm.meta.collectAsState()
  val doc = vm.document
  val c = Tali.colors
  val holder = remember { CanvasHolder() }
  val scope = rememberCoroutineScope()
  // The paper is always light, so the status bar needs dark icons here even in dark mode.
  val view = LocalView.current
  DisposableEffect(c.isDark) {
    val window = (view.context as? android.app.Activity)?.window
    val bars = window?.let { WindowCompat.getInsetsController(it, view) }
    bars?.isAppearanceLightStatusBars = true
    onDispose { bars?.isAppearanceLightStatusBars = !c.isDark }
  }
  var renaming by remember { mutableStateOf(false) }
  var exporting by remember { mutableStateOf(false) }
  var savingShape by remember { mutableStateOf<ShapeGeometry?>(null) }
  var pendingPdf by remember { mutableStateOf<File?>(null) }

  val imagePicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri != null) {
        scope.launch {
          val result = vm.importImage(uri)
          if (result == null) vm.showHint("That image couldn't be opened") else holder.view?.runSafely { insertImage(result.first, result.second) }
        }
      }
    }
  val pdfPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) {
        scope.launch {
          val pages = vm.importPdf(uri) ?: return@launch
          holder.view?.runSafely { insertPages(pages) }
          vm.showHint(if (pages.size == 1) "Added 1 page — write right on it" else "Added ${pages.size} pages — write right on them")
        }
      }
    }
  val savePdf =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
      val file = pendingPdf
      if (uri != null && file != null) {
        runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } }
          .onSuccess { vm.showHint("PDF saved") }
          .onFailure { vm.showHint("Couldn't save the PDF. Try another folder.") }
      }
      pendingPdf = null
    }

  BackHandler(enabled = vm.panel != null) { vm.panel = null }

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

  Box(Modifier.fillMaxSize().background(c.paper)) {
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

                override fun onPageChanged(index: Int, count: Int) {
                  vm.page = index to count
                }
              },
            )
            .also {
              holder.view = it
              it.settings = settings
              it.attach(doc, vm.initialViewport)
            }
        },
        update = { v ->
          v.settings = settings
          v.tool = vm.tool
          v.shapeKind = vm.shapeKind
          v.customShape = settings.customShapes.firstOrNull { it.id == vm.customShapeId }
          v.paperStyle = meta?.paper ?: PaperStyle.DOTS
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
      Text("Opening…", style = MaterialTheme.typography.bodyLarge, color = c.inkFaint, modifier = Modifier.align(Alignment.Center))
    }

    // Tapping the page closes an open panel instead of drawing.
    if (vm.panel != null) Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { vm.panel = null } })

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 12.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) {
          TitlePill(meta?.title ?: "", onBack = onBack, onRename = { renaming = true })
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          MainToolbar(
            settings = settings,
            tool = vm.tool,
            shapeKind = vm.shapeKind,
            customShape = settings.customShapes.firstOrNull { it.id == vm.customShapeId },
            onPen = vm::onPenTapped,
            onTool = vm::onToolTapped,
            onImage = {
              vm.panel = null
              imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onPdf = {
              vm.panel = null
              pdfPicker.launch(arrayOf("application/pdf"))
            },
          )
          AnimatedVisibility(
            visible = vm.panel != null,
            enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { -it / 12 } + scaleIn(tween(180), initialScale = 0.97f),
            exit = fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.98f),
          ) {
            Column {
              Spacer(Modifier.height(10.dp))
              Panel {
                when (vm.panel) {
                  PanelKind.PEN -> PenPanel(settings, vm)
                  PanelKind.ERASER -> EraserPanel(settings, vm)
                  PanelKind.LASSO -> LassoPanel(settings, vm)
                  PanelKind.SHAPES -> ShapesPanel(settings, vm.shapeKind, vm)
                  null -> Unit
                }
              }
            }
          }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.TopEnd) {
          UndoRedoBar(
            canUndo = vm.canUndo,
            canRedo = vm.canRedo,
            onUndo = { holder.view?.runSafely { undo() } },
            onRedo = { holder.view?.runSafely { redo() } },
          ) {
            MoreMenu(
              paper = meta?.paper ?: PaperStyle.DOTS,
              fingerDraws = settings.fingerDraws,
              onPaper = vm::setPaper,
              onFit = { holder.view?.runSafely { fitContent() } },
              onExport = { exporting = true },
              onFingerDraws = vm::setFingerDraws,
            )
          }
        }
      }
      Spacer(Modifier.weight(1f))
      Box(Modifier.fillMaxWidth()) {
        HintPill(vm.hint, onDone = { vm.hint = null }, modifier = Modifier.align(Alignment.BottomCenter))
        Row(Modifier.align(Alignment.BottomEnd), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          val (page, pages) = vm.page
          if (page >= 0) PageChip(page, pages) { target -> holder.view?.runSafely { goToPage(target) } }
          ZoomChip(vm.zoom) { holder.view?.runSafely { setZoom(1f) } }
        }
      }
    }

    vm.selection?.let { sel ->
      if (vm.panel == null) {
        SelectionMenu(
          sel,
          colors = settings.savedColors,
          onDelete = { holder.view?.runSafely { deleteSelection() } },
          onDuplicate = { holder.view?.runSafely { duplicateSelection() } },
          onColor = { col -> holder.view?.runSafely { recolorSelection(col) } },
          onSaveShape = { savingShape = holder.view?.selectionGeometry() },
        )
      }
    }
  }

  savingShape?.let { geometry ->
    SaveShapeDialog(geometry, onDismiss = { savingShape = null }) { name ->
      vm.saveCustomShape(name, geometry)
      savingShape = null
      holder.view?.runSafely { clearSelection() }
      vm.showHint("Saved to My shapes — hold the pen at the end of a stroke to snap to it")
    }
  }
  if (renaming) {
    RenameDialog(meta?.title ?: "", onDismiss = { renaming = false }) {
      vm.rename(it)
      renaming = false
    }
  }
  if (exporting) {
    ExportDialog(
      hasPdfPages = remember(doc) { doc?.all?.any { it is com.tali.notes.model.ImageItem && it.pdfPage } == true },
      onDismiss = { exporting = false },
      onExport = { layout, share ->
        exporting = false
        vm.showHint("Preparing PDF…")
        scope.launch {
          val file = vm.exportPdf(layout)
          if (file == null) {
            vm.showHint("Couldn't create the PDF")
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
            context.startActivity(Intent.createChooser(send, "Share PDF"))
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
private fun TitlePill(title: String, onBack: () -> Unit, onRename: () -> Unit) {
  val c = Tali.colors
  FloatingBar(Modifier.height(52.dp), crown = CloudCrown.SOFT_EDGE) {
    ToolButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back to notebooks", onClick = onBack)
    Text(
      title,
      style = MaterialTheme.typography.titleSmall,
      color = c.ink,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Rename", onClick = onRename).padding(horizontal = 8.dp, vertical = 6.dp),
    )
  }
}

@Composable
private fun MoreMenu(
  paper: PaperStyle,
  fingerDraws: Boolean?,
  onPaper: (PaperStyle) -> Unit,
  onFit: () -> Unit,
  onExport: () -> Unit,
  onFingerDraws: (Boolean?) -> Unit,
) {
  val c = Tali.colors
  var open by remember { mutableStateOf(false) }
  Box {
    ToolButton(Icons.Outlined.MoreHoriz, "More") { open = true }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
      Text("Paper", style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
      PaperStyle.entries.forEach { style ->
        DropdownMenuItem(
          text = { Text(style.label) },
          leadingIcon = { PaperSwatch(style, Modifier.size(28.dp, 22.dp)) },
          trailingIcon = { if (style == paper) Icon(Icons.Outlined.Check, null, tint = c.accent) },
          onClick = { onPaper(style) },
        )
      }
      HorizontalDivider(color = c.line)
      DropdownMenuItem(text = { Text("Show everything") }, leadingIcon = { Icon(Icons.Outlined.FitScreen, null) }, onClick = {
        open = false
        onFit()
      })
      DropdownMenuItem(text = { Text("Export PDF") }, leadingIcon = { Icon(Icons.Outlined.IosShare, null) }, onClick = {
        open = false
        onExport()
      })
      val fingerLabel = when (fingerDraws) {
        null -> "Automatic"
        true -> "On"
        false -> "Off"
      }
      DropdownMenuItem(
        text = { Text("Draw with finger: $fingerLabel") },
        leadingIcon = { Icon(Icons.Outlined.TouchApp, null) },
        onClick = {
          onFingerDraws(
            when (fingerDraws) {
              null -> true
              true -> false
              false -> null
            }
          )
        },
      )
    }
  }
}

@Composable
private fun ZoomChip(zoom: Float, modifier: Modifier = Modifier, onReset: () -> Unit) {
  val c = Tali.colors
  Box(
    modifier.cloudSurface(12.dp, shadowDepth = 3.dp).clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Reset zoom", onClick = onReset).padding(horizontal = 12.dp, vertical = 7.dp)
  ) {
    Text("${(zoom * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
  }
}

/** "3 / 12" with arrows, for notebooks with imported slides. */
@Composable
private fun PageChip(page: Int, count: Int, onGo: (Int) -> Unit) {
  val c = Tali.colors
  Row(Modifier.cloudSurface(12.dp, shadowDepth = 3.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    PageArrow(Icons.Outlined.KeyboardArrowUp, "Previous page", enabled = page > 0) { onGo(page - 1) }
    Text("${page + 1} / $count", style = MaterialTheme.typography.labelMedium, color = c.inkMuted, modifier = Modifier.padding(horizontal = 2.dp))
    PageArrow(Icons.Outlined.KeyboardArrowDown, "Next page", enabled = page < count - 1) { onGo(page + 1) }
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
      delay(2400)
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

@Composable
private fun SelectionMenu(sel: SelectionInfo, colors: List<Int>, onDelete: () -> Unit, onDuplicate: () -> Unit, onColor: (Int) -> Unit, onSaveShape: () -> Unit) {
  val c = Tali.colors
  val density = LocalDensity.current
  var picking by remember(sel.count) { mutableStateOf(false) }
  Box(
    Modifier.fillMaxSize().layout { measurable, constraints ->
      val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
      val gap = with(density) { 14.dp.roundToPx() }
      // Keep clear of the toolbars along the top.
      val topLimit = with(density) { 128.dp.roundToPx() }
      val bottomLimit = constraints.maxHeight - p.height - gap
      val cx = sel.screenRect.centerX().roundToInt()
      val x = (cx - p.width / 2).coerceIn(gap, constraints.maxWidth - p.width - gap)
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
    FloatingBar(Modifier.height(52.dp), crown = CloudCrown.SOFT_EDGE) {
      if (picking) {
        Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
          colors.take(12).forEach { col ->
            ColorDot(Color(col), selected = false, size = 26.dp) {
              onColor(col)
              picking = false
            }
          }
        }
      } else {
        if (!sel.hasImage || sel.count > 1) ToolButton(Icons.Outlined.Palette, "Colour") { picking = true }
        ToolButton(Icons.Outlined.ContentCopy, "Duplicate", onClick = onDuplicate)
        ToolButton(Icons.Outlined.DeleteOutline, "Delete", onClick = onDelete)
        if (sel.hasDrawing) ToolButton(Icons.Outlined.AutoAwesome, "Save as shape", onClick = onSaveShape)
        Text(if (sel.count == 1) "1 item" else "${sel.count} items", style = MaterialTheme.typography.labelMedium, color = c.inkFaint, modifier = Modifier.padding(horizontal = 8.dp))
      }
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
        Text("Save as shape", style = MaterialTheme.typography.titleLarge, color = c.ink)
        Spacer(Modifier.height(4.dp))
        Text("It will appear in Shapes under My shapes, and holding the pen at the end of a similar stroke will snap to it.", style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
      }
    }
    Spacer(Modifier.height(18.dp))
    TitleField(value, { value = it }, "Name, e.g. I-beam") { onSave(value.text) }
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton("Cancel", onClick = onDismiss)
      PrimaryButton("Save shape") { onSave(value.text) }
    }
  }
}

@Composable
private fun ExportDialog(hasPdfPages: Boolean, onDismiss: () -> Unit, onExport: (PdfLayout, share: Boolean) -> Unit) {
  val c = Tali.colors
  // Slides she imported come back out as the same slides, with her writing on them.
  val options = if (hasPdfPages) listOf(PdfLayout.PDF_PAGES, PdfLayout.A4_PAGES, PdfLayout.ONE_PAGE) else listOf(PdfLayout.A4_PAGES, PdfLayout.ONE_PAGE)
  var layout by remember { mutableStateOf(options.first()) }
  DialogCard(onDismiss, width = 520) {
    Text("Export PDF", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(16.dp))
    Segmented(
      options,
      layout,
      {
        when (it) {
          PdfLayout.PDF_PAGES -> "PDF pages"
          PdfLayout.A4_PAGES -> "A4 pages"
          PdfLayout.ONE_PAGE -> "One long page"
        }
      },
      { layout = it },
      Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Text(
      when (layout) {
        PdfLayout.PDF_PAGES -> "Each page you imported comes back as its own page, with your writing on and around it."
        PdfLayout.A4_PAGES -> "Splits the canvas into printable A4 pages. Empty areas are skipped."
        PdfLayout.ONE_PAGE -> "Keeps the whole canvas on one page, sized to fit everything."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = c.inkMuted,
    )
    Spacer(Modifier.height(24.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton("Cancel", onClick = onDismiss)
      QuietButton("Save to Files") { onExport(layout, false) }
      PrimaryButton("Share") { onExport(layout, true) }
    }
  }
}
