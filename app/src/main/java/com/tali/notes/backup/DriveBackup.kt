package com.tali.notes.backup

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.tali.notes.BuildConfig
import com.tali.notes.CrashLog
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Automatic backup to her own Google Drive, as one file ("Tali's Notes backup.zip" in a
 * "Tali's Notes" folder) that is replaced in place each time; Drive keeps earlier versions of it.
 *
 * Uses the drive.file scope: the app can only see files it created itself, never the rest of her
 * Drive. Backups run in the background a couple of minutes after she leaves a notebook, and once a
 * day, only when something changed.
 *
 * Switched off until [BuildConfig.DRIVE_BACKUP] is true, because Google sign-in needs a one-time
 * OAuth client for this app's package and signing key (see BACKUP_SETUP.md).
 */
object DriveBackup {
  val available: Boolean
    get() = BuildConfig.DRIVE_BACKUP

  private const val SCOPE = "https://www.googleapis.com/auth/drive.file"
  private const val FOLDER_NAME = "Tali's Notes"
  private const val FILE_NAME = "Tali's Notes backup.zip"
  private const val API = "https://www.googleapis.com/drive/v3"
  private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
  private const val SOON = "drive-backup-soon"
  private const val DAILY = "drive-backup-daily"
  private val json = Json { ignoreUnknownKeys = true }

  // ---- State shown in the app ----

  data class Status(val enabled: Boolean, val lastSuccess: Long, val error: String?, val running: Boolean = false)

  private var state: MutableStateFlow<Status>? = null

  fun status(context: Context): StateFlow<Status> = flow(context).asStateFlow()

  private fun flow(context: Context): MutableStateFlow<Status> =
    state ?: synchronized(this) { state ?: MutableStateFlow(read(context)).also { state = it } }

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("drive_backup", Context.MODE_PRIVATE)

  private fun read(context: Context): Status {
    val p = prefs(context)
    return Status(p.getBoolean("enabled", false), p.getLong("last_success", 0L), p.getString("error", null))
  }

  private fun save(context: Context, transform: (Status) -> Status) {
    val f = flow(context)
    val next = transform(f.value)
    f.value = next
    prefs(context).edit().putBoolean("enabled", next.enabled).putLong("last_success", next.lastSuccess).putString("error", next.error).apply()
  }

  /** Marks that something changed since the last backup. Called for every saved edit, so it only writes when the flag flips. */
  fun markDirty(context: Context) {
    val p = prefs(context)
    if (!p.getBoolean("dirty", false)) p.edit().putBoolean("dirty", true).apply()
  }

  private fun isDirty(context: Context) = prefs(context).getBoolean("dirty", true)

  // ---- Turning it on and off ----

  sealed interface Auth {
    data class Token(val value: String) : Auth

    /** First time (or after she removed access): show Google's consent screen. */
    data class NeedsConsent(val intent: PendingIntent) : Auth
  }

