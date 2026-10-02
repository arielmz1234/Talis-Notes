package com.tali.notes

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.tali.notes.data.ScreenLock
import kotlinx.coroutines.launch
import com.tali.notes.backup.DriveBackup
import com.tali.notes.theme.TaliTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { TaliTheme { MainNavigation() } }
    // Her screen lock (set from a notebook's ⋯ menu) applies everywhere, so the library doesn't
    // flip round when she comes back to it.
    lifecycleScope.launch {
      (application as TalisApp).settings.settings.collect { s ->
        val want = when (s.screenLock) {
          ScreenLock.AUTO -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          ScreenLock.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
          ScreenLock.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        if (requestedOrientation != want) requestedOrientation = want
      }
    }
  }

  override fun onStop() {
    super.onStop()
    // She put the tablet down or switched apps: a good moment to back up.
    DriveBackup.backupSoon(this)
  }
}
