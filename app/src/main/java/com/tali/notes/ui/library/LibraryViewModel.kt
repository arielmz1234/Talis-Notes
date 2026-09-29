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

  fun create(title: String, paper: PaperStyle, onCreated: (String) -> Unit) {
    viewModelScope.launch {
      val meta = repo.create(title.ifBlank { "Untitled" }, paper, selectedFolder.value?.takeIf { it != TRASH })
      getApplication<Application>().talisApp.settings.update { it.copy(defaultPaper = paper) }
      onCreated(meta.id)
    }
  }

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

  fun createFolder(name: String) {
    viewModelScope.launch {
      val color = COVER_COLORS[folders.value.size % COVER_COLORS.size]
      val folder = repo.createFolder(name.ifBlank { "New folder" }, color)
      selectedFolder.value = folder.id
    }
  }

  fun renameFolder(id: String, name: String) = repo.renameFolder(id, name.ifBlank { "Folder" })

  fun recolorFolder(id: String, color: Int) = repo.recolorFolder(id, color)

  fun deleteFolder(id: String) {
    if (selectedFolder.value == id) selectedFolder.value = null
    repo.deleteFolder(id)
  }
}