  private fun request() = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(SCOPE))).build()

  suspend fun authorize(context: Context): Auth {
    val result = Identity.getAuthorizationClient(context).authorize(request()).await()
    return result.toAuth()
  }

  /** Reads the result of the consent screen. */
  fun authFromConsent(context: Context, data: Intent?): Auth? =
    runCatching { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data).toAuth() }.getOrNull()

  private fun AuthorizationResult.toAuth(): Auth {
    val pending = pendingIntent
    return if (hasResolution() && pending != null) Auth.NeedsConsent(pending) else Auth.Token(accessToken ?: throw IOException("Google didn't return a token"))
  }

  fun enable(context: Context) {
    save(context) { it.copy(enabled = true, error = null) }
    val work = WorkManager.getInstance(context)
    work.enqueueUniquePeriodicWork(
      DAILY,
      ExistingPeriodicWorkPolicy.KEEP,
      PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).setConstraints(constraints(batteryNotLow = true)).build(),
    )
  }

  fun disable(context: Context) {
    save(context) { it.copy(enabled = false, error = null) }
    val work = WorkManager.getInstance(context)
    work.cancelUniqueWork(DAILY)
    work.cancelUniqueWork(SOON)
  }

  /** Backs up a little later, so a burst of edits becomes one upload. */
  fun backupSoon(context: Context, delayMinutes: Long = 2) {
    if (!available || !flow(context).value.enabled) return
    WorkManager.getInstance(context)
      .enqueueUniqueWork(
        SOON,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<BackupWorker>()
          .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
          .setConstraints(constraints(batteryNotLow = false))
          .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
          .build(),
      )
  }

  private fun constraints(batteryNotLow: Boolean) = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(batteryNotLow).build()

  // ---- The backup itself ----

  class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
      val ctx = applicationContext
      if (!available || !flow(ctx).value.enabled) return Result.success()
      if (!isDirty(ctx)) return Result.success()
      return try {
        when (val auth = authorize(ctx)) {
          is Auth.NeedsConsent -> {
            save(ctx) { it.copy(error = "Google needs you to sign in again") }
            Result.success()
          }
          is Auth.Token -> {
            backupNow(ctx, auth.value)
            Result.success()
          }
        }
      } catch (e: IOException) {
        save(ctx) { it.copy(error = "Couldn't reach Google Drive, will try again", running = false) }
        Result.retry()
      } catch (t: Throwable) {
        CrashLog.recovered(ctx, t)
        save(ctx) { it.copy(error = "Backup failed: ${t.message ?: t.javaClass.simpleName}", running = false) }
        Result.success()
      }
    }
  }

  /** Uploads a fresh backup file, replacing the previous one in Drive. */
  suspend fun backupNow(context: Context, token: String) =
    withContext(Dispatchers.IO) {
      save(context) { it.copy(running = true) }
      val file = File(context.cacheDir, "drive-backup.zip")
      try {
        // Clear the flag first: an edit made during the upload marks it dirty again.
        prefs(context).edit().putBoolean("dirty", false).apply()
        file.outputStream().use { BackupArchive.write(context, it) }
        val p = prefs(context)
        val folder = p.getString("folder_id", null) ?: findOrCreateFolder(token).also { p.edit().putString("folder_id", it).apply() }
        val existing = p.getString("file_id", null) ?: findBackup(token)
        val id =
          try {
            upload(token, file, existing, folder)
          } catch (e: NotFound) {
            // She deleted the file (or its folder) in Drive: start over.
            val newFolder = findOrCreateFolder(token).also { p.edit().putString("folder_id", it).apply() }
            upload(token, file, null, newFolder)
          }
        p.edit().putString("file_id", id).apply()
        save(context) { it.copy(lastSuccess = System.currentTimeMillis(), error = null, running = false) }
      } catch (t: Throwable) {
        markDirty(context)
        save(context) { it.copy(running = false) }
        throw t
      } finally {
        file.delete()
      }
    }

  /** Downloads the Drive backup and merges it in (for a new tablet, or after reinstalling). */
  suspend fun restore(context: Context, token: String) =
    withContext(Dispatchers.IO) {
      val id = findBackup(token) ?: throw BackupArchive.InvalidBackup("There's no backup in this Google Drive yet.")
      val conn = open("$API/files/$id?alt=media", token)
      check(conn)
      conn.inputStream.use { BackupArchive.restore(context, it) }
    }

  private class NotFound : IOException("Not found")

  private fun open(url: String, token: String, method: String = "GET"): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 20_000
      readTimeout = 60_000
      setRequestProperty("Authorization", "Bearer $token")
    }

  private fun check(conn: HttpURLConnection) {
    val code = conn.responseCode
    if (code == 404) throw NotFound()
    if (code == 401 || code == 403) throw IllegalStateException("Google Drive refused access ($code)")
    if (code !in 200..299) throw IOException("Google Drive error $code")
  }

  private fun readJson(conn: HttpURLConnection): JsonObject = json.parseToJsonElement(conn.inputStream.use { it.readBytes().decodeToString() }).jsonObject

  private fun query(q: String) = URLEncoder.encode(q, "UTF-8")

  private fun escape(name: String) = name.replace("\\", "\\\\").replace("'", "\\'")

  private fun findOrCreateFolder(token: String): String {
    val q = "mimeType='application/vnd.google-apps.folder' and name='${escape(FOLDER_NAME)}' and trashed=false"
    val list = open("$API/files?q=${query(q)}&spaces=drive&fields=files(id)", token).also(::check).let(::readJson)
    list["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content?.let { return it }
    val conn = open("$API/files?fields=id", token, "POST")
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    conn.outputStream.use { it.write("""{"name":"${FOLDER_NAME.replace("\"", "\\\"")}","mimeType":"application/vnd.google-apps.folder"}""".encodeToByteArray()) }
    check(conn)
    return readJson(conn)["id"]!!.jsonPrimitive.content
  }

  private fun findBackup(token: String): String? {
    val q = "name='${escape(FILE_NAME)}' and trashed=false"
    val list = open("$API/files?q=${query(q)}&spaces=drive&orderBy=modifiedTime%20desc&fields=files(id)", token).also(::check).let(::readJson)
    return list["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
  }

  /** Resumable upload, which works for any size: start a session, then send the bytes. Returns the file id. */
  private fun upload(token: String, file: File, existingId: String?, folderId: String): String {
    val start =
      if (existingId == null) open("$UPLOAD/files?uploadType=resumable&fields=id", token, "POST")
      else open("$UPLOAD/files/$existingId?uploadType=resumable&fields=id", token, "POST").apply { setRequestProperty("X-HTTP-Method-Override", "PATCH") }
    start.doOutput = true
    start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    start.setRequestProperty("X-Upload-Content-Type", BackupArchive.MIME)
    start.setRequestProperty("X-Upload-Content-Length", file.length().toString())
    val meta = if (existingId == null) """{"name":"${FILE_NAME.replace("\"", "\\\"")}","parents":["$folderId"]}""" else "{}"
    start.outputStream.use { it.write(meta.encodeToByteArray()) }
    check(start)
    val session = start.getHeaderField("Location") ?: throw IOException("Google Drive didn't start the upload")
    val put = (URL(session).openConnection() as HttpURLConnection).apply {
      requestMethod = "PUT"
      doOutput = true
      connectTimeout = 20_000
      readTimeout = 120_000
      setFixedLengthStreamingMode(file.length())
      setRequestProperty("Content-Type", BackupArchive.MIME)
    }
    file.inputStream().use { input -> put.outputStream.use { input.copyTo(it) } }
    check(put)
    return readJson(put)["id"]!!.jsonPrimitive.content
  }

  private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
      addOnSuccessListener { cont.resume(it) }
      addOnFailureListener { cont.resumeWithException(it) }
      addOnCanceledListener { cont.cancel() }
    }
}
