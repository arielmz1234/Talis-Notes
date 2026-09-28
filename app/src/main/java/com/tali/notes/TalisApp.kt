package com.tali.notes

import android.app.Application
import android.content.Context
import com.tali.notes.data.NotesRepository
import com.tali.notes.data.SettingsStore
import com.tali.notes.ink.Brushes

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
  }
}

val Context.talisApp: TalisApp
  get() = applicationContext as TalisApp
