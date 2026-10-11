package com.tali.notes.backup

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
      .onFailure { return "That place doesn't let apps keep saving to a file. Try Google Drive or the tablet." }
    // A different file than before: let go of the old one.
    flow(context).value.uri?.takeIf { it != uri }?.let { old -> runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } }
    val name = withContext(Dispatchers.IO) { displayName(context, uri) }
    save(context) { it.copy(uri = uri, place = placeOf(uri), fileName = name, error = null) }
    WorkManager.getInstance(context)
      .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.UPDATE, PeriodicWorkRequestBuilder<Worker>(1, TimeUnit.DAYS).setConstraints(constraints(batteryNotLow = true)).build())
    return if (backupNow(context)) "Backed up. From now on it saves by itself." else flow(context).value.error ?: "Couldn't save the backup"
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
          save(context) { it.copy(error = "The app can't reach the backup file any more. Choose it again.", running = false) }
          false
        } catch (e: IOException) {
          markDirty(context)
          val gone = e is java.io.FileNotFoundException
          save(context) { it.copy(error = if (gone) "The backup file was moved or deleted. Choose it again." else "Couldn't save the backup, will try again", running = false) }
          false
        } catch (t: Throwable) {
          CrashLog.recovered(context, t)
          markDirty(context)
          save(context) { it.copy(error = "Backup failed: ${t.message ?: t.javaClass.simpleName}", running = false) }
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
    val pfd = runCatching { resolver.openFileDescriptor(uri, "wt") }.getOrNull() ?: resolver.openFileDescriptor(uri, "w") ?: throw IOException("Couldn't open the backup file")
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
      val uri = flow(context).value.uri ?: throw BackupArchive.InvalidBackup("No backup file chosen yet.")
      val input = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull() ?: throw BackupArchive.InvalidBackup("The app can't open the backup file. Choose it again.")
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
