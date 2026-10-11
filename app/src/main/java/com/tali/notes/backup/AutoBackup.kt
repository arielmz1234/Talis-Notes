package com.tali.notes.backup

import com.tali.notes.tr
import com.tali.notes.R
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tali.notes.CrashLog
import com.tali.notes.data.NotesRepository
import com.tali.notes.talisApp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Automatic backup into one file she chose once with Android's own "save to" screen: in her Google
 * Drive, OneDrive, Dropbox or on the tablet. The app keeps permission to that one file and replaces
 * it by itself a couple of minutes after she leaves the app, and once a day, only when something
 * changed. No sign-in or Google Cloud setup: the storage app (Drive, say) uploads it.
 *
 * Android only lets an app pick a file in those apps, not a folder, so it's always the same file,
 * replaced each time (Drive keeps earlier versions of it for a while).
 */
object AutoBackup {
  const val FILE_NAME = "Tali's Notes backup.zip"
  private const val SOON = "auto-backup-soon"
  private const val DAILY = "auto-backup-daily"
  private val writing = Mutex()

  data class Status(
    /** The file it backs up into, or null when it's off. */
    val uri: Uri?,
    /** Where that is, for showing her: "Google Drive", "this tablet"… */
    val place: String?,
    val fileName: String?,
    val lastSuccess: Long,
    val error: String?,
    val running: Boolean = false,
  ) {
    val enabled: Boolean
      get() = uri != null
  }

  private var state: MutableStateFlow<Status>? = null

  fun status(context: Context): StateFlow<Status> = flow(context).asStateFlow()

