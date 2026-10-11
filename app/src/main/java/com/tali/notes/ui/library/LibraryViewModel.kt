package com.tali.notes.ui.library

import com.tali.notes.tr
import com.tali.notes.R
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tali.notes.data.COVER_COLORS
import com.tali.notes.data.Folder
import com.tali.notes.data.NotebookMeta
import com.tali.notes.model.PaperStyle
import com.tali.notes.talisApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
  private val repo = app.talisApp.repository
  val settings = app.talisApp.settings.settings

  val query = MutableStateFlow("")

  /** null shows every notebook; otherwise the id of the open folder, or [TRASH]. */
  val selectedFolder = MutableStateFlow<String?>(null)

  val folders: StateFlow<List<Folder>> = repo.folders

  val allNotebooks: StateFlow<List<NotebookMeta>> = repo.notebooks

  val trash: StateFlow<List<NotebookMeta>> = repo.trash

  val notebooks: StateFlow<List<NotebookMeta>> =
    combine(repo.notebooks, repo.trash, query, selectedFolder, settings) { list, trash, q, folder, s ->
        // Recently deleted stays in the order things were deleted; the rest follow her sort.
        val source = if (folder == TRASH) trash else sortNotebooks(list.filter { folder == null || it.folderId == folder }, s.librarySort, s.librarySortReversed, s.recentNotebooks)
        source.filter { q.isBlank() || it.title.contains(q.trim(), ignoreCase = true) }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  fun thumbnailFile(id: String) = repo.thumbnailFile(id)

  /** Sorts the library by [sort]; choosing the sort already in use turns it the other way round. */
  fun sortBy(sort: com.tali.notes.data.LibrarySort) =
    getApplication<Application>().talisApp.settings.update { s ->
      if (s.librarySort == sort) s.copy(librarySortReversed = !s.librarySortReversed) else s.copy(librarySort = sort, librarySortReversed = false)
    }

  /**
   * What a new notebook is called when she leaves the name empty: "New notebook", or the next free
   * "New notebook 2" among the notebooks in the folder it goes into. (Her own names may repeat.)
   */
  fun freeNotebookName(): String {
    val folder = selectedFolder.value?.takeIf { it != TRASH }
    return nextFreeName(tr(R.string.new_notebook), repo.notebooks.value.filter { it.folderId == folder }.map { it.title })
  }

  /** The same for a new folder, among the folders beside it. */
  fun freeFolderName(parentId: String?): String = nextFreeName(tr(R.string.new_folder), folders.value.filter { it.parentId == parentId }.map { it.name })

  fun create(n: NewNotebook, onCreated: (String) -> Unit) {
    viewModelScope.launch {
      val meta =
        repo.create(
          n.title.ifBlank { freeNotebookName() },
          n.paper,
          selectedFolder.value?.takeIf { it != TRASH },
          pages = if (n.paged) 1 else null,
          customPaper = n.customPaper,
          dark = n.dark,
          cover = n.cover,
          landscape = n.landscape,
        )
      // The next notebook starts the same way.
      getApplication<Application>().talisApp.settings.update { it.copy(defaultPaper = n.paper, defaultCustomPaper = n.customPaper, defaultPaged = n.paged, defaultLandscape = if (n.paged) n.landscape else it.defaultLandscape) }
      onCreated(meta.id)
    }
  }

  /** The cover the next notebook gets unless she picks another. */
  fun nextCover(): Int = COVER_COLORS[allNotebooks.value.size % COVER_COLORS.size]

  fun setThemeMode(mode: com.tali.notes.data.ThemeMode) = getApplication<Application>().talisApp.settings.update { it.copy(themeMode = mode) }

  fun setMinimal(on: Boolean) = getApplication<Application>().talisApp.settings.update { it.copy(minimal = on) }

  fun addCustomPaper(paper: com.tali.notes.model.CustomPaper) = getApplication<Application>().talisApp.settings.update { it.copy(customPapers = it.customPapers + paper) }

  fun removeCustomPaper(paper: com.tali.notes.model.CustomPaper) =
    getApplication<Application>().talisApp.settings.update { s -> s.copy(customPapers = s.customPapers.filterNot { it.id == paper.id }, defaultCustomPaper = s.defaultCustomPaper?.takeIf { it != paper.id }) }

  suspend fun importImage(uri: android.net.Uri): String? = repo.importImage(uri)

  fun rename(id: String, title: String) = repo.rename(id, title.ifBlank { tr(R.string.untitled) })

  fun setCover(id: String, color: Int) = repo.setCover(id, color)

  fun delete(id: String) = repo.delete(id)

  fun restoreFromTrash(id: String) = repo.restoreFromTrash(id)

  fun deleteForever(id: String) = repo.deleteForever(id)

  fun emptyTrash() {
    selectedFolder.value = null
    repo.emptyTrash()
  }

  companion object {
    /** The "folder" id that shows Recently deleted. */
    const val TRASH = "trash"
  }

  fun duplicate(id: String) {
    viewModelScope.launch { repo.duplicate(id) }
  }

  fun moveToFolder(id: String, folderId: String?) = repo.moveToFolder(id, folderId)

  /**
   * Makes a folder inside [parentId] (or at the top level). A top-level folder opens straight away;
   * one made inside a folder appears among that folder's folders, so she can make several.
   */
  fun createFolder(name: String, parentId: String? = null, chosen: Int? = null) {
    viewModelScope.launch {
      val color = chosen ?: parentId?.let { p -> folders.value.firstOrNull { it.id == p }?.color } ?: COVER_COLORS[folders.value.size % COVER_COLORS.size]
      val folder = repo.createFolder(name.ifBlank { freeFolderName(parentId) }, color, parentId)
      if (parentId == null) selectedFolder.value = folder.id
    }
  }

  /** Moves a folder into [parentId]; a folder can't go inside itself or one of its own folders. */
  /** The colour a new folder in [parentId] starts with: its parent's, or the next cover colour. */
  fun nextFolderColor(parentId: String?): Int = parentId?.let { p -> folders.value.firstOrNull { it.id == p }?.color } ?: COVER_COLORS[folders.value.size % COVER_COLORS.size]

  fun moveFolder(id: String, parentId: String?) {
    if (parentId != null && (parentId == id || parentId in descendants(folders.value, id))) return
    repo.moveFolder(id, parentId)
  }

  fun renameFolder(id: String, name: String) = repo.renameFolder(id, name.ifBlank { tr(R.string.folder) })

  fun recolorFolder(id: String, color: Int) = repo.recolorFolder(id, color)

  fun deleteFolder(id: String) {
    if (selectedFolder.value == id) selectedFolder.value = folders.value.firstOrNull { it.id == id }?.parentId
    repo.deleteFolder(id)
  }
}

