package com.tali.notes.ui.library

import androidx.compose.material.icons.outlined.Check
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
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
import androidx.compose.runtime.LaunchedEffect
import com.tali.notes.data.NotesRepository
import com.tali.notes.backup.DriveBackup
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudDone
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.tali.notes.ui.components.PuffBox
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.tali.notes.ui.components.SkyClouds
import com.tali.notes.ui.components.DriftingCloud
import com.tali.notes.ui.components.PaperSwatch
import com.tali.notes.ui.components.Sparkle
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.RulerDivider
import com.tali.notes.ui.components.SectionLabel
import com.tali.notes.ui.components.ShapeIcon
import com.tali.notes.ui.components.relativeTime
import com.tali.notes.ui.components.rememberThumbnail
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import com.tali.notes.ui.components.SlidingSelection
import com.tali.notes.ui.components.rememberSlidingSelection
import com.tali.notes.ui.components.slidingHighlight
import com.tali.notes.ui.components.slidingOption

@Composable
fun LibraryScreen(onOpen: (String) -> Unit) {
  val app = LocalContext.current.applicationContext as Application
  val vm: LibraryViewModel = viewModel { LibraryViewModel(app) }
  val c = Tali.colors
  val notebooks by vm.notebooks.collectAsState()
  val all by vm.allNotebooks.collectAsState()
  val trash by vm.trash.collectAsState()
  var trashAction by remember { mutableStateOf<NotebookMeta?>(null) }
  var emptyingTrash by remember { mutableStateOf(false) }
  val folders by vm.folders.collectAsState()
  val selected by vm.selectedFolder.collectAsState()
  val query by vm.query.collectAsState()
  val settings by vm.settings.collectAsState()
  var creating by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf<NotebookMeta?>(null) }
  var deleting by remember { mutableStateOf<NotebookMeta?>(null) }
  /** A folder being made, and where. */
  var newFolder by remember { mutableStateOf<NewFolderRequest?>(null) }
  var movingFolder by remember { mutableStateOf<Folder?>(null) }
  // Notebooks and folders on their way out (puffing away), and a folder that has just been made.
  var vanishing by remember { mutableStateOf(emptySet<String>()) }
  val puffScope = rememberCoroutineScope()
  fun vanish(id: String, then: () -> Unit) {
    vanishing = vanishing + id
    puffScope.launch {
      kotlinx.coroutines.delay(com.tali.notes.ui.components.PUFF_LEAVE_MS.toLong())
      then()
      vanishing = vanishing - id
    }
  }
  var knownFolders by remember { mutableStateOf<Set<String>?>(null) }
  var freshFolders by remember { mutableStateOf(emptySet<String>()) }
  LaunchedEffect(folders) {
    val ids = folders.map { it.id }.toSet()
    // One new folder is one she just made (not the list loading in).
    knownFolders?.let { old -> (ids - old).let { new -> if (new.size == 1) freshFolders = new } }
    knownFolders = ids
  }
  var renamingFolder by remember { mutableStateOf<Folder?>(null) }
  var deletingFolder by remember { mutableStateOf<Folder?>(null) }
  var backups by remember { mutableStateOf(false) }
  val currentFolder = folders.firstOrNull { it.id == selected }
  val inTrash = selected == LibraryViewModel.TRASH
  // Picking several notebooks at once (to move, duplicate or delete them together).
  var picked by remember { mutableStateOf<Set<String>?>(null) }
  var deletingPicked by remember { mutableStateOf(false) }
  // Changing folder or opening Recently deleted ends picking (and so does unticking the last one).
  LaunchedEffect(selected) { picked = null }
  androidx.activity.compose.BackHandler(enabled = picked != null) { picked = null }
  // Leave Recently deleted once it's empty.
  LaunchedEffect(inTrash, trash.isEmpty()) { if (inTrash && trash.isEmpty()) vm.selectedFolder.value = null }
  val context = LocalContext.current
  var crashReport by remember { mutableStateOf(CrashLog.pendingReport(context)) }

  // One continuous sky across the top of the whole screen; the sidebar and notebooks sit below it.
  // Puffs of deleted notebooks and folders drift over everything here (sidebar, sky) without being cut off.
  com.tali.notes.ui.components.PuffLayerHost(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to c.background, 0.7f to c.background, 1f to c.sidebarEnd))) {
  SkyBand(height = 360.dp)
  SkyClouds(LocalConfiguration.current.screenWidthDp)
  Row(Modifier.fillMaxSize()) {
    Sidebar(
      folders = folders,
      notebooks = all,
      trashCount = trash.size,
      selected = selected,
      onSelect = { vm.selectedFolder.value = it },
      onNewFolder = { newFolder = NewFolderRequest(it) },
      onRenameFolder = { renamingFolder = it },
      onRecolorFolder = vm::recolorFolder,
      onMoveFolder = { movingFolder = it },
      vanishing = vanishing,
      fresh = freshFolders,
      onDeleteFolder = { deletingFolder = it },
      onBackup = { backups = true },
    )
    Box(Modifier.weight(1f).fillMaxHeight()) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 36.dp, end = 36.dp)) {
      Spacer(Modifier.height(112.dp))
      val heading: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
          // Inside a folder within a folder: the way back up, one tap per level.
          val path = folderPath(folders, currentFolder?.id).dropLast(1)
          if (path.isNotEmpty()) {
            Breadcrumb(path, onSelect = { vm.selectedFolder.value = it })
            Spacer(Modifier.height(4.dp))
          }
          Text(if (inTrash) "Recently deleted" else currentFolder?.name ?: "All notebooks", style = MaterialTheme.typography.displayLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Spacer(Modifier.height(6.dp))
          val today = remember { SimpleDateFormat("EEEE, d MMMM", Locale.UK).format(Date()) }
          val count = if (currentFolder == null) all.size else all.count { it.folderId == currentFolder.id }
          val inside = if (currentFolder == null) 0 else folders.count { it.parentId == currentFolder.id }
          val counts = (if (count == 1) "1 notebook" else "$count notebooks") + when (inside) {
            0 -> ""
            1 -> " · 1 folder"
            else -> " · $inside folders"
          }
          Text(
            if (inTrash) "Notebooks stay here for ${NotesRepository.TRASH_DAYS} days, then they're deleted for good"
            else "$counts · $today",
            style = MaterialTheme.typography.bodyLarge,
            color = c.inkMuted,
          )
        }
      }
      val actions: @Composable (Modifier) -> Unit = { mod ->
        androidx.compose.animation.AnimatedContent(
          targetState = picked != null,
          transitionSpec = { (fadeIn(tween(180, delayMillis = 60)) + androidx.compose.animation.slideInVertically(tween(220, easing = com.tali.notes.ui.components.EaseOutStrong)) { it / 3 }).togetherWith(fadeOut(tween(120))) },
          label = "libraryActions",
        ) { picking ->
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (picking) {
              val chosen = picked.orEmpty()
              PickBar(
                count = chosen.size,
                allPicked = notebooks.isNotEmpty() && notebooks.all { it.id in chosen },
                folders = folders,
                onAll = { picked = if (notebooks.all { it.id in chosen }) emptySet() else notebooks.map { it.id }.toSet() },
                onMove = { folder -> chosen.forEach { vm.moveToFolder(it, folder) }; picked = null },
                onDuplicate = { chosen.forEach { vm.duplicate(it) }; picked = null },
                onDelete = { deletingPicked = true },
                onDone = { picked = null },
              )
            } else {
              SearchField(query, onChange = { vm.query.value = it }, modifier = mod)
              Spacer(Modifier.width(12.dp))
              if (inTrash) QuietButton("Empty", color = c.danger) { emptyingTrash = true }
              else {
                if (notebooks.isNotEmpty()) {
                  QuietButton("Select") { picked = emptySet() }
                  Spacer(Modifier.width(8.dp))
                }
                PrimaryButton("New notebook", Icons.Outlined.Add) { creating = true }
              }
            }
          }
        }
      }
      // Held upright there isn't room for all of it on one line: the search and button go underneath.
      if (LocalConfiguration.current.screenWidthDp < 1000) {
        heading(Modifier)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { actions(Modifier.weight(1f)) }
      } else {
        Row(verticalAlignment = Alignment.Bottom) {
          heading(Modifier.weight(1f))
          actions(Modifier.width(240.dp))
        }
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

      // Folders inside the open folder show as tiles above its notebooks.
      val subfolders = if (currentFolder == null || query.isNotBlank()) emptyList() else folders.filter { it.parentId == currentFolder.id }
      when {
        all.isEmpty() && !inTrash && folders.isEmpty() -> EmptyLibrary(onCreate = { creating = true })
        notebooks.isEmpty() && query.isNotBlank() -> Text("No notebooks match “$query”.", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
        notebooks.isEmpty() && subfolders.isEmpty() && currentFolder != null -> EmptyFolder(onCreate = { creating = true }, onNewFolder = { newFolder = NewFolderRequest(currentFolder.id) })
        notebooks.isEmpty() && subfolders.isEmpty() -> EmptyLibrary(onCreate = { creating = true })
        else -> {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(210.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            contentPadding = PaddingValues(bottom = 40.dp),
          ) {
            if (currentFolder != null && !inTrash && query.isBlank()) {
              item(key = "folders-label", span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Folders") }
              items(subfolders, key = { "folder-" + it.id }) { f ->
                PuffBox(leaving = f.id in vanishing, appearing = f.id in freshFolders) {
                FolderTile(
                  f,
                  folders = folders,
                  notebooks = all,
                  onOpen = { vm.selectedFolder.value = f.id },
                  onNewFolder = { newFolder = NewFolderRequest(f.id) },
                  onRename = { renamingFolder = f },
                  onRecolor = { vm.recolorFolder(f.id, it) },
                  onMove = { movingFolder = f },
                  onDelete = { deletingFolder = f },
                )
                }
              }
              item(key = "new-folder") { NewFolderTile { newFolder = NewFolderRequest(currentFolder.id) } }
            }
            if (notebooks.isNotEmpty()) {
              item(key = "notebooks-label", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(if (inTrash) "Tap a notebook to bring it back" else if (currentFolder != null) "Notebooks" else "Recent") }
            }
            items(notebooks, key = { it.id }) { nb ->
              PuffBox(leaving = nb.id in vanishing, appearing = false) {
              val chosen = picked
              NotebookCard(
                nb,
                folders = folders,
                thumbnail = vm.thumbnailFile(nb.id),
                picking = chosen != null,
                picked = chosen?.contains(nb.id) == true,
                onPick = { picked = (picked ?: emptySet()).let { if (nb.id in it) it - nb.id else it + nb.id }.takeIf { it.isNotEmpty() } },
                onOpen = { if (inTrash) trashAction = nb else onOpen(nb.id) },
                onRename = { renaming = nb },
                onDuplicate = { vm.duplicate(nb.id) },
                onCover = { vm.setCover(nb.id, it) },
                onMove = { vm.moveToFolder(nb.id, it) },
                onDelete = { deleting = nb },
                inTrash = inTrash,
              )
              }
            }
          }
        }
      }
    }
    }
  }
  }

  if (creating) {
    NewNotebookDialog(
      settings = settings,
      folderName = currentFolder?.name,
      defaultCover = remember { vm.nextCover() },
      onDismiss = { creating = false },
      importImage = vm::importImage,
      onAddPaper = vm::addCustomPaper,
      onRemovePaper = vm::removeCustomPaper,
    ) {
      creating = false
      vm.create(it, onOpen)
    }
  }
  renaming?.let { nb ->
    RenameDialog(nb.title, onDismiss = { renaming = null }) {
      vm.rename(nb.id, it)
      renaming = null
    }
  }
  if (deletingPicked) {
    val ids = picked.orEmpty()
    ConfirmDialog(
      title = if (ids.size == 1) "Delete 1 notebook?" else "Delete ${ids.size} notebooks?",
      body = "They move to Recently deleted, where you can bring them back for ${NotesRepository.TRASH_DAYS} days.",
      confirm = "Delete",
      destructive = true,
      onDismiss = { deletingPicked = false },
    ) {
      deletingPicked = false
      picked = null
      ids.forEach { id -> vanish(id) { vm.delete(id) } }
    }
  }
  deleting?.let { nb ->
    ConfirmDialog(
      title = "Delete “${nb.title}”?",
      body = "It moves to Recently deleted, where you can bring it back for ${NotesRepository.TRASH_DAYS} days.",
      confirm = "Delete",
      destructive = true,
      onDismiss = { deleting = null },
    ) {
      deleting = null
      vanish(nb.id) { vm.delete(nb.id) }
    }
  }
  if (backups) BackupDialog(onDismiss = { backups = false })
  trashAction?.let { nb ->
    DialogCard(onDismiss = { trashAction = null }, width = 480, dim = false) {
      Text("“${nb.title}”", style = MaterialTheme.typography.titleLarge, color = c.ink)
      Spacer(Modifier.height(8.dp))
      val left = NotesRepository.TRASH_DAYS - ((System.currentTimeMillis() - (nb.deleted ?: 0L)) / 86_400_000L).toInt()
      Text(
        "Deleted ${relativeTime(nb.deleted ?: 0L).replaceFirstChar { it.lowercase() }}. It will be deleted for good in ${left.coerceAtLeast(1)} ${if (left == 1) "day" else "days"}.",
        style = MaterialTheme.typography.bodyMedium,
        color = c.inkMuted,
      )
      Spacer(Modifier.height(24.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
        QuietButton("Delete forever", color = c.danger) {
          vm.deleteForever(nb.id)
          trashAction = null
        }
        PrimaryButton("Restore") {
          vm.restoreFromTrash(nb.id)
          trashAction = null
        }
      }
    }
  }
  if (emptyingTrash) {
    ConfirmDialog(
      title = "Empty Recently deleted?",
      body = if (trash.size == 1) "1 notebook will be deleted for good. This can't be undone." else "${trash.size} notebooks will be deleted for good. This can't be undone.",
      confirm = "Delete forever",
      destructive = true,
      onDismiss = { emptyingTrash = false },
    ) {
      vm.emptyTrash()
      emptyingTrash = false
    }
  }
  newFolder?.let { request ->
    val parentId = request.parentId
    val parentName = folders.firstOrNull { it.id == parentId }?.name
    NewFolderDialog(
      title = if (parentName != null) "New folder in $parentName" else "New folder",
      placeholder = "New folder",
      initialColor = remember(request) { vm.nextFolderColor(parentId) },
      onDismiss = { newFolder = null },
    ) { name, color ->
      vm.createFolder(name, parentId, color)
      newFolder = null
    }
  }
  movingFolder?.let { f ->
    MoveFolderDialog(f, folders, onDismiss = { movingFolder = null }) { parent ->
      vm.moveFolder(f.id, parent)
      movingFolder = null
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
      body = folders.firstOrNull { it.id == f.parentId }.let { parent ->
        if (parent != null) "Nothing inside is deleted. Its notebooks and folders move up into “${parent.name}”."
        else "Nothing inside is deleted. Its notebooks move back to All notebooks, and its folders to the top level."
      },
      confirm = "Delete folder",
      destructive = true,
      onDismiss = { deletingFolder = null },
    ) {
      deletingFolder = null
      vanish(f.id) { vm.deleteFolder(f.id) }
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
  trashCount: Int,
  selected: String?,
  onSelect: (String?) -> Unit,
  onNewFolder: (parentId: String?) -> Unit,
  onRenameFolder: (Folder) -> Unit,
  onRecolorFolder: (String, Int) -> Unit,
  onMoveFolder: (Folder) -> Unit,
  onDeleteFolder: (Folder) -> Unit,
  onBackup: () -> Unit,
  vanishing: Set<String> = emptySet(),
  fresh: Set<String> = emptySet(),
) {
  val c = Tali.colors
  // No panel colour of its own: the sidebar shares the page, so there's no seam to see.
  // One highlight glides between All notebooks, the folders and Recently deleted, taking on each folder's colour.
  val nav = rememberSlidingSelection()
  Box(Modifier.width(268.dp).fillMaxHeight()) {
  Column(
    Modifier.fillMaxSize()
      .slidingHighlight(nav, selected ?: ALL_NOTEBOOKS, c.washBrush, 14.dp)
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
      nav = nav,
      key = ALL_NOTEBOOKS,
      onClick = { onSelect(null) },
    )
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
      SectionLabel("Folders", Modifier.weight(1f))
      Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClickLabel = "New folder") { onNewFolder(null) },
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
          modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onNewFolder(null) }.padding(horizontal = 10.dp, vertical = 6.dp),
        )
      }
      // A tree: folders inside folders sit indented under them, and fold away with the arrow.
      val tree = remember(folders) { folderTree(folders) }
      var collapsed by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
      val openPath = remember(folders, selected) { folderPath(folders, selected).map { it.id }.toSet() }
      tree.forEach { (f, depth) ->
        val hidden = folderPath(folders, f.parentId).any { it.id in collapsed && it.id !in openPath }
        if (hidden) return@forEach
        val hasChildren = folders.any { it.parentId == f.id }
        val expanded = f.id !in collapsed || f.id in openPath
        var menu by remember { mutableStateOf(false) }
        PuffBox(leaving = f.id in vanishing, appearing = f.id in fresh, modifier = Modifier.padding(start = (depth * 16).dp)) {
        Box {
          NavRow(
            wash = folderWash(Color(f.color)),
            icon = {
              Icon(if (selected == f.id) Icons.Outlined.FolderOpen else Icons.Outlined.Folder, null, tint = Color(f.color), modifier = Modifier.size(20.dp))
            },
            label = f.name,
            count = notebooks.count { it.folderId == f.id || it.folderId in descendants(folders, f.id) },
            selected = selected == f.id,
            nav = nav,
            key = f.id,
            onClick = { onSelect(f.id) },
            onLongClick = { menu = true },
            trailing = if (!hasChildren) null else {
              {
                Box(
                  Modifier.size(28.dp).clip(CircleShape).clickable(onClickLabel = if (expanded) "Fold" else "Unfold") {
                    collapsed = if (f.id in collapsed) collapsed - f.id else collapsed + f.id
                  },
                  contentAlignment = Alignment.Center,
                ) {
                  Icon(if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint, modifier = Modifier.size(18.dp))
                }
              }
            },
          )
          FolderMenu(
            f,
            expanded = menu,
            onDismiss = { menu = false },
            onNewFolder = { onNewFolder(f.id) },
            onRename = { onRenameFolder(f) },
            onRecolor = { onRecolorFolder(f.id, it) },
            onMove = { onMoveFolder(f) },
            onDelete = { onDeleteFolder(f) },
          )
        }
        }
      }
    }
    if (trashCount > 0) {
      NavRow(
        icon = { Icon(Icons.Outlined.DeleteOutline, null, tint = if (selected == LibraryViewModel.TRASH) c.blue else c.inkMuted, modifier = Modifier.size(20.dp)) },
        label = "Recently deleted",
        count = trashCount,
        selected = selected == LibraryViewModel.TRASH,
        nav = nav,
        key = LibraryViewModel.TRASH,
        onClick = { onSelect(LibraryViewModel.TRASH) },
      )
      Spacer(Modifier.height(4.dp))
    }
    val context = LocalContext.current
    val drive by DriveBackup.status(context).collectAsState()
    Row(
      Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClickLabel = "Backups", onClick = onBackup).padding(horizontal = 10.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      val onDrive = DriveBackup.available && drive.enabled
      Icon(if (onDrive) Icons.Outlined.CloudDone else Icons.Outlined.CheckCircle, null, tint = c.blue, modifier = Modifier.size(16.dp))
      Spacer(Modifier.width(8.dp))
      Column(Modifier.weight(1f)) {
        Text(if (onDrive) "Backed up to Google Drive" else "Saved on this tablet", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
        Text(
          when {
            onDrive && drive.error != null -> drive.error!!
            onDrive && drive.lastSuccess > 0 -> "Last backup ${relativeTime(drive.lastSuccess)}"
            else -> "Tap for backups"
          },
          style = MaterialTheme.typography.bodySmall,
          color = if (onDrive && drive.error != null) c.danger else c.inkFaint,
        )
      }
      Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Backups", tint = c.inkFaint, modifier = Modifier.size(18.dp))
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

/** The soft gradient behind a selected folder, in that folder's own colour. */
@Composable
private fun folderWash(color: Color): Brush {
  val c = Tali.colors
  return if (c.isDark) Brush.horizontalGradient(listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0.14f)))
  else Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.lerp(Color.White, color, 0.42f), androidx.compose.ui.graphics.lerp(Color.White, color, 0.14f)))
}

