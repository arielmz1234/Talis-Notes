package com.tali.notes.backup

import com.tali.notes.later
import com.tali.notes.R
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
import com.tali.notes.data.NotebookMeta
import com.tali.notes.data.NotesRepository
import com.tali.notes.export.Exporter
import com.tali.notes.export.PdfLayout
import com.tali.notes.export.PdfQuality
import com.tali.notes.talisApp
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    return if (hasResolution() && pending != null) Auth.NeedsConsent(pending) else Auth.Token(accessToken ?: throw IOException(later(R.string.google_didn_t_return_a_token)))
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

  /**
   * Backs up a moment later (a burst of changes becomes one upload, and what was just saved has
   * reached the database). Called when something is added, a notebook is closed, or the app is left,
   * so another device sees it straight away; never on every stroke. Runs in the background
   * (WorkManager, waiting for a network), so it never slows the app.
   */
  fun backupSoon(context: Context, delaySeconds: Long = QUICK_S) {
    if (!available || !flow(context).value.enabled) return
    WorkManager.getInstance(context)
      .enqueueUniqueWork(
        SOON,
        ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<BackupWorker>()
          .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
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
            save(ctx) { it.copy(error = later(R.string.google_needs_you_to_sign_in)) }
            Result.success()
          }
          is Auth.Token -> {
            backupNow(ctx, auth.value)
            Result.success()
          }
        }
      } catch (e: IOException) {
        save(ctx) { it.copy(error = later(R.string.couldn_t_reach_google_drive_will), running = false) }
        Result.retry()
      } catch (t: Throwable) {
        CrashLog.recovered(ctx, t)
        save(ctx) { it.copy(error = later(R.string.backup_failed_n, t.message ?: t.javaClass.simpleName), running = false) }
        Result.success()
      }
    }
  }

  // ---- One file per notebook ----
  //
  // The "Tali's Notes" folder holds one backup file per notebook ("Statics.zip"), one for her
  // settings and folders, and, when she wants, a PDF beside each notebook. Each file carries hidden
  // app properties naming what it is, so a renamed notebook renames its file instead of adding one.
  // Only notebooks that changed since this tablet last sent them are uploaded again.

  private const val KIND = "tali"
  private const val NOTEBOOK = "notebook"
  private const val PDF = "pdf"
  private const val SETTINGS = "settings"
  private const val NOTEBOOK_ID = "notebookId"
  private const val SETTINGS_NAME = "Tali's Notes settings.zip"
  /** On each file: the notebook's last edit (or when the settings were written), and the device that sent it. */
  private const val UPDATED = "updated"
  private const val DEVICE = "device"

  /** This install's own id, so a device never pulls back what it sent itself. */
  private fun deviceId(context: Context): String {
    val p = prefs(context)
    return p.getString("device", null) ?: java.util.UUID.randomUUID().toString().also { p.edit().putString("device", it).apply() }
  }

  /** What this tablet last sent for a notebook. */
  @kotlinx.serialization.Serializable private data class Sent(val sig: String, val pdf: Boolean = false)

  private val sentSerializer = kotlinx.serialization.builtins.MapSerializer(String.serializer(), Sent.serializer())

  private fun sent(context: Context): MutableMap<String, Sent> =
    prefs(context).getString("sent", null)?.let { runCatching { json.decodeFromString(sentSerializer, it) }.getOrNull() }?.toMutableMap() ?: mutableMapOf()

  private fun saveSent(context: Context, sent: Map<String, Sent>) = prefs(context).edit().putString("sent", json.encodeToString(sentSerializer, sent)).apply()

  /** What changes a notebook's backup: an edit, a rename, a move to another folder, a new cover or paper. */
  private fun signature(nb: NotebookMeta) = listOf(nb.updated, nb.title, nb.folderId, nb.cover, nb.paper, nb.customPaper, nb.pages, nb.dark, nb.landscape).joinToString("|")

  /** A file in the backup folder, and what it holds (null kind: not one of ours, like the old single backup). */
  private class Remote(val id: String, val name: String, val kind: String?, val notebook: String?, val updated: Long? = null, val device: String? = null)

  private fun fileName(title: String) = title.ifBlank { "Untitled" }.replace('/', '-').replace('\\', '-')

  /** Backs up every notebook that changed, her settings and folders, and the PDFs if she wants them. */
  suspend fun backupNow(context: Context, token: String) =
    withContext(Dispatchers.IO) {
      save(context) { it.copy(running = true) }
      val app = context.talisApp
      val repo = app.repository
      val snapshot = File(context.cacheDir, "drive-snapshot.db")
      val tmp = File(context.cacheDir, "drive-upload.tmp")
      try {
        // Cleared first: an edit made during the upload marks it again.
        prefs(context).edit().putBoolean("dirty", false).apply()
        repo.snapshotDatabase(snapshot)
        val folder = findOrCreateFolder(token)
        val remote = listFolder(token, folder)
        val sent = sent(context)
        val pdfOn = app.settings.settings.value.drivePdf
        tmp.outputStream().use { BackupArchive.writeSettings(context, snapshot, it) }
        val me = deviceId(context)
        val settingsAt = System.currentTimeMillis()
        upload(token, tmp, remote.firstOrNull { it.kind == SETTINGS }?.id, folder, SETTINGS_NAME, BackupArchive.MIME, mapOf(KIND to SETTINGS, UPDATED to settingsAt.toString(), DEVICE to me))
        prefs(context).edit().putLong("settings_seen", settingsAt).apply()
        val live = repo.notebooks.value
        for (nb in live) {
          val sig = signature(nb)
          val before = sent[nb.id]
          val changed = before?.sig != sig
          val zip = remote.firstOrNull { it.kind == NOTEBOOK && it.notebook == nb.id }
          val pdf = remote.firstOrNull { it.kind == PDF && it.notebook == nb.id }
          val name = fileName(nb.title)
          if (changed || zip == null) {
            tmp.outputStream().use { BackupArchive.writeNotebook(context, snapshot, nb.id, it) }
            upload(token, tmp, zip?.id, folder, "$name.zip", BackupArchive.MIME, mapOf(KIND to NOTEBOOK, NOTEBOOK_ID to nb.id, UPDATED to nb.updated.toString(), DEVICE to me))
          }
          var pdfSent = false
          if (pdfOn) {
            pdfSent = before?.pdf == true && !changed && pdf != null
            if (!pdfSent && makePdf(context, nb, tmp)) {
              upload(token, tmp, pdf?.id, folder, "$name.pdf", "application/pdf", mapOf(KIND to PDF, NOTEBOOK_ID to nb.id))
              pdfSent = true
            }
          } else if (pdf != null) {
            trash(token, pdf.id)
          }
          sent[nb.id] = Sent(sig, pdfSent)
          // Kept after each notebook, so an interrupted backup carries on where it stopped.
          saveSent(context, sent)
        }
        // A notebook this tablet sent and has since erased for good (not just put in Recently deleted):
        // its files go to Drive's trash, where they can still be found for 30 days. Only ones this
        // tablet sent: a new or cleared tablet has sent nothing, so it never removes anything.
        val here = (live + repo.trash.value).map { it.id }.toSet()
        for (id in sent.keys - here) {
          remote.filter { it.notebook == id }.forEach { trash(token, it.id) }
          sent.remove(id)
        }
        saveSent(context, sent)
        // The old single backup file is replaced by these (it stays in Drive's trash for 30 days).
        remote.filter { it.kind == null && it.name == FILE_NAME }.forEach { trash(token, it.id) }
        prefs(context).edit().remove("file_id").apply()
        save(context) { it.copy(lastSuccess = System.currentTimeMillis(), error = null, running = false) }
      } catch (t: Throwable) {
        markDirty(context)
        save(context) { it.copy(running = false) }
        throw t
      } finally {
        snapshot.delete()
        tmp.delete()
      }
    }

  /** A PDF of the notebook (A4 pages, pictures at print quality), written to [out]. */
  private suspend fun makePdf(context: Context, nb: NotebookMeta, out: File): Boolean {
    val app = context.talisApp
    val s = app.settings.settings.value
    val items = runCatching { app.repository.loadItems(nb.id) }.getOrNull()?.filterNot { it is com.tali.notes.model.AudioItem } ?: return false
    return runCatching {
        Exporter.pdf(
          items,
          nb.paper,
          PdfLayout.A4_PAGES,
          out,
          app.repository.images,
          fixedPages = s.pageGuides,
          pageCount = nb.pages,
          dark = nb.dark,
          custom = nb.customPaper?.let { id -> s.customPapers.firstOrNull { it.id == id } },
          quality = PdfQuality.BALANCED,
          landscape = nb.landscape,
          inkBetweenPages = s.inkBetweenPages,
          pageAspect = nb.pageAspect,
        )
      }
      .onFailure { CrashLog.recovered(context, it) }
      .isSuccess && out.length() > 0
  }

  /**
   * Brings the Drive backup back (a new tablet, or after reinstalling): her settings and folders
   * first, then every notebook, each merged in. Older single-file backups still restore.
   */
  suspend fun restore(context: Context, token: String): NotesRepository.MergeResult =
    withContext(Dispatchers.IO) {
      val folder = findFolder(token)
      val remote = folder?.let { listFolder(token, it) }.orEmpty()
      val parts = remote.filter { it.kind == SETTINGS } + remote.filter { it.kind == NOTEBOOK }
      val files = parts.map { it.id }.ifEmpty { listOfNotNull(findBackup(token)) }
      if (files.isEmpty()) throw BackupArchive.InvalidBackup(later(R.string.there_s_no_backup_in_this))
      var total = NotesRepository.MergeResult(0, 0, 0, emptySet())
      for (id in files) {
        val conn = open("$API/files/$id?alt=media", token)
        check(conn)
        val r = conn.inputStream.use { BackupArchive.restore(context, it) }
        total = NotesRepository.MergeResult(total.added + r.added, total.updated + r.updated, total.kept + r.kept, total.changed + r.changed)
      }
      total
    }

  /** The notebook open on screen right now: never replaced from Drive underneath her. */
  @Volatile var openNotebook: String? = null

  private val syncing = java.util.concurrent.atomic.AtomicBoolean(false)

  /**
   * Catches up with Drive when the app opens or comes back (another device may have backed up since):
   * notebooks that are newer there, or new to this device, are brought in (merged: a newer notebook
   * replaces the older one here), and settings and folders another device changed. Quiet: no consent
   * screen, nothing shown if Drive can't be reached. Only with Drive backup on.
   */
  fun syncSoon(context: Context) {
    val app = context.applicationContext
    if (!available || !flow(app).value.enabled) return
    val last = prefs(app).getLong("synced_at", 0L)
    if (System.currentTimeMillis() - last < SYNC_GAP_MS) return
    if (!syncing.compareAndSet(false, true)) return
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
      try {
        val auth = runCatching { authorize(app) }.getOrNull() as? Auth.Token ?: return@launch
        val brought = syncNow(app, auth.value)
        prefs(app).edit().putLong("synced_at", System.currentTimeMillis()).apply()
        if (brought > 0) synced.value = brought
      } catch (t: Throwable) {
        if (t !is IOException) CrashLog.recovered(app, t)
      } finally {
        syncing.set(false)
      }
    }
  }

  /** How many notebooks the last catch-up brought in (shown once by the library, then cleared). */
  val synced = MutableStateFlow(0)

  private suspend fun syncNow(context: Context, token: String): Int =
    withContext(Dispatchers.IO) {
      val folder = findFolder(token) ?: return@withContext 0
      val remote = listFolder(token, folder)
      val me = deviceId(context)
      val repo = context.talisApp.repository
      val p = prefs(context)
      // Settings and folders another device sent since this one last saw them.
      remote.firstOrNull { it.kind == SETTINGS }?.let { s ->
        val at = s.updated ?: return@let
        if (s.device != null && s.device != me && at > p.getLong("settings_seen", 0L)) {
          download(token, s.id) { BackupArchive.restore(context, it) }
          p.edit().putLong("settings_seen", at).apply()
        }
      }
      val local = (repo.notebooks.value + repo.trash.value).associateBy { it.id }
      val sent = sent(context)
      var brought = 0
      for (r in remote.filter { it.kind == NOTEBOOK && it.notebook != null && it.updated != null }) {
        val id = r.notebook!!
        if (id == openNotebook || r.device == me) continue
        val here = local[id]
        // Sent from here and erased since: its Drive file goes on the next backup, don't bring it back.
        if (here == null && id in sent) continue
        if (here != null && here.updated >= r.updated!!) continue
        val result = download(token, r.id) { BackupArchive.restore(context, it) }
        if (result.added + result.updated > 0) brought++
        // It now matches Drive: nothing to send back for it.
        repo.notebooks.value.firstOrNull { it.id == id }?.let { nb -> sent[id] = Sent(signature(nb), sent[id]?.pdf == true) }
      }
      saveSent(context, sent)
      brought
    }

  private inline fun <T> download(token: String, id: String, read: (java.io.InputStream) -> T): T {
    val conn = open("$API/files/$id?alt=media", token)
    check(conn)
    return conn.inputStream.use(read)
  }

  private const val SYNC_GAP_MS = 60_000L
  private const val QUICK_S = 5L

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
    if (code == 401 || code == 403) throw IllegalStateException(later(R.string.google_drive_refused_access_n, code))
    if (code !in 200..299) throw IOException(later(R.string.google_drive_error_n, code))
  }

  private fun readJson(conn: HttpURLConnection): JsonObject = json.parseToJsonElement(conn.inputStream.use { it.readBytes().decodeToString() }).jsonObject

  private fun query(q: String) = URLEncoder.encode(q, "UTF-8")

  private fun escape(name: String) = name.replace("\\", "\\\\").replace("'", "\\'")

  private fun findFolder(token: String): String? {
    val q = "mimeType='application/vnd.google-apps.folder' and name='${escape(FOLDER_NAME)}' and trashed=false"
    val list = open("$API/files?q=${query(q)}&spaces=drive&fields=files(id)", token).also(::check).let(::readJson)
    return list["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
  }

  private fun findOrCreateFolder(token: String): String {
    findFolder(token)?.let { return it }
    val conn = open("$API/files?fields=id", token, "POST")
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    val meta = JsonObject(mapOf("name" to JsonPrimitive(FOLDER_NAME), "mimeType" to JsonPrimitive("application/vnd.google-apps.folder")))
    conn.outputStream.use { it.write(meta.toString().encodeToByteArray()) }
    check(conn)
    return readJson(conn)["id"]!!.jsonPrimitive.content
  }

  /** Every file in the backup folder (the app only ever sees its own files). */
  private fun listFolder(token: String, folder: String): List<Remote> {
    val out = mutableListOf<Remote>()
    var page: String? = null
    do {
      val q = "'$folder' in parents and trashed=false"
      val url = "$API/files?q=${query(q)}&spaces=drive&pageSize=1000&fields=${query("nextPageToken,files(id,name,appProperties)")}" + (page?.let { "&pageToken=${query(it)}" } ?: "")
      val res = open(url, token).also(::check).let(::readJson)
      res["files"]?.jsonArray?.forEach { f ->
        val o = f.jsonObject
        val props = o["appProperties"]?.jsonObject
        out += Remote(
          o["id"]!!.jsonPrimitive.content,
          o["name"]?.jsonPrimitive?.content.orEmpty(),
          props?.get(KIND)?.jsonPrimitive?.content,
          props?.get(NOTEBOOK_ID)?.jsonPrimitive?.content,
          props?.get(UPDATED)?.jsonPrimitive?.content?.toLongOrNull(),
          props?.get(DEVICE)?.jsonPrimitive?.content,
        )
      }
      page = res["nextPageToken"]?.jsonPrimitive?.content
    } while (page != null)
    return out
  }

  private fun findBackup(token: String): String? {
    val q = "name='${escape(FILE_NAME)}' and trashed=false"
    val list = open("$API/files?q=${query(q)}&spaces=drive&orderBy=modifiedTime%20desc&fields=files(id)", token).also(::check).let(::readJson)
    return list["files"]?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
  }

  /** Moves a file to Drive's trash (it can be brought back there for 30 days). */
  private fun trash(token: String, id: String) {
    val conn = open("$API/files/$id?fields=id", token, "POST")
    conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
    conn.doOutput = true
    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    conn.outputStream.use { it.write(JsonObject(mapOf("trashed" to JsonPrimitive(true))).toString().encodeToByteArray()) }
    runCatching { check(conn) }.onFailure { if (it !is NotFound) throw it }
  }

  /**
   * Resumable upload, which works for any size: start a session, then send the bytes. Replaces
   * [existingId] (renaming it to [name]) or makes a new file in [folderId]. Returns the file id.
   */
  private fun upload(token: String, file: File, existingId: String?, folderId: String, name: String, mime: String, props: Map<String, String>): String =
    try {
      uploadOnce(token, file, existingId, folderId, name, mime, props)
    } catch (e: NotFound) {
      // The file went in the meantime: make it again.
      if (existingId == null) throw e
      uploadOnce(token, file, null, folderId, name, mime, props)
    }

  private fun uploadOnce(token: String, file: File, existingId: String?, folderId: String, name: String, mime: String, props: Map<String, String>): String {
    val start =
      if (existingId == null) open("$UPLOAD/files?uploadType=resumable&fields=id", token, "POST")
      else open("$UPLOAD/files/$existingId?uploadType=resumable&fields=id", token, "POST").apply { setRequestProperty("X-HTTP-Method-Override", "PATCH") }
    start.doOutput = true
    start.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
    start.setRequestProperty("X-Upload-Content-Type", mime)
    start.setRequestProperty("X-Upload-Content-Length", file.length().toString())
    val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("name" to JsonPrimitive(name), "appProperties" to JsonObject(props.mapValues { JsonPrimitive(it.value) }))
    if (existingId == null) fields["parents"] = JsonArray(listOf(JsonPrimitive(folderId)))
    start.outputStream.use { it.write(JsonObject(fields).toString().encodeToByteArray()) }
    check(start)
    val session = start.getHeaderField("Location") ?: throw IOException(later(R.string.google_drive_didn_t_start_the))
    val put = (URL(session).openConnection() as HttpURLConnection).apply {
      requestMethod = "PUT"
      doOutput = true
      connectTimeout = 20_000
      readTimeout = 120_000
      setFixedLengthStreamingMode(file.length())
      setRequestProperty("Content-Type", mime)
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
