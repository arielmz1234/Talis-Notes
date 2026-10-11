package com.tali.notes.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.TabletAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tali.notes.CrashLog
import androidx.compose.material.icons.outlined.CloudSync
import com.tali.notes.backup.AutoBackup
import com.tali.notes.talisApp
import com.tali.notes.backup.BackupArchive
import com.tali.notes.backup.DriveBackup
import com.tali.notes.backup.RestoreFiles
import com.tali.notes.data.NotesRepository
import com.tali.notes.theme.Tali
import com.tali.notes.ui.library.CardBody
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.ToggleRow
import com.tali.notes.ui.components.WorkingText
import com.tali.notes.ui.components.relativeTime
import kotlinx.coroutines.launch

/**
 * Where her notes are kept safe: always on the tablet, in a backup file she can save anywhere, and
 * (once it's set up) automatically in her Google Drive.
 */
@Composable
fun BackupDialog(onDismiss: () -> Unit) {
  val c = Tali.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf<String?>(null) }
  var message by remember { mutableStateOf<String?>(null) }
  // Which card the running (or last) action came from: its status shows in that card, where she tapped.
  var source by remember { mutableStateOf("file") }

  fun describe(r: NotesRepository.MergeResult): String {
    val parts = buildList {
      if (r.added > 0) add(if (r.added == 1) "1 notebook added" else "${r.added} notebooks added")
      if (r.updated > 0) add(if (r.updated == 1) "1 notebook updated" else "${r.updated} notebooks updated")
    }
    return if (parts.isEmpty()) "Everything in the backup was already here." else parts.joinToString(", ") + "."
  }

  fun run(label: String, from: String = "file", block: suspend () -> String) {
    if (busy != null) return
    source = from
    busy = label
    message = null
    scope.launch {
      message =
        try {
          block()
        } catch (e: BackupArchive.InvalidBackup) {
          e.message
        } catch (t: Throwable) {
          CrashLog.recovered(context, t)
          "Something went wrong: ${t.message ?: t.javaClass.simpleName}"
        }
      busy = null
    }
  }

  val saveFile =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupArchive.MIME)) { uri ->
      if (uri != null) run("Saving backup…") {
        context.contentResolver.openOutputStream(uri)?.use { BackupArchive.write(context, it) } ?: error("Couldn't open the file")
        "Backup saved."
      }
    }
  val openFiles =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) run("Restoring…") { describe(RestoreFiles.restore(context, uris)) }
    }
  val openFolder =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
      if (tree != null) run("Restoring…") { describe(RestoreFiles.restore(context, RestoreFiles.inFolder(context, tree))) }
    }
  val note = busy ?: message

  DialogCard(onDismiss, width = 560) {
    Text("Backups", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(16.dp))
    CardBody {
    BackupCard(Icons.Outlined.TabletAndroid, "On this tablet", "Every stroke is saved the moment you write it, even without internet.")
    // With Google Drive set up, it's the main automatic backup; saving into a file she picked stays
    // as the way for OneDrive, Dropbox or the tablet.
    if (DriveBackup.available) {
      Spacer(Modifier.height(10.dp))
      DriveCard(onRun = { l, b -> run(l, "drive", b) }, describe = ::describe, note = note.takeIf { source == "drive" })
    }
    Spacer(Modifier.height(10.dp))
    AutoCard(onRun = { l, b -> run(l, "auto", b) }, compact = DriveBackup.available, note = note.takeIf { source == "auto" })
    Spacer(Modifier.height(10.dp))
    BackupCard(Icons.Outlined.FolderZip, "Backup file", "Everything in one file: notebooks, pictures, pens, colours and your shapes. Keep it in Files, on a computer, or send it to yourself.") {
      @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
      androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The one to set up is the automatic backup above; this is the extra copy.
        // Saving and restoring wear their own colours so they're not mistaken for each other.
        QuietButton("Save backup file", color = c.blue) { saveFile.launch(BackupArchive.fileName()) }
        QuietButton("Restore files", color = restoreColor()) { openFiles.launch(arrayOf(BackupArchive.MIME, "application/x-zip-compressed", "application/octet-stream")) }
        QuietButton("Restore folder", color = restoreColor()) { openFolder.launch(null) }
      }
    }
    Spacer(Modifier.height(14.dp))
    val fileNote = note.takeIf { source == "file" }
    WorkingText(
      fileNote ?: "Restoring never deletes anything: notebooks from a backup are added, and if a notebook is in both places, the newest version is kept. To bring back every notebook at once, pick several files or the folder they're in.",
      style = MaterialTheme.typography.bodySmall,
      color = if (fileNote != null) c.blue else c.inkFaint,
    )
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { QuietButton("Done", onClick = onDismiss) }
  }
}

