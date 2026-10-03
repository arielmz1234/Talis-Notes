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
