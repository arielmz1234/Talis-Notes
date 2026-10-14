package com.tali.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.drawToBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.tali.notes.backup.DriveBackup
import com.tali.notes.theme.TaliTheme

class MainActivity : ComponentActivity() {
  // Before Android 13, the language chosen in Settings is applied here (see AppLanguage).
  override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(AppLanguage.wrap(newBase))

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Opened with a PDF, picture or backup (not again when the screen turns).
    if (savedInstanceState == null) Incoming.receive(this, intent)
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
      // A change of theme or style fades over from a capture of the screen as it was (ThemeFade).
      androidx.compose.runtime.DisposableEffect(view) {
        com.tali.notes.ui.components.ThemeFade.capture = {
          runCatching { window.decorView.drawToBitmap() }.getOrNull()?.let { com.tali.notes.ui.components.ThemeFade.snapshot = it.asImageBitmap() }
        }
        onDispose { com.tali.notes.ui.components.ThemeFade.capture = null }
      }
      TaliTheme(darkTheme = dark, theme = s.colorTheme) {
        androidx.compose.runtime.CompositionLocalProvider(com.tali.notes.theme.LocalMinimal provides s.minimal) {
          androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            MainNavigation()
            com.tali.notes.ui.components.ThemeFade.snapshot?.let { shot ->
              val fade = androidx.compose.runtime.remember(shot) { androidx.compose.animation.core.Animatable(1f) }
              androidx.compose.runtime.LaunchedEffect(shot) {
                fade.animateTo(0f, androidx.compose.animation.core.tween(450, easing = com.tali.notes.ui.components.EaseOutStrong))
                if (com.tali.notes.ui.components.ThemeFade.snapshot === shot) com.tali.notes.ui.components.ThemeFade.snapshot = null
              }
              androidx.compose.foundation.Image(
                shot,
                null,
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                modifier = androidx.compose.ui.Modifier.fillMaxSize().graphicsLayer { alpha = fade.value },
              )
            }
          }
        }
      }
    }
  }

  // A PDF, picture or backup opened with or shared to the app while it was already open.
  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    Incoming.receive(this, intent)
  }

  // The S Pen's hover and button events go to the notebook canvas from here (see PenEvents).
  override fun dispatchGenericMotionEvent(ev: android.view.MotionEvent): Boolean {
    com.tali.notes.canvas.PenEvents.dispatch(ev)
    return super.dispatchGenericMotionEvent(ev)
  }

  override fun onStart() {
    super.onStart()
    // Back in the app: catch up with anything another device backed up to Drive.
    DriveBackup.syncSoon(this)
  }

  override fun onStop() {
    super.onStop()
    // She put the tablet down or switched apps: back up now, so her other devices have it.
    DriveBackup.backupSoon(this)
    com.tali.notes.backup.AutoBackup.backupSoon(this)
  }
}
