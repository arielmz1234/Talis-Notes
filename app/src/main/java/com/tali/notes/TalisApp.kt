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
fun tr(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
  if (args.isEmpty()) appContext().getString(id) else appContext().getString(id, *args)

/**
 * Names the app makes up (a new notebook's or folder's, "Picture", "Scan Oct 12") are English in
 * every language, like file names: the owner's call, so a library isn't a mix of languages after a
 * switch.
 */
fun english(@androidx.annotation.StringRes id: Int, vararg args: Any): String {
  val c = appContext()
  val conf = android.content.res.Configuration(c.resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) }
  return c.createConfigurationContext(conf).getString(id, *args)
}

/**
 * Words kept for later, such as a backup's saved error or a card's last note: stored by the string's
 * name with its values, and put into words only when shown ([shown]), so they follow a change of
 * language. Pieces can be joined with plain text (", ", " ") like finished text.
 */
fun later(@androidx.annotation.StringRes id: Int, vararg args: Any): String =
  buildString {
    append(START).append(appContext().resources.getResourceEntryName(id))
    // A message inside another (an error wrapped in "Something went wrong: …") is put into words now.
    for (a in args) append(ARG).append(if (a is Int || a is Long) "#" else "$").append(if (a is String) shown(a) else a.toString())
    append(END)
  }

/** [later] words (any number, among plain text) in the language shown now. Plain text passes through. */
fun shown(text: String): String {
  if (START !in text) return text
  val res = appContext().resources
  return Regex("$START([^$END]*)$END").replace(text) { m ->
    val parts = m.groupValues[1].split(ARG)
    val id = res.getIdentifier(parts[0], "string", appContext().packageName)
    if (id == 0) return@replace ""
    val args = parts.drop(1).map<String, Any> { a -> if (a.startsWith("#")) (a.drop(1).toLongOrNull() ?: 0L) else a.drop(1) }
    res.getString(id, *args.toTypedArray())
  }
}

private const val START = '\u2063'
private const val ARG = '\u2064'
private const val END = '\u2065'

/** The language the app is showing (for dates and month names). */
fun appLocale(): java.util.Locale = appContext().resources.configuration.locales[0]

// The app; or, where it isn't Tali's Notes' own (tests), whichever application is running.
private fun appContext(): Context =
  if (::instance.isInitialized) instance
  else Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null) as Context
