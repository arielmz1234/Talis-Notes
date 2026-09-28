package com.tali.notes.ui.library

import android.app.Application
import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tali.notes.CrashLog
import com.tali.notes.data.COVER_COLORS
import com.tali.notes.data.Folder
import com.tali.notes.data.NotebookMeta
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.ShapeKind
import com.tali.notes.theme.Lilac
import com.tali.notes.theme.Sky
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.SkyBand
import com.tali.notes.ui.components.SkyClouds
import com.tali.notes.ui.components.DriftingCloud
import com.tali.notes.ui.components.PaperSwatch
import com.tali.notes.ui.components.Sparkle
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.RulerDivider
import com.tali.notes.ui.components.SectionLabel
import com.tali.notes.ui.components.ShapeIcon
import com.tali.notes.ui.components.relativeTime
import com.tali.notes.ui.components.rememberThumbnail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibraryScreen(onOpen: (String) -> Unit) {
  val app = LocalContext.current.applicationContext as Application
  val vm: LibraryViewModel = viewModel { LibraryViewModel(app) }
  val c = Tali.colors
  val notebooks by vm.notebooks.collectAsState()
  val all by vm.allNotebooks.collectAsState()
  val folders by vm.folders.collectAsState()
  val selected by vm.selectedFolder.collectAsState()
  val query by vm.query.collectAsState()
  val settings by vm.settings.collectAsState()
  var creating by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<NotebookMeta?>(null) }
  var deleting by remember { mutableStateOf<NotebookMeta?>(null) }
  var newFolder by remember { mutableStateOf(false) }
  var renamingFolder by remember { mutableStateOf<Folder?>(null) }
  var deletingFolder by remember { mutableStateOf<Folder?>(null) }
  val currentFolder = folders.firstOrNull { it.id == selected }
  val context = LocalContext.current
  var crashReport by remember { mutableStateOf(CrashLog.pendingReport(context)) }

  // One continuous sky across the top of the whole screen; the sidebar and notebooks sit below it.
  Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.background, 0.7f to c.background, 1f to c.sidebarEnd))) {
  SkyBand(height = 360.dp)
  SkyClouds()
  Row(Modifier.fillMaxSize()) {
    Sidebar(
      folders = folders,
      notebooks = all,
      selected = selected,
      onSelect = { vm.selectedFolder.value = it },
      onNewFolder = { newFolder = true },
      onRenameFolder = { renamingFolder = it },
      onRecolorFolder = vm::recolorFolder,
      onDeleteFolder = { deletingFolder = it },
    )
    Box(Modifier.weight(1f).fillMaxHeight()) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 36.dp, end = 36.dp)) {
      Spacer(Modifier.height(112.dp))
      Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
          Text(currentFolder?.name ?: "All notebooks", style = MaterialTheme.typography.displayLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(6.dp))
          val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date()) }
          val count = if (currentFolder == null) all.size else all.count { it.folderId == currentFolder.id }
          Text(if (count == 1) "1 notebook · $today" else "$count notebooks · $today", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
        }
        SearchField(query, onChange = { vm.query.value = it })
        Spacer(Modifier.width(12.dp))
        PrimaryButton("New notebook", Icons.Outlined.Add) { creating = true }
      }
      crashReport?.let { report ->
        Spacer(Modifier.height(16.dp))
        CrashBanner(
          onSend = {
            val send = Intent(Intent.ACTION_SEND).apply {
              type = "text/plain"
              putExtra(Intent.EXTRA_SUBJECT, "Tali's Notes crash report")
              putExtra(Intent.EXTRA_TEXT, report)
            }
            context.startActivity(Intent.createChooser(send, "Send crash report"))
            CrashLog.dismiss(context)
            crashReport = null
          },
          onDismiss = {
            CrashLog.dismiss(context)
            crashReport = null
          },
        )
      }
      Spacer(Modifier.height(18.dp))
      RulerDivider()
      Spacer(Modifier.height(22.dp))

      when {
        all.isEmpty() -> EmptyLibrary(onCreate = { creating = true })
        notebooks.isEmpty() && query.isNotBlank() -> Text("No notebooks match “$query”.", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
        notebooks.isEmpty() -> EmptyFolder(onCreate = { creating = true })
        else -> {
          SectionLabel("Recent")
          Spacer(Modifier.height(14.dp))
          LazyVerticalGrid(
            columns = GridCells.Adaptive(210.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
          ) {
            items(notebooks, key = { it.id }) { nb ->
              NotebookCard(
                nb,
                folders = folders,
                thumbnail = vm.thumbnailFile(nb.id),
                onOpen = { onOpen(nb.id) },
                onRename = { renaming = nb },
                onDuplicate = { vm.duplicate(nb.id) },
                onCover = { vm.setCover(nb.id, it) },
                onMove = { vm.moveToFolder(nb.id, it) },
                onDelete = { deleting = nb },
              )
            }
          }
        }
      }
    }
    }
  }
  }

  if (creating) {
    NewNotebookDialog(defaultPaper = settings.defaultPaper, folderName = currentFolder?.name, onDismiss = { creating = false }) { title, paper ->
      creating = false
      vm.create(title, paper, onOpen)
    }
  }
  renaming?.let { nb ->
    RenameDialog(nb.title, onDismiss = { renaming = null }) {
      vm.rename(nb.id, it)
      renaming = null
    }
  }
  deleting?.let { nb ->
    ConfirmDialog(
      title = "Delete “${nb.title}”?",
      body = "This removes the notebook from this tablet. Older copies stay in your backups.",
      confirm = "Delete",
      destructive = true,
      onDismiss = { deleting = null },
    ) {
      vm.delete(nb.id)
      deleting = null
    }
  }
  if (newFolder) {
    NameDialog(title = "New folder", initial = "", placeholder = "Statics", confirm = "Create", onDismiss = { newFolder = false }) {
      vm.createFolder(it)
      newFolder = false
    }
  }
  renamingFolder?.let { f ->
    NameDialog(title = "Rename folder", initial = f.name, placeholder = "Folder name", confirm = "Rename", onDismiss = { renamingFolder = null }) {
      vm.renameFolder(f.id, it)
      renamingFolder = null
    }
  }
  deletingFolder?.let { f ->
    ConfirmDialog(
      title = "Delete the “${f.name}” folder?",
      body = "The notebooks inside aren't deleted. They move back to All notebooks.",
      confirm = "Delete folder",
      destructive = true,
      onDismiss = { deletingFolder = null },
    ) {
      vm.deleteFolder(f.id)
      deletingFolder = null
    }
  }
}

