package com.tali.notes.ui.library

import com.tali.notes.later
import androidx.compose.ui.res.stringResource
import com.tali.notes.R
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
import com.tali.notes.ui.components.ButtonRow
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
      if (r.added > 0) add(if (r.added == 1) later(R.string.t_1_notebook_added) else later(R.string.n_notebooks_added, r.added))
      if (r.updated > 0) add(if (r.updated == 1) later(R.string.t_1_notebook_updated) else later(R.string.n_notebooks_updated, r.updated))
    }
    return if (parts.isEmpty()) later(R.string.everything_in_the_backup_was_already) else parts.joinToString(", ") + "."
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
          later(R.string.something_went_wrong_n, t.message ?: t.javaClass.simpleName)
        }
      busy = null
    }
  }

  val saveFile =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupArchive.MIME)) { uri ->
      if (uri != null) run(later(R.string.saving_backup)) {
        context.contentResolver.openOutputStream(uri)?.use { BackupArchive.write(context, it) } ?: error(later(R.string.couldn_t_open_the_file))
        later(R.string.backup_saved)
      }
    }
  val openFiles =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) run(later(R.string.restoring)) { describe(RestoreFiles.restore(context, uris)) }
    }
  val openFolder =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
      if (tree != null) run(later(R.string.restoring)) { describe(RestoreFiles.restore(context, RestoreFiles.inFolder(context, tree))) }
    }
  val note = busy ?: message

  DialogCard(onDismiss, width = 560) {
    Text(stringResource(R.string.backups), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(16.dp))
    CardBody {
    BackupCard(Icons.Outlined.TabletAndroid, stringResource(R.string.on_this_tablet), stringResource(R.string.every_stroke_is_saved_the_moment))
    // With Google Drive set up, it's the main automatic backup; saving into a file she picked stays
    // as the way for OneDrive, Dropbox or the tablet.
    if (DriveBackup.available) {
      Spacer(Modifier.height(10.dp))
      DriveCard(onRun = { l, b -> run(l, "drive", b) }, describe = ::describe, note = note.takeIf { source == "drive" })
    }
    Spacer(Modifier.height(10.dp))
    AutoCard(onRun = { l, b -> run(l, "auto", b) }, compact = DriveBackup.available, note = note.takeIf { source == "auto" })
    Spacer(Modifier.height(10.dp))
    BackupCard(Icons.Outlined.FolderZip, stringResource(R.string.backup_file), stringResource(R.string.everything_in_one_file_notebooks_pictures)) {
      @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
      androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // The one to set up is the automatic backup above; this is the extra copy.
        // Saving and restoring wear their own colours so they're not mistaken for each other.
        QuietButton(stringResource(R.string.save_backup_file), color = c.blue) { saveFile.launch(BackupArchive.fileName()) }
        QuietButton(stringResource(R.string.restore_files), color = restoreColor()) { openFiles.launch(arrayOf(BackupArchive.MIME, "application/x-zip-compressed", "application/octet-stream")) }
        QuietButton(stringResource(R.string.restore_folder), color = restoreColor()) { openFolder.launch(null) }
      }
    }
    Spacer(Modifier.height(14.dp))
    val fileNote = note.takeIf { source == "file" }
    WorkingText(
      fileNote ?: stringResource(R.string.restoring_never_deletes_anything_notebooks_from),
      style = MaterialTheme.typography.bodySmall,
      color = if (fileNote != null) c.blue else c.inkFaint,
    )
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { QuietButton(stringResource(R.string.done), onClick = onDismiss) }
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
      same(day, today) -> com.tali.notes.tr(R.string.backed_up_today)
      same(day, yesterday) -> com.tali.notes.tr(R.string.backed_up_yesterday)
      else -> java.text.SimpleDateFormat("d MMM", com.tali.notes.appLocale()).format(java.util.Date(millis))
    }
  return com.tali.notes.tr(R.string.n_n, date, time)
}