/** Naming a new folder and choosing its colour. */
@Composable
private fun NewFolderDialog(title: String, placeholder: String, initialColor: Int, onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
  val c = Tali.colors
  var name by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("")) }
  var color by remember { mutableStateOf(initialColor) }
  DialogCard(onDismiss, width = 480) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(folderWash(Color(color))), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.Folder, null, tint = Color(color), modifier = Modifier.size(24.dp))
      }
      Spacer(Modifier.width(14.dp))
      Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink)
    }
    Spacer(Modifier.height(18.dp))
    TitleField(name, { name = it }, placeholder) { onCreate(name.text, color) }
    Spacer(Modifier.height(16.dp))
    SectionLabel("Colour")
    Spacer(Modifier.height(6.dp))
    Box(Modifier.offset(x = (-12).dp)) { CoverSwatches(current = color) { color = it } }
    Spacer(Modifier.height(22.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
      QuietButton("Cancel", onClick = onDismiss)
      PrimaryButton("Create") { onCreate(name.text, color) }
    }
  }
}

/** The sidebar's "All notebooks" entry, in the same keyspace as folder ids. */
private const val ALL_NOTEBOOKS = "\u0000all"

@Composable
private fun NavRow(icon: @Composable () -> Unit, label: String, count: Int, selected: Boolean, nav: SlidingSelection, key: Any, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, wash: Brush? = null) {
  val c = Tali.colors
  val ink by animateColorAsState(if (selected) c.ink else c.inkMuted, tween(220), label = "navInk")
  Row(
    Modifier.slidingOption(nav, key, wash)
      .fillMaxWidth()
      .height(44.dp)
      .clip(RoundedCornerShape(14.dp))
      .combinedClickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick, onLongClick = onLongClick)
      .padding(start = 12.dp, end = if (trailing != null) 4.dp else 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    icon()
    Spacer(Modifier.width(12.dp))
    Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
    if (trailing != null) {
      Spacer(Modifier.width(2.dp))
      trailing()
    }
  }
}