  private fun flow(context: Context): MutableStateFlow<Status> =
    state ?: synchronized(this) { state ?: MutableStateFlow(read(context)).also { state = it } }

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)

  private fun read(context: Context): Status {
    val p = prefs(context)
    return Status(p.getString("uri", null)?.let(Uri::parse), p.getString("place", null), p.getString("name", null), p.getLong("last_success", 0L), p.getString("error", null))
  }

  private fun save(context: Context, transform: (Status) -> Status) {
    val f = flow(context)
    val next = transform(f.value)
    f.value = next
    prefs(context)
      .edit()
      .putString("uri", next.uri?.toString())
      .putString("place", next.place)
      .putString("name", next.fileName)
      .putLong("last_success", next.lastSuccess)
      .putString("error", next.error)
      .apply()
  }

  /** Something changed since the last backup. Called for every saved edit, so it only writes when the flag flips. */
  fun markDirty(context: Context) {
    val p = prefs(context)
    if (!p.getBoolean("dirty", false)) p.edit().putBoolean("dirty", true).apply()
  }

  private fun isDirty(context: Context) = prefs(context).getBoolean("dirty", true)

  // ---- Turning it on and off ----

  /**
   * She picked [uri] in the "save to" screen: keep permission to it, back up into it straight away
   * and from then on by itself. Returns the message to show.
   */
  suspend fun choose(context: Context, uri: Uri): String {
    val resolver = context.contentResolver
    runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
      .onFailure { return tr(R.string.that_place_doesn_t_let_apps) }
    // A different file than before: let go of the old one.
    flow(context).value.uri?.takeIf { it != uri }?.let { old -> runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
    val name = withContext(Dispatchers.IO) { displayName(context, uri) }
    // On a new tablet (or after the app was cleared), picking her old backup file brings her notes
    // back first: saving straight away would replace that backup with an empty one.
    var restored: NotesRepository.MergeResult? = null
    if (context.talisApp.repository.notebooks.value.isEmpty() && withContext(Dispatchers.IO) { sizeOf(context, uri) } > 0) {
      restored =
        try {
          withContext(Dispatchers.IO) { resolver.openInputStream(uri)?.use { BackupArchive.restore(context, it) } }
        } catch (e: BackupArchive.InvalidBackup) {
          return tr(R.string.that_file_isn_t_a_tali)
        } catch (t: Throwable) {
          CrashLog.recovered(context, t)
          return tr(R.string.couldn_t_read_the_backup_in)
        }
    }
    save(context) { it.copy(uri = uri, place = placeOf(uri), fileName = name, error = null) }
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).setConstraints(constraints(batteryNotLow = true)).build())
    val r = restored
    if (r != null) {
      val n = r.added + r.updated
      return (if (n == 1) tr(R.string.brought_back_1_notebook_from_the) else tr(R.string.brought_back_n_notebooks_from_the, n)) + tr(R.string.from_now_on_it_saves_by)
    }
    return if (backupNow(context)) tr(R.string.backed_up_from_now_on_it) else flow(context).value.error ?: tr(R.string.couldn_t_save_the_backup)
  }

  private fun sizeOf(context: Context, uri: Uri): Long =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L }
      }
      .getOrNull() ?: 0L

  /**
   * After restoring from [uri] (picked with the open screen), keeps backing up into that same file
   * when the storage app lets the app write to it. Does nothing if a backup file is already set.
   * Returns whether it did.
   */
  suspend fun adopt(context: Context, uri: Uri): Boolean {
    if (flow(context).value.enabled) return false
    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }.onFailure { return false }
    val name = withContext(Dispatchers.IO) { displayName(context, uri) }
    save(context) { it.copy(uri = uri, place = placeOf(uri), fileName = name, error = null, lastSuccess = 0L) }
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).setConstraints(constraints(batteryNotLow = true)).build())
    return true
  }

  fun turnOff(context: Context) {
    val uri = flow(context).value.uri
    if (uri != null) runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    save(context) { it.copy(uri = null, place = null, fileName = null, error = null, lastSuccess = 0L) }
    val work = WorkManager.getInstance(context)
    work.cancelUniqueWork(DAILY)
    work.cancelUniqueWork(SOON)
  }

  /** Backs up a little later, so a burst of edits becomes one save. */
  fun backupSoon(context: Context, delayMinutes: Long = 2) {
    if (!flow(context).value.enabled) return
    WorkManager.getInstance(context)
      .enqueueUniqueWork(
        SOON,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<Worker>()
          .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
          .build(),
      )
  }

  // Writing to Drive or OneDrive goes through their app, which uploads when it can: no network needed here.
  private fun constraints(batteryNotLow: Boolean) = Constraints.Builder().setRequiresBatteryNotLow(batteryNotLow).build()

  class Worker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      val ctx = applicationContext
      if (!flow(ctx).value.enabled || !isDirty(ctx)) return Result.success()
      return if (backupNow(ctx) || flow(ctx).value.uri == null) Result.success() else Result.retry()
    }
  }

  // ---- The backup itself ----

  /**
   * Writes a fresh backup into her file. The backup is made in the app's own storage first, so the
   * file is only replaced once a complete backup exists. Returns whether it worked.
   */
  suspend fun backupNow(context: Context): Boolean =
    writing.withLock {
      withContext(Dispatchers.IO) {
        val uri = flow(context).value.uri ?: return@withContext false
        save(context) { it.copy(running = true) }
        val tmp = File(context.cacheDir, "auto-backup.zip")
        try {
          // Cleared first: an edit made while saving marks it again.
          prefs(context).edit().putBoolean("dirty", false).apply()
          tmp.outputStream().use { BackupArchive.write(context, it) }
          writeInto(context, uri, tmp)
          save(context) { it.copy(lastSuccess = System.currentTimeMillis(), error = null, running = false) }
          true
        } catch (e: SecurityException) {
          markDirty(context)
          save(context) { it.copy(error = tr(R.string.the_app_can_t_reach_the), running = false) }
          false
        } catch (e: IOException) {
          markDirty(context)
          val gone = e is java.io.FileNotFoundException
          save(context) { it.copy(error = if (gone) tr(R.string.the_backup_file_was_moved_or) else tr(R.string.couldn_t_save_the_backup_will), running = false) }
          false
        } catch (t: Throwable) {
          CrashLog.recovered(context, t)
          markDirty(context)
          save(context) { it.copy(error = tr(R.string.backup_failed_n, t.message ?: t.javaClass.simpleName), running = false) }
          false
        } finally {
          tmp.delete()
        }
      }
    }

  /**
   * Replaces the file's contents with [src]. "wt" empties it first; storage apps that don't offer
   * that get "w" and are cut to length afterwards where possible (a zip with an older, longer
   * backup's tail left on would not open).
   */
  private fun writeInto(context: Context, uri: Uri, src: File) {
    val resolver = context.contentResolver
    val pfd = runCatching { resolver.openFileDescriptor(uri, "wt") }.getOrNull() ?: resolver.openFileDescriptor(uri, "w") ?: throw IOException(tr(R.string.couldn_t_open_the_backup_file))
    pfd.use {
      FileOutputStream(it.fileDescriptor).use { out ->
        src.inputStream().use { input -> input.copyTo(out) }
        out.flush()
        runCatching { out.channel.let { ch -> ch.truncate(ch.position()) } }
      }
    }
  }

  /** Restores from her backup file (merging, like any restore). */
  suspend fun restore(context: Context) =
    withContext(Dispatchers.IO) {
      val uri = flow(context).value.uri ?: throw BackupArchive.InvalidBackup(tr(R.string.no_backup_file_chosen_yet))
      val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: throw BackupArchive.InvalidBackup(tr(R.string.the_app_can_t_open_the))
      input.use { BackupArchive.restore(context, it) }
    }

  private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
      }
      .getOrNull()

  /** A friendly name for the storage app behind [uri]. */
  fun placeOf(uri: Uri): String {
    val a = uri.authority.orEmpty()
    return when {
      a.startsWith("com.google.android.apps.docs") -> "Google Drive"
      a.startsWith("com.microsoft.skydrive") -> "OneDrive"
      a.startsWith("com.dropbox") -> "Dropbox"
      a == "com.android.externalstorage.documents" || a == "com.android.providers.downloads.documents" || a == "com.android.providers.media.documents" -> "this tablet"
      a.contains("samsung") -> "this tablet"
      else -> "the place you chose"
    }
  }
}
