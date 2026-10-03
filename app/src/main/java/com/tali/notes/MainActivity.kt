package com.tali.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tali.notes.backup.DriveBackup
import com.tali.notes.theme.TaliTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      // Light, dark or following the tablet, and the cloudy or minimal look, from Settings.
      val s by talisApp.settings.settings.collectAsState()
      val dark = when (s.themeMode) {
        com.tali.notes.data.ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        com.tali.notes.data.ThemeMode.LIGHT -> false
        com.tali.notes.data.ThemeMode.DARK -> true
      }
      // The status bar's icons match the chosen theme, not the tablet's.
      val view = androidx.compose.ui.platform.LocalView.current
      androidx.compose.runtime.SideEffect { androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark }
      TaliTheme(darkTheme = dark) {
        androidx.compose.runtime.CompositionLocalProvider(com.tali.notes.theme.LocalMinimal provides s.minimal) { MainNavigation() }
      }
    }
  }

  // The S Pen's hover and button events go to the notebook canvas from here (see PenEvents).
  override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
    com.tali.notes.canvas.PenEvents.dispatch(ev)
    return super.dispatchGenericMotionEvent(ev)
  }

  override fun onStop() {
    super.onStop()
    // She put the tablet down or switched apps: a good moment to back up.
    DriveBackup.backupSoon(this)
  }
}