@Composable
private fun CrashBanner(onSend: () -> Unit, onDismiss: () -> Unit) {
  val c = Tali.colors
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.horizontalGradient(listOf(c.accentSoft, c.blueSoft))).border(1.dp, Lilac.L300, RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text("Tali's Notes closed unexpectedly last time", style = MaterialTheme.typography.titleSmall, color = c.ink)
      Text("Your notebooks are saved. Sending the report helps fix what went wrong.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
    Text("Dismiss", style = MaterialTheme.typography.labelLarge, color = c.inkMuted, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(10.dp))
    Spacer(Modifier.width(4.dp))
    PrimaryButton("Send report", onClick = onSend)
  }
}

// ---- Sidebar ----

@Composable
private fun Sidebar(
  folders: List<Folder>,
  notebooks: List<NotebookMeta>,
  selected: String?,
  onSelect: (String?) -> Unit,
  onNewFolder: () -> Unit,
  onRenameFolder: (Folder) -> Unit,
  onRecolorFolder: (String, Int) -> Unit,
  onDeleteFolder: (Folder) -> Unit,
) {
  val c = Tali.colors
  // No panel colour of its own: the sidebar shares the page, so there's no seam to see.
  Box(Modifier.width(268.dp).fillMaxHeight()) {
  Column(
    Modifier.fillMaxSize()
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(horizontal = 18.dp)
  ) {
    Spacer(Modifier.height(120.dp))
    Wordmark(Modifier.padding(start = 8.dp))
    Spacer(Modifier.height(30.dp))
    NavRow(
      icon = { Icon(Icons.Outlined.AutoStories, null, tint = if (selected == null) c.blue else c.inkMuted, modifier = Modifier.size(20.dp)) },
      label = "All notebooks",
      count = notebooks.size,
      selected = selected == null,
      onClick = { onSelect(null) },
    )
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
      SectionLabel("Folders", Modifier.weight(1f))
      Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClickLabel = "New folder", onClick = onNewFolder),
        contentAlignment = Alignment.Center,
      ) {
        Icon(Icons.Outlined.Add, "New folder", tint = c.inkMuted, modifier = Modifier.size(18.dp))
      }
    }
    Spacer(Modifier.height(6.dp))
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
      if (folders.isEmpty()) {
        Text(
          "Make a folder for each course, like Statics or Hydraulics.",
          style = MaterialTheme.typography.bodySmall,
          color = c.inkFaint,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
          "New folder",
          style = MaterialTheme.typography.labelLarge,
          color = c.accent,
          modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onNewFolder).padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
      folders.forEach { f ->
        var menu by remember { mutableStateOf(false) }
        Box {
          NavRow(
            icon = {
              Icon(if (selected == f.id) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = Color(f.color), modifier = Modifier.size(20.dp))
            },
            label = f.name,
            count = notebooks.count { it.folderId == f.id },
            selected = selected == f.id,
            onClick = { onSelect(f.id) },
            onLongClick = { menu = true },
          )
          DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = {
              menu = false
              onRenameFolder(f)
            })
            CoverSwatches(current = f.color) {
              onRecolorFolder(f.id, it)
              menu = false
            }
            DropdownMenuItem(text = { Text("Delete folder", color = c.danger) }, onClick = {
              menu = false
              onDeleteFolder(f)
            })
          }
        }
      }
    }
    Row(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
      Icon(Icons.Outlined.CheckCircle, null, tint = c.blue, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(8.dp))
      Column {
        Text("Saved on this tablet", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
        Text("Every stroke saves as you write", style = MaterialTheme.typography.bodySmall, color = c.inkFaint)
      }
    }
  }
  }
}