@Composable
private fun CoverSwatches(current: Int, onPick: (Int) -> Unit) {
  val c = Tali.colors
  val ring = rememberSlidingSelection()
  androidx.compose.foundation.layout.FlowRow(
    Modifier.padding(horizontal = 12.dp, vertical = 6.dp).slidingHighlight(ring, current, SolidColor(c.ink), corner = 100.dp, ring = 2.dp, above = true),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    maxItemsInEachRow = 9,
  ) {
    com.tali.notes.data.PICK_COLORS.forEach { col ->
      Box(Modifier.slidingOption(ring, col).size(26.dp).clip(CircleShape).background(Color(col)).clickable(interactionSource = null, indication = null, role = Role.RadioButton) { onPick(col) })
    }
  }
}

// ---- Main area ----

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
  val c = Tali.colors
  Row(
    modifier.height(48.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
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
  picking: Boolean = false,
  picked: Boolean = false,
  onPick: () -> Unit = {},
  onOpen: () -> Unit,
  onRename: () -> Unit,
  onDuplicate: () -> Unit,
  onCover: (Int) -> Unit,
  onMove: (String?) -> Unit,
  onDelete: () -> Unit,
  inTrash: Boolean = false,
) {
  val c = Tali.colors
  var menu by remember { mutableStateOf(false) }
  var moving by remember { mutableStateOf(false) }
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  // A picked notebook sits a little lower, as if pressed in, with an accent outline.
  val scale by animateFloatAsState(if (pressed) 0.97f else if (picked) 0.955f else 1f, label = "press")
  val image = rememberThumbnail(thumbnail, nb.thumbVersion)
  val shape = RoundedCornerShape(18.dp)
  val edge by androidx.compose.animation.animateColorAsState(if (picked) c.accent else c.line, tween(160), label = "pickEdge")

  Column(
    Modifier.scale(scale).combinedClickable(
      interactionSource = interaction,
      indication = null,
      onClick = if (picking) onPick else onOpen,
      onLongClick = { if (picking) onPick() else if (inTrash) onOpen() else menu = true },
    )
  ) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(1.32f)
        .shadow(12.dp, shape, ambientColor = Color(0x224F7BE8), spotColor = Color(0x337A4ED6))
        .clip(shape)
        .background(c.paper)
        .border(if (picked) 2.5.dp else 1.dp, edge, shape)
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
      // While picking, a check circle in the corner: filled when picked.
      androidx.compose.animation.AnimatedVisibility(
        picking,
        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
        enter = fadeIn(tween(160)) + androidx.compose.animation.scaleIn(tween(200, easing = com.tali.notes.ui.components.EaseOutStrong), initialScale = 0.7f),
        exit = fadeOut(tween(110)) + androidx.compose.animation.scaleOut(tween(110), targetScale = 0.85f),
      ) {
        val fill by androidx.compose.animation.animateColorAsState(if (picked) c.accent else Color.White.copy(alpha = 0.85f), tween(150), label = "pickFill")
        Box(
          Modifier.size(28.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(fill).border(1.5.dp, if (picked) c.accent else c.inkFaint, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          if (picked) Icon(Icons.Outlined.Check, "Picked", tint = Color.White, modifier = Modifier.size(18.dp))
        }
      }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false; moving = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
        if (!moving) {
          DropdownMenuItem(text = { Text("Select") }, leadingIcon = { Icon(Icons.Outlined.CheckCircle, null) }, onClick = { menu = false; onPick() })
          DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; onRename() })
          DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
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
          DropdownMenuItem(text = { Text("Delete", color = c.danger) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = c.danger) }, onClick = { menu = false; onDelete() })
        } else {
          Text("Move to", style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
          DropdownMenuItem(
            text = { Text("No folder") },
            leadingIcon = { Icon(Icons.Outlined.AutoStories, null, tint = c.inkMuted) },
            onClick = { onMove(null); menu = false; moving = false },
          )
          folderTree(folders).forEach { (f, depth) ->
            DropdownMenuItem(
              text = { Text(f.name + if (f.id == nb.folderId) "  (here)" else "") },
              leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = Color(f.color)) },
              onClick = { onMove(f.id); menu = false; moving = false },
              modifier = Modifier.padding(start = (depth * 18).dp),
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
    Text((if (inTrash) "Deleted " else "Edited ") + relativeTime(if (inTrash) nb.deleted ?: nb.updated else nb.updated).replaceFirstChar { it.lowercase() }, style = MaterialTheme.typography.bodySmall, color = c.inkFaint, modifier = Modifier.padding(start = 2.dp))
  }
}

@Composable
private fun EmptyLibrary(onCreate: () -> Unit) {
  val c = Tali.colors
  Column(Modifier.fillMaxWidth().padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(320.dp, 190.dp), contentAlignment = Alignment.Center) {
    DriftingCloud(Modifier.align(Alignment.TopStart).offset(y = 6.dp), width = 96.dp, variant = 1, driftDp = 8f, burstable = true)
    DriftingCloud(Modifier.align(Alignment.BottomEnd).offset(y = (-4).dp), width = 110.dp, variant = 0, driftDp = 10f, periodMs = 12000, burstable = true)
    Sparkle(Modifier.align(Alignment.TopEnd).offset(x = (-30).dp, y = 18.dp), size = 12.dp, color = Sky.S400)
    Box(Modifier.size(200.dp, 150.dp).clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Lilac.L50, Sky.S100)), alpha = if (c.isDark) 0.15f else 1f).padding(14.dp)) {
      // Just the empty engineering page, ready to write on.
      PaperSwatch(PaperStyle.ENGINEERING, Modifier.fillMaxSize())
    }
    }
    Spacer(Modifier.height(20.dp))
    Text("Start your first notebook", style = MaterialTheme.typography.headlineMedium, color = c.ink)
    Spacer(Modifier.height(8.dp))
    Text("A4 pages that keep coming as you write. Everything saves as you go.", style = MaterialTheme.typography.bodyLarge, color = c.inkMuted)
    Spacer(Modifier.height(24.dp))
    PrimaryButton("New notebook", Icons.Outlined.Add, onClick = onCreate)
  }
}

