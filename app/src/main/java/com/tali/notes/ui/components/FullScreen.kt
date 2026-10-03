package com.tali.notes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Whether the notebook is in full screen (system bars hidden), for dialogs opened over it. */
val LocalFullScreen = staticCompositionLocalOf { false }

/**
 * Call inside a dialog: a dialog is a window of its own, and Android shows the system bars for it
 * unless it asks otherwise, which knocked the notebook out of full screen whenever a confirmation
 * opened. In full screen, this hides them for the dialog's window too.
 */
@Composable
fun KeepFullScreen() {
  if (!LocalFullScreen.current) return
  val view = LocalView.current
  val window = (view.parent as? DialogWindowProvider)?.window ?: return
  SideEffect {
    WindowCompat.getInsetsController(window, view).apply {
      systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      hide(WindowInsetsCompat.Type.systemBars())
    }
  }
}
