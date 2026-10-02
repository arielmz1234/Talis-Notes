package com.tali.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tali.notes.backup.DriveBackup
import com.tali.notes.theme.TaliTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { TaliTheme { MainNavigation() } }
  }

  override fun onStop() {
    super.onStop()
    // She put the tablet down or switched apps: a good moment to back up.
    DriveBackup.backupSoon(this)
  }
}