/** Folders in tree order (each followed by its own folders), with how deep each one sits. */
fun folderTree(folders: List<Folder>): List<Pair<Folder, Int>> {
  val children = folders.groupBy { f -> f.parentId?.takeIf { p -> folders.any { it.id == p } } }
  val out = ArrayList<Pair<Folder, Int>>(folders.size)
  val seen = HashSet<String>()
  fun walk(parent: String?, depth: Int) {
    children[parent].orEmpty().forEach { f ->
      if (seen.add(f.id)) {
        out += f to depth
        walk(f.id, depth + 1)
      }
    }
  }
  walk(null, 0)
  return out
}

/** Ids of every folder inside [id], at any depth. */
fun descendants(folders: List<Folder>, id: String): Set<String> {
  val out = LinkedHashSet<String>()
  var frontier = listOf(id)
  while (frontier.isNotEmpty()) {
    frontier = folders.filter { it.parentId in frontier && out.add(it.id) }.map { it.id }
  }
  return out
}

/** The folders from the top level down to [id], [id] included. */
fun folderPath(folders: List<Folder>, id: String?): List<Folder> {
  val out = ArrayList<Folder>()
  var cur = folders.firstOrNull { it.id == id }
  while (cur != null && out.none { it.id == cur!!.id }) {
    out.add(0, cur)
    cur = folders.firstOrNull { it.id == cur!!.parentId }
  }
  return out
}

/** [base] if no name in [taken] is it (ignoring case and spaces at the ends), else "[base] 2", "[base] 3"… */
internal fun nextFreeName(base: String, taken: Collection<String>): String = com.tali.notes.data.numberedName(base, taken)

/** Notebooks in [sort] order: newest first (A to Z for names), or the other way round when [reversed]. */
internal fun sortNotebooks(list: List<NotebookMeta>, sort: com.tali.notes.data.LibrarySort, reversed: Boolean, opened: List<String>): List<NotebookMeta> {
  val base =
    when (sort) {
      com.tali.notes.data.LibrarySort.EDITED -> list.sortedByDescending { it.updated }
      com.tali.notes.data.LibrarySort.CREATED -> list.sortedByDescending { it.created }
      // Ones she hasn't opened since this was kept come after, newest edited first.
      com.tali.notes.data.LibrarySort.OPENED -> {
        val rank = opened.withIndex().associate { it.value to it.index }
        list.sortedWith(compareBy<NotebookMeta> { rank[it.id] ?: Int.MAX_VALUE }.thenByDescending { it.updated })
      }
      com.tali.notes.data.LibrarySort.NAME -> list.sortedWith { a, b -> naturalCompare(a.title, b.title) }
    }
  return if (reversed) base.reversed() else base
}

/** Compares names the way people read them: ignoring case, and "Notebook 2" before "Notebook 10". */
internal fun naturalCompare(a: String, b: String): Int {
  val chunk = Regex("\\d+|\\D+")
  val x = chunk.findAll(a.trim().lowercase()).map { it.value }.toList()
  val y = chunk.findAll(b.trim().lowercase()).map { it.value }.toList()
  for (i in 0 until minOf(x.size, y.size)) {
    val p = x[i]
    val q = y[i]
    val c =
      if (p[0].isDigit() && q[0].isDigit()) p.trimStart('0').length.compareTo(q.trimStart('0').length).takeIf { it != 0 } ?: p.trimStart('0').compareTo(q.trimStart('0'))
      else p.compareTo(q)
    if (c != 0) return c
  }
  return x.size.compareTo(y.size)
}