@Composable
private fun EmptyFolder(onCreate: () -> Unit, onNewFolder: () -> Unit) {
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
      QuietButton("Folder inside", onClick = onNewFolder)
      PrimaryButton("New notebook", Icons.Outlined.Add, onClick = onCreate)
    }
  }
}

// ---- Folders inside folders ----

/** A folder she is naming: made inside [parentId], or at the top level when it's null. */
private class NewFolderRequest(val parentId: String?)

/** "All notebooks › Civil ›" above a folder's name; each part goes back up to that level. */
@Composable
private fun Breadcrumb(path: List<Folder>, onSelect: (String?) -> Unit) {
  val c = Tali.colors
  Row(verticalAlignment = Alignment.CenterVertically) {
    val crumb: @Composable (String, String?) -> Unit = { label, id ->
      Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = c.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(id) }.padding(horizontal = 4.dp, vertical = 2.dp),
      )
      Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.inkFaint, modifier = Modifier.size(16.dp))
    }
    crumb("All notebooks", null)
    path.forEach { crumb(it.name, it.id) }
  }
}

@Composable
private fun FolderMenu(
  f: Folder,
  expanded: Boolean,
  onDismiss: () -> Unit,
  onNewFolder: () -> Unit,
  onRename: () -> Unit,
  onRecolor: (Int) -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
) {
  val c = Tali.colors
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
    DropdownMenuItem(text = { Text("New folder inside") }, leadingIcon = { Icon(Icons.Outlined.CreateNewFolder, null) }, onClick = {
      onDismiss()
      onNewFolder()
    })
    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = {
      onDismiss()
      onRename()
    })
    DropdownMenuItem(text = { Text("Move to…") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, null) }, onClick = {
      onDismiss()
      onMove()
    })
    CoverSwatches(current = f.color) {
      onRecolor(it)
      onDismiss()
    }
    HorizontalDivider(color = c.line)
    DropdownMenuItem(text = { Text("Delete folder", color = c.danger) }, leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = c.danger) }, onClick = {
      onDismiss()
      onDelete()
    })
  }
}

