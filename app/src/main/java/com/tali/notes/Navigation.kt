package com.tali.notes

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.tali.notes.ui.library.LibraryScreen
import com.tali.notes.ui.notebook.NotebookScreen
import kotlinx.serialization.Serializable

@Serializable data object Library : NavKey

@Serializable data class Notebook(val id: String) : NavKey

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Library)
  val activity = LocalActivity.current
  // The library always stays at the bottom: Back there leaves the app, and a quick double tap on a
  // notebook's back arrow can't pop it too (an empty stack would crash).
  val back: () -> Unit = { if (backStack.size > 1) backStack.removeLastOrNull() else activity?.finish() }
  NavDisplay(
    backStack = backStack,
    onBack = back,
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    entryProvider =
      entryProvider {
        entry<Library> { LibraryScreen(onOpen = { id -> backStack.add(Notebook(id)) }) }
        entry<Notebook> { key -> NotebookScreen(notebookId = key.id, onBack = { if (backStack.lastOrNull() == key) back() }) }
      },
  )
}
