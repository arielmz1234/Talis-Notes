package com.tali.notes

import android.app.Application
import android.content.Context
import com.tali.notes.data.NotesRepository
import com.tali.notes.data.SettingsStore
import com.tali.notes.backup.DriveBackup
import com.tali.notes.ink.Brushes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class TalisApp : Application() {
  lateinit var repository: NotesRepository
    private set

  lateinit var settings: SettingsStore
    private set

  override fun onCreate() {
    super.onCreate()
    CrashLog.install(this)
    Brushes.init(resources)
    repository = NotesRepository(this)
    settings = SettingsStore(this)
    // Every saved edit means the Drive backup has something new to upload.
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { repository.revision.drop(1).collect { DriveBackup.markDirty(this@TalisApp) } }
  }
}

val Context.talisApp: TalisApp
  get() = applicationContext as TalisApp