/** A folder inside the open folder: its colour, name and what it holds. Hold for its menu. */
@Composable
private fun FolderTile(
  f: Folder,
  folders: List<Folder>,
  notebooks: List<NotebookMeta>,
  onOpen: () -> Unit,
  onNewFolder: () -> Unit,
  onRename: () -> Unit,
  onRecolor: (Int) -> Unit,
  onMove: () -> Unit,
  onDelete: () -> Unit,
) {
  val c = Tali.colors
  var menu by remember { mutableStateOf(false) }
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
  val shape = RoundedCornerShape(18.dp)
  val tint = Color(f.color)
  val count = notebooks.count { it.folderId == f.id }
  val inner = folders.count { it.parentId == f.id }
  Box {
    Row(
      Modifier.scale(scale)
        .fillMaxWidth()
        .height(76.dp)
        .shadow(8.dp, shape, ambientColor = Color(0x224F7BE8), spotColor = Color(0x2A7A4ED6))
        .clip(shape)
        .background(c.surface)
        .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.06f))))
        .border(1.dp, c.line, shape)
        .combinedClickable(interactionSource = interaction, indication = null, onClick = onOpen, onLongClick = { menu = true })
        .padding(horizontal = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.Folder, null, tint = tint, modifier = Modifier.size(30.dp))
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(f.name, style = MaterialTheme.typography.titleMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
          listOfNotNull(
              if (count == 1) "1 notebook" else "$count notebooks",
              when (inner) {
                0 -> null
                1 -> "1 folder"
                else -> "$inner folders"
              },
            )
            .joinToString(" · "),
          style = MaterialTheme.typography.bodySmall,
          color = c.inkFaint,
        )
      }
    }
    FolderMenu(f, menu, { menu = false }, onNewFolder, onRename, onRecolor, onMove, onDelete)
  }
}