@Composable
private fun BackupCard(icon: ImageVector, title: String, body: String, actions: (@Composable () -> Unit)? = null) {
  val c = Tali.colors
  Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface.copy(alpha = 0.7f)).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(16.dp)) {
    Icon(icon, null, tint = c.blue, modifier = Modifier.size(22.dp))
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
      Spacer(Modifier.height(2.dp))
      Text(body, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
      if (actions != null) {
        Spacer(Modifier.height(12.dp))
        actions()
      }
    }
  }
}

/** "Today, 14:20", "Yesterday, 09:05" or "12 Oct, 18:40": when the last automatic backup was saved. */
fun backedUpAt(millis: Long, now: Long = System.currentTimeMillis()): String {
  val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.UK).format(java.util.Date(millis))
  val day = java.util.Calendar.getInstance().apply { timeInMillis = millis }
  val today = java.util.Calendar.getInstance().apply { timeInMillis = now }
  fun same(a: java.util.Calendar, b: java.util.Calendar) = a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
  val yesterday = (today.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
  val date =
    when {
      same(day, today) -> "today"
      same(day, yesterday) -> "yesterday"
      else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.UK).format(java.util.Date(millis))
    }
  return "$date, $time"
}

/** The backup that saves itself into one file she picked once (her Drive, OneDrive, the tablet…). */
@Composable
private fun AutoCard(onRun: (String, suspend () -> String) -> Unit, compact: Boolean = false, note: String? = null) {
  val c = Tali.colors
  val context = LocalContext.current
  val status by AutoBackup.status(context).collectAsState()
  val pick =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupArchive.MIME)) { uri ->
      if (uri != null) onRun("Saving backup…") { AutoBackup.choose(context, uri) }
    }
  if (compact && !status.enabled) {
    // Google Drive already backs up by itself: this is just the other way, kept to one line.
    Row(
      Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.surface.copy(alpha = 0.7f)).border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(Icons.Outlined.CloudSync, null, tint = c.blue, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text("Or back up by itself into a file", style = MaterialTheme.typography.titleSmall, color = c.ink)
        Text("For OneDrive, Dropbox or the tablet", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
      }
      Spacer(Modifier.width(10.dp))
      QuietButton("Choose") { pick.launch(AutoBackup.FILE_NAME) }
    }
    return
  }
  BackupCard(
    if (status.enabled) Icons.Outlined.CloudDone else Icons.Outlined.CloudSync,
    "Back up by itself",
    if (status.enabled) "Saves a fresh backup a couple of minutes after you finish, and once a day, into “${status.fileName ?: AutoBackup.FILE_NAME}” ${if (status.place == "this tablet") "on" else "in"} ${status.place ?: "the place you chose"}."
    else "Choose a place once and the app keeps one backup file there up to date by itself. For Google Drive, tap ☰ at the top left of the next screen. Picking an old backup file brings its notebooks back.",
  ) {
    if (status.enabled) {
      WorkingText(
        when {
          note != null -> note
          status.running -> "Backing up now…"
          status.error != null -> status.error!!
          status.lastSuccess > 0 -> "Last backed up: ${backedUpAt(status.lastSuccess)}"
          else -> "No backup yet"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (note != null) c.blue else if (status.error != null) c.danger else c.inkFaint,
      )
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietButton("Back up now", color = c.blue) { onRun("Backing up…") { if (AutoBackup.backupNow(context)) "Backed up." else AutoBackup.status(context).value.error ?: "Couldn't save the backup" } }
        QuietButton("Change place") { pick.launch(AutoBackup.FILE_NAME) }
        QuietButton("Turn off") { AutoBackup.turnOff(context) }
      }
    } else {
      PrimaryButton("Choose where to save") { pick.launch(AutoBackup.FILE_NAME) }
    }
  }
}

