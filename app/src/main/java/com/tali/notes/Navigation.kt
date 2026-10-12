package com.tali.notes

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.compose.runtime.collectAsState
import com.tali.notes.ui.library.IncomingCard
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
  // A PDF, pictures or a backup from another app: she picks where they go, then that notebook opens
  // (in place of one already open, so Back still goes to the library) and adds them.
  val offer = Incoming.offer.collectAsState().value
  val app = androidx.compose.ui.platform.LocalContext.current.talisApp
  if (offer != null && offer.kind == Incoming.Kind.PDF) {
    // A PDF always opens in a new notebook she's just looking at, until she saves it. It opens on
    // top of whatever was open, so leaving it goes back there.
    androidx.compose.runtime.LaunchedEffect(offer) {
      val id = runCatching { com.tali.notes.ui.library.openPdfDraft(app, offer) }.getOrNull()
      Incoming.offer.value = null
      if (id != null) {
        Incoming.sendTo(id, offer)
        backStack.add(Notebook(id))
      }
    }
  } else if (offer != null) {
    val open = (backStack.lastOrNull() as? Notebook)?.id
    IncomingCard(
      offer,
      openNotebook = open,
      onGo = { id ->
        Incoming.offer.value = null
        Incoming.sendTo(id, offer)
        when {
          id == open -> Unit
          open != null -> backStack[backStack.lastIndex] = Notebook(id)
          else -> backStack.add(Notebook(id))
        }
      },
      // Made into a Picture item of its own (already holding them): just open it.
      onMade = { id ->
        Incoming.offer.value = null
        if (open != null) backStack[backStack.lastIndex] = Notebook(id) else backStack.add(Notebook(id))
      },
      onDismiss = { Incoming.offer.value = null },
    )
  }
  NavDisplay(
    backStack = backStack,
    onBack = back,
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
    // The fade between the library and a notebook: 420 ms, 40% quicker than the default 700.
    transitionSpec = { fadeIn(tween(SCREEN_FADE_MS)) togetherWith fadeOut(tween(SCREEN_FADE_MS)) },
    popTransitionSpec = { fadeIn(tween(SCREEN_FADE_MS)) togetherWith fadeOut(tween(SCREEN_FADE_MS)) },
    predictivePopTransitionSpec = { fadeIn(tween(SCREEN_FADE_MS)) togetherWith fadeOut(tween(SCREEN_FADE_MS)) },
    entryProvider =
      entryProvider {
        entry<Library> { LibraryScreen(onOpen = { id -> backStack.add(Notebook(id)) }) }
        entry<Notebook> { key ->
          // A notebook is laid out the same in every language (toolbars, panels, menus): in Hebrew or
          // Arabic only the words change, so the tools stay where her hand expects them.
          CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            NotebookScreen(
              notebookId = key.id,
              onBack = { if (backStack.lastOrNull() == key) back() },
              // Quick open: this notebook gives way to the other one, so Back still goes to the library.
              onSwitch = { id -> if (backStack.lastOrNull() == key && id != key.id) backStack[backStack.lastIndex] = Notebook(id) },
            )
          }
        }
      },
  )
}

private const val SCREEN_FADE_MS = 420