@Composable
private fun NewFolderTile(onClick: () -> Unit) {
  val c = Tali.colors
  val shape = RoundedCornerShape(18.dp)
  Row(
    Modifier.fillMaxWidth()
      .height(76.dp)
      .clip(shape)
      .drawBehind {
        drawRoundRect(
          c.inkFaint.copy(alpha = 0.6f),
          cornerRadius = CornerRadius(18.dp.toPx()),
          style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
        )
      }
      .clickable(onClickLabel = "New folder", onClick = onClick)
      .padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(Icons.Outlined.CreateNewFolder, null, tint = c.inkMuted, modifier = Modifier.size(26.dp))
    Spacer(Modifier.width(12.dp))
    Text("New folder", style = MaterialTheme.typography.titleSmall, color = c.inkMuted)
  }
}

/** Where a folder goes: the top level or any folder that isn't inside it. */
@Composable
private fun MoveFolderDialog(f: Folder, folders: List<Folder>, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
  val c = Tali.colors
  val blocked = remember(f, folders) { descendants(folders, f.id) + f.id }
  DialogCard(onDismiss, width = 460) {
    Text("Move “${f.name}”", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(12.dp))
    Column(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
      MoveTarget("Top level", Icons.Outlined.AutoStories, c.inkMuted, depth = 0, current = f.parentId == null) { onMove(null) }
      folderTree(folders).filter { it.first.id !in blocked }.forEach { (target, depth) ->
        MoveTarget(target.name, Icons.Outlined.Folder, Color(target.color), depth = depth + 1, current = target.id == f.parentId) { onMove(target.id) }
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { QuietButton("Cancel", onClick = onDismiss) }
  }
}

@Composable
private fun MoveTarget(label: String, icon: ImageVector, tint: Color, depth: Int, current: Boolean, onClick: () -> Unit) {
  val c = Tali.colors
  Row(
    Modifier.fillMaxWidth()
      .padding(start = (depth * 18).dp)
      .height(44.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(enabled = !current, onClick = onClick)
      .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(12.dp))
    Text(label, style = MaterialTheme.typography.titleSmall, color = if (current) c.inkFaint else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    if (current) Text("Here now", style = MaterialTheme.typography.labelMedium, color = c.inkFaint)
  }
}

/**
 * In place of the search and New notebook while picking: how many are picked, then Select all,
 * Move to folder, Duplicate, Delete and Done.
 */
@Composable
private fun PickBar(
  count: Int,
  allPicked: Boolean,
  folders: List<Folder>,
  onAll: () -> Unit,
  onMove: (String?) -> Unit,
  onDuplicate: () -> Unit,
  onDelete: () -> Unit,
  onDone: () -> Unit,
) {
  val c = Tali.colors
  var moving by remember { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(if (count == 1) "1 selected" else "$count selected", style = MaterialTheme.typography.titleMedium, color = c.ink)
    Spacer(Modifier.width(12.dp))
    QuietButton(if (allPicked) "Select none" else "Select all", onClick = onAll)
    Spacer(Modifier.width(10.dp))
    // The actions together in one frosted pill, so they read as a group on the sky.
    Row(
      Modifier.clip(RoundedCornerShape(16.dp)).background(c.surface.copy(alpha = 0.72f)).padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
    Box {
      com.tali.notes.ui.components.ToolButton(Icons.AutoMirrored.Outlined.DriveFileMove, "Move to folder", enabled = count > 0) { moving = true }
      DropdownMenu(expanded = moving, onDismissRequest = { moving = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
        Text("Move to", style = MaterialTheme.typography.labelSmall, color = c.inkFaint, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        DropdownMenuItem(text = { Text("No folder") }, leadingIcon = { Icon(Icons.Outlined.AutoStories, null, tint = c.inkMuted) }, onClick = { moving = false; onMove(null) })
        folderTree(folders).forEach { (f, depth) ->
          DropdownMenuItem(
            text = { Text(f.name) },
            leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = Color(f.color)) },
            onClick = { moving = false; onMove(f.id) },
            modifier = Modifier.padding(start = (depth * 18).dp),
          )
        }
      }
    }
    com.tali.notes.ui.components.ToolButton(Icons.Outlined.ContentCopy, "Duplicate", enabled = count > 0, onClick = onDuplicate)
    com.tali.notes.ui.components.ToolButton(Icons.Outlined.DeleteOutline, "Delete", enabled = count > 0, onClick = onDelete)
    }
    Spacer(Modifier.width(10.dp))
    PrimaryButton("Done", onClick = onDone)
  }
}
