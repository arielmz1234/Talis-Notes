package com.tali.notes

import android.app.Application
import android.content.Context
import com.tali.notes.data.NotesRepository
import com.tali.notes.data.SettingsStore
import com.tali.notes.backup.AutoBackup
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

  // Before Android 13, the language chosen in Settings applies to the whole app (see AppLanguage).
  override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLanguage.wrap(base))

  override fun onCreate() {
    super.onCreate()
    instance = this
    CrashLog.install(this)
    Brushes.init(resources)
    repository = NotesRepository(this)
    settings = SettingsStore(this)
    // Every saved edit means the backups have something new to save.
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
      repository.revision.drop(1).collect {
        DriveBackup.markDirty(this@TalisApp)
        AutoBackup.markDirty(this@TalisApp)
      }
    }
  }
}

val Context.talisApp: TalisApp
  get() = applicationContext as TalisApp

private lateinit var instance: TalisApp

/**
 * Text from the app's strings in the language she chose, for code outside the screens (hints from
 * the canvas, backup messages, shape names). On screen, prefer `stringResource`.
 */
fun tr(@androidx.annotation.StringRes id: Int, vararg args: Any): String = appContext().getString(id, *args)

/** The language the app is showing (for dates and month names). */
fun appLocale(): java.util.Locale = appContext().resources.configuration.locales[0]

// The app; or, where it isn't Tali's Notes' own (tests), whichever application is running.
private fun appContext(): Context =
  if (::instance.isInitialized) instance
  else Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Context
