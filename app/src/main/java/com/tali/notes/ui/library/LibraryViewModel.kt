package com.tali.notes.ui.library

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
    combine(repo.notebooks, repo.trash, query, selectedFolder) { list, trash, q, folder ->
        val source = if (folder == TRASH) trash else list.filter { folder == null || it.folderId == folder }
        source.filter { q.isBlank() || it.title.contains(q.trim(), ignoreCase = true) }
      }
      .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  fun thumbnailFile(id: String) = repo.thumbnailFile(id)

  fun create(n: NewNotebook, onCreated: (String) -> Unit) {
    viewModelScope.launch {
      val meta =
        repo.create(
          n.title.ifBlank { "Untitled" },
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

  fun addCustomPaper(paper: com.tali.notes.model.CustomPaper) = getApplication<Application>().talisApp.settings.update { it.copy(customPapers = it.customPapers + paper) }

  fun removeCustomPaper(paper: com.tali.notes.model.CustomPaper) =
    getApplication<Application>().talisApp.settings.update { s -> s.copy(customPapers = s.customPapers.filterNot { it.id == paper.id }, defaultCustomPaper = s.defaultCustomPaper?.takeIf { it != paper.id }) }

  suspend fun importImage(uri: android.net.Uri): String? = repo.importImage(uri)

  fun rename(id: String, title: String) = repo.rename(id, title.ifBlank { "Untitled" })

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
      val folder = repo.createFolder(name.ifBlank { "New folder" }, color, parentId)
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

  fun renameFolder(id: String, name: String) = repo.renameFolder(id, name.ifBlank { "Folder" })

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