/** "Tali's Notes" with a lilac highlighter swipe behind "Notes". */
@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
  val c = Tali.colors
  Box(modifier) {
    Canvas(Modifier.matchParentSize()) {
      val h = size.height * 0.42f
      val top = size.height * 0.52f
      val left = size.width * 0.44f
      val path = Path().apply {
        moveTo(left, top + h * 0.15f)
        lineTo(size.width + 6.dp.toPx(), top)
        lineTo(size.width + 4.dp.toPx(), top + h)
        lineTo(left - 2.dp.toPx(), top + h * 1.05f)
        close()
      }
      drawPath(path, Brush.horizontalGradient(listOf(if (c.isDark) c.lilac else Lilac.L300, if (c.isDark) c.sky else Sky.S200), startX = left, endX = size.width))
    }
    Text("Tali's Notes", style = MaterialTheme.typography.headlineMedium, color = c.ink)
  }
}

@Composable
private fun NavRow(icon: @Composable () -> Unit, label: String, count: Int, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
  val c = Tali.colors
  Row(
    Modifier.fillMaxWidth()
      .height(44.dp)
      .clip(RoundedCornerShape(14.dp))
      .background(if (selected) c.washBrush else SolidColor(Color.Transparent))
      .combinedClickable(role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    icon()
    Spacer(Modifier.width(12.dp))
    Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) c.ink else c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
  }
}

@Composable
private fun CoverSwatches(current: Int, onPick: (Int) -> Unit) {
  val c = Tali.colors
  Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    COVER_COLORS.forEach { col ->
      Box(
        Modifier.size(26.dp).clip(CircleShape).background(Color(col))
          .then(if (col == current) Modifier.border(2.dp, c.ink, CircleShape) else Modifier)
          .clickable { onPick(col) }
      )
    }
  }
}

// ---- Main area ----

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
  val c = Tali.colors
  Row(
    Modifier.width(240.dp).height(48.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.Search, null, tint = c.inkFaint, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(10.dp))
    Box(Modifier.weight(1f)) {
      if (value.isEmpty()) Text("Search notebooks", style = MaterialTheme.typography.bodyMedium, color = c.inkFaint)
      BasicTextField(
        value,
        onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.ink),
        cursorBrush = SolidColor(c.accent),
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (value.isNotEmpty()) {
      Icon(Icons.Outlined.Close, "Clear search", tint = c.inkMuted, modifier = Modifier.size(18.dp).clickable { onChange("") })
    }
  }
}