/** The backup that saves itself into one file she picked once (her Drive, OneDrive, the tablet…). */
@Composable
private fun AutoCard(onRun: (String, suspend () -> String) -> Unit, compact: Boolean = false, note: String? = null) {
  val c = Tali.colors
  val context = LocalContext.current
  val status by AutoBackup.status(context).collectAsState()
  val pick =
    rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupArchive.MIME)) { uri ->
      if (uri != null) onRun(later(R.string.saving_backup)) { AutoBackup.choose(context, uri) }
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
        Text(stringResource(R.string.or_back_up_by_itself_into), style = MaterialTheme.typography.titleSmall, color = c.ink)
        Text(stringResource(R.string.for_onedrive_dropbox_or_the_tablet), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
      }
      Spacer(Modifier.width(10.dp))
      QuietButton(stringResource(R.string.choose)) { pick.launch(AutoBackup.FILE_NAME) }
    }
    return
  }
  BackupCard(
    if (status.enabled) Icons.Outlined.CloudDone else Icons.Outlined.CloudSync,
    stringResource(R.string.back_up_by_itself),
    if (status.enabled) {
      val file = status.fileName ?: AutoBackup.FILE_NAME
      // The place is kept as "this tablet" or the storage app's own name (Google Drive, OneDrive…).
      when (val place = status.place) {
        null -> stringResource(R.string.auto_backup_into_file_somewhere, file)
        "this tablet" -> stringResource(R.string.auto_backup_into_file_on_tablet, file)
        else -> stringResource(R.string.auto_backup_into_file_in_place, file, place)
      }
    }
    else stringResource(R.string.choose_a_place_once_and_the),
  ) {
    if (status.enabled) {
      WorkingText(
        when {
          note != null -> note
          status.running -> stringResource(R.string.backing_up_now)
          status.error != null -> status.error!!
          status.lastSuccess > 0 -> stringResource(R.string.last_backed_up_n, backedUpAt(status.lastSuccess))
          else -> stringResource(R.string.no_backup_yet)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (note != null) c.blue else if (status.error != null) c.danger else c.inkFaint,
      )
      Spacer(Modifier.height(10.dp))
      ButtonRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietButton(stringResource(R.string.back_up_now), color = c.blue) { onRun(later(R.string.backing_up)) { if (AutoBackup.backupNow(context)) later(R.string.backed_up) else AutoBackup.status(context).value.error ?: later(R.string.couldn_t_save_the_backup) } }
        QuietButton(stringResource(R.string.change_place)) { pick.launch(AutoBackup.FILE_NAME) }
        QuietButton(stringResource(R.string.turn_off)) { AutoBackup.turnOff(context) }
      }
    } else {
      PrimaryButton(stringResource(R.string.choose_where_to_save)) { pick.launch(AutoBackup.FILE_NAME) }
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
          later(R.string.choose_your_google_account_to_continue)
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

  BackupCard(if (status.enabled) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload, stringResource(R.string.google_drive), stringResource(R.string.backs_up_by_itself_a_couple)) {
    ToggleRow(stringResource(R.string.back_up_automatically), status.enabled, { on ->
      if (on) withToken(later(R.string.backing_up_to_drive)) { token ->
        DriveBackup.enable(context)
        DriveBackup.backupNow(context, token)
        later(R.string.backed_up_to_google_drive)
      } else DriveBackup.disable(context)
    })
    if (status.enabled) {
      val settings by context.talisApp.settings.settings.collectAsState()
      ToggleRow(stringResource(R.string.also_keep_a_pdf_of_each), settings.drivePdf, { on ->
        context.talisApp.settings.update { it.copy(drivePdf = on) }
        DriveBackup.markDirty(context)
        DriveBackup.backupSoon(context, delaySeconds = 0)
      }, detail = stringResource(R.string.opens_on_any_computer_or_phone))
    }
    WorkingText(
      when {
        note != null -> note
        status.running -> stringResource(R.string.backing_up_now)
        status.error != null -> status.error!!
        status.lastSuccess > 0 -> stringResource(R.string.last_backup_n, relativeTime(status.lastSuccess))
        else -> stringResource(R.string.no_backup_yet)
      },
      style = MaterialTheme.typography.bodySmall,
      color = if (note != null) c.blue else if (status.error != null) c.danger else c.inkFaint,
    )
    Spacer(Modifier.height(10.dp))
    ButtonRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      if (status.enabled) {
        QuietButton(stringResource(R.string.back_up_now), color = c.blue) {
          withToken(later(R.string.backing_up_to_drive)) { token ->
            DriveBackup.backupNow(context, token)
            later(R.string.backed_up_to_google_drive)
          }
        }
      }
      QuietButton(stringResource(R.string.restore_from_drive), color = restoreColor()) {
        withToken(later(R.string.restoring_from_drive)) { token -> describe(DriveBackup.restore(context, token)) }
      }
    }
  }
}