/**
 * Runs something that needs Google Drive: gets a token (showing Google's account and consent screen
 * the first time), then the work, all under [label], so "Restoring from Drive…" hops from the
 * moment she taps.
 */
@Composable
internal fun rememberDriveAuth(onRun: (String, suspend () -> String) -> Unit): (String, suspend (String) -> String) -> Unit {
  val context = LocalContext.current
  // What to do once Google hands back a token after the consent screen.
  var afterConsent by remember { mutableStateOf<((String) -> Unit)?>(null) }
  val consent =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
      val auth = if (result.resultCode == Activity.RESULT_OK) DriveBackup.authFromConsent(context, result.data) else null
      val next = afterConsent
      afterConsent = null
      if (auth is DriveBackup.Auth.Token && next != null) next(auth.value)
    }
  return { label, then ->
    onRun(label) {
      when (val auth = DriveBackup.authorize(context)) {
        is DriveBackup.Auth.Token -> then(auth.value)
        is DriveBackup.Auth.NeedsConsent -> {
          afterConsent = { token -> onRun(label) { then(token) } }
          consent.launch(IntentSenderRequest.Builder(auth.intent).build())
          "Choose your Google account to continue."
        }
      }
    }
  }
}

/** Restoring wears teal everywhere in Backups, saving blue, so the two are never mistaken for each other. */
@Composable
private fun restoreColor() = if (Tali.colors.isDark) Color(0xFF6FD3B5) else Color(0xFF1C8466)

@Composable
private fun DriveCard(onRun: (String, suspend () -> String) -> Unit, describe: (NotesRepository.MergeResult) -> String, note: String? = null) {
  val c = Tali.colors
  val context = LocalContext.current
  val status by DriveBackup.status(context).collectAsState()
  val withToken = rememberDriveAuth(onRun)

  BackupCard(if (status.enabled) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload, "Google Drive", "Backs up by itself a couple of minutes after you finish writing: one file per notebook in a “Tali's Notes” folder in your Drive, sending only the notebooks that changed. The app can only see its own files, nothing else in your Drive.") {
    ToggleRow("Back up automatically", status.enabled, { on ->
      if (on) withToken("Backing up to Drive…") { token ->
        DriveBackup.enable(context)
        DriveBackup.backupNow(context, token)
        "Backed up to Google Drive."
      } else DriveBackup.disable(context)
    })
    if (status.enabled) {
      val settings by context.talisApp.settings.settings.collectAsState()
      ToggleRow("Also keep a PDF of each notebook", settings.drivePdf, { on ->
        context.talisApp.settings.update { it.copy(drivePdf = on) }
        DriveBackup.markDirty(context)
        DriveBackup.backupSoon(context, delayMinutes = 0)
      }, detail = "Opens on any computer or phone, without the app. Uses more space and takes longer.")
    }
    WorkingText(
      when {
        note != null -> note
        status.running -> "Backing up now…"
        status.error != null -> status.error!!
        status.lastSuccess > 0 -> "Last backup ${relativeTime(status.lastSuccess)}"
        else -> "No backup yet"
      },
      style = MaterialTheme.typography.bodySmall,
      color = if (note != null) c.blue else if (status.error != null) c.danger else c.inkFaint,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      if (status.enabled) {
        QuietButton("Back up now", color = c.blue) {
          withToken("Backing up to Drive…") { token ->
            DriveBackup.backupNow(context, token)
            "Backed up to Google Drive."
          }
        }
      }
      QuietButton("Restore from Drive", color = restoreColor()) {
        withToken("Restoring from Drive…") { token -> describe(DriveBackup.restore(context, token)) }
      }
    }
  }
}