@Composable
private fun NotebookCard(
  nb: NotebookMeta,
  folders: List<Folder>,
  thumbnail: java.io.File,
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDuplicate: () -> Unit,
  onCover: (Int) -> Unit,
  onMove: (String?) -> Unit,
  onDelete: () -> Unit,
) {
  val c = Tali.colors
  var menu by remember { mutableStateOf(false) }
  var moving by remember { mutableStateOf(false) }
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
  val image = rememberThumbnail(thumbnail, nb.thumbVersion)
  val shape = RoundedCornerShape(18.dp)

  Column(Modifier.scale(scale).combinedClickable(interactionSource = interaction, indication = null, onClick = onOpen, onLongClick = { menu = true })) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(1.32f)
        .shadow(12.dp, shape, ambientColor = Color(0x224F7BE8), spotColor = Color(0x337A4ED6))
        .clip(shape)
        .background(c.paper)
        .border(1.dp, c.line, shape)
    ) {
      if (image != null) {
        Image(image, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
      } else {
        PaperSwatch(nb.paper, Modifier.fillMaxSize())
      }
      // Notebook spine in the cover colour, with a stitched edge.
      Box(Modifier.fillMaxHeight().width(9.dp).background(Color(nb.cover)))
      Canvas(Modifier.fillMaxHeight().width(9.dp)) {
        var y = 6.dp.toPx()
        while (y < size.height) {
          drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(size.width / 2 - 0.75.dp.toPx(), y), Size(1.5.dp.toPx(), 5.dp.toPx()), CornerRadius(1.dp.toPx()))
          y += 11.dp.toPx()
        }
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false; moving = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
        if (!moving) {
          DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
          DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
          DropdownMenuItem(
            text = { Text("Move to folder") },
            leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) },
            onClick = { moving = true },
          )
          CoverSwatches(current = nb.cover) {
            onCover(it)
            menu = false
          }
          HorizontalDivider(color = c.line)
          DropdownMenuItem(text = { Text("Delete", color = c.danger) }, onClick = { menu = false; onDelete() })
        } else {
          Text("Move to", style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
          DropdownMenuItem(
            text = { Text("No folder") },
            leadingIcon = { Icon(Icons.Outlined.AutoStories, null, tint = c.inkMuted) },
            onClick = { onMove(null); menu = false; moving = false },
          )
          folders.forEach { f ->
            DropdownMenuItem(
              text = { Text(f.name + if (f.id == nb.folderId) "  (here)" else "") },
              leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = Color(f.color)) },
              onClick = { onMove(f.id); menu = false; moving = false },
            )
          }
          if (folders.isEmpty()) {
            Text("No folders yet. Add one from the sidebar.", style = MaterialTheme.typography.bodySmall, color = c.inkMuted, modifier = Modifier.padding(16.dp))
          }
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(nb.title, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 2.dp))
    Text("Edited " + relativeTime(nb.updated).replaceFirstChar { it.lowercase() }, style = MaterialTheme.typography.bodySmall, color = c.inkFaint, modifier = Modifier.padding(start = 2.dp))
  }
}

@Composable
private fun EmptyLibrary(onCreate: () -> Unit) {
  val c = Tali.colors
  Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(320.dp, 190.dp), contentAlignment = Alignment.Center) {
    DriftingCloud(Modifier.align(Alignment.TopStart).offset(y = 6.dp), width = 96.dp, variant = 1, driftDp = 8f)
    DriftingCloud(Modifier.align(Alignment.BottomEnd).offset(y = (-4).dp), width = 110.dp, variant = 0, driftDp = 10f, periodMs = 12000)
    Sparkle(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 18.dp), size = 12.dp, color = Sky.S400)
    Box(Modifier.size(200.dp, 150.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Lilac.L50, Sky.S100)), alpha = if (c.isDark) 0.15f else 1f).padding(14.dp)) {
      PaperSwatch(PaperStyle.ENGINEERING, Modifier.fillMaxSize())
      Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ShapeIcon(ShapeKind.PIN_SUPPORT, c.ink, size = 44.dp)
        ShapeIcon(ShapeKind.POINT_LOAD, c.blue, size = 44.dp)
        ShapeIcon(ShapeKind.ROLLER_SUPPORT, c.ink, size = 44.dp)
      }
    }
    }
    Spacer(Modifier.height(20.dp))
    Text("Start your first notebook", style = MaterialTheme.typography.headlineMedium, color = c.ink)
    Spacer(Modifier.height(8.dp))
    Text("One endless page per notebook. Everything saves as you write.", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
    Spacer(Modifier.height(24.dp))
    PrimaryButton("New notebook", Icons.Outlined.Add, onClick = onCreate)
  }
}

@Composable
private fun EmptyFolder(onCreate: () -> Unit) {
  val c = Tali.colors
  Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.FolderOpen, null, tint = Sky.S400, modifier = Modifier.size(64.dp).offset(y = 18.dp))
      DriftingCloud(Modifier.offset(x = 34.dp, y = (-22).dp), width = 72.dp, variant = 2, driftDp = 6f)
    }
    Spacer(Modifier.height(16.dp))
    Text("This folder is empty", style = MaterialTheme.typography.headlineMedium, color = c.ink)
    Spacer(Modifier.height(8.dp))
    Text("Create a notebook here, or move one in from its menu (press and hold a notebook).", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
    Spacer(Modifier.height(24.dp))
    PrimaryButton("New notebook", Icons.Outlined.Add, onClick = onCreate)
  }
}
