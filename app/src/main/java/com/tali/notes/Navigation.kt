package com.tali.notes

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tali.notes.ui.components.cloudSurface
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
  // A PDF or pictures from another app open as themselves (a PDF item or a Picture item, built like
  // + › File: its own pages on plain paper), never into a notebook: from a notebook she can add
  // them herself. Until she saves it, it's just for looking (unless *Add them to my notebooks* is
  // on). It takes the place of whatever was open, so Back always goes to the library. A backup asks to restore.
  val offer = Incoming.offer.collectAsState().value
  val app = androidx.compose.ui.platform.LocalContext.current.talisApp
  var opening by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
  if (offer != null && offer.kind != Incoming.Kind.BACKUP) {
    androidx.compose.runtime.LaunchedEffect(offer) {
      val draft = !app.settings.settings.value.pdfsToLibrary
      val id = runCatching {
        if (offer.kind == Incoming.Kind.PDF) {
          opening = com.tali.notes.tr(R.string.opening_pdf)
          // Several PDFs at once are each kept (only one can be looked at); the first opens.
          val many = offer.uris.size > 1
          offer.uris.map { uri ->
            com.tali.notes.Imports.pdf(app, uri, null, draft = draft && !many) { done, total -> opening = com.tali.notes.tr(R.string.adding_pages_n_of_n, done, total) }.id
          }.first()
        } else {
          opening = com.tali.notes.tr(R.string.adding_pictures)
          com.tali.notes.Imports.pictures(app, offer.uris, null, draft)?.id
        }
      }.onFailure { com.tali.notes.CrashLog.recovered(app, it) }.getOrNull()
      opening = null
      Incoming.offer.value = null
      if (id != null) {
        // Only the library stays under it: Back from it always goes home, never to a notebook
        // that happened to be open when the file arrived.
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(Notebook(id))
      }
    }
  } else if (offer != null) {
    IncomingCard(offer, onDismiss = { Incoming.offer.value = null })
  }
  Box(Modifier.fillMaxSize()) {
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
  // While a PDF or pictures from another app are being made into an item: what's happening.
  opening?.let { words -> OpeningPill(words, Modifier.align(Alignment.BottomCenter)) }
  }
}

/** A small frosted pill near the bottom: a turning ring and [words] ("Adding pages… 12 of 59"). */
@Composable
private fun OpeningPill(words: String, modifier: Modifier) {
  val c = com.tali.notes.theme.Tali.colors
  Row(
    modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(bottom = 40.dp).cloudSurface(20.dp).padding(horizontal = 18.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
    Spacer(Modifier.width(12.dp))
    Text(words, style = MaterialTheme.typography.labelLarge, color = c.ink)
  }
}

private const val SCREEN_FADE_MS = 420
