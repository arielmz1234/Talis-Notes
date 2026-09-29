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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tali.notes.CrashLog
import com.tali.notes.backup.BackupArchive
import com.tali.notes.backup.DriveBackup
import com.tali.notes.data.NotesRepository
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.ToggleRow
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

  fun describe(r: NotesRepository.MergeResult): String {
    val parts = buildList {
      if (r.added > 0) add(if (r.added == 1) "1 notebook added" else "${r.added} notebooks added")
      if (r.updated > 0) add(if (r.updated == 1) "1 notebook updated" else "${r.updated} notebooks updated")
    }
    return if (parts.isEmpty()) "Everything in the backup was already here." else parts.joinToString(", ") + "."
  }

  fun run(label: String, block: suspend () -> String) {
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
  val openFile =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      if (uri != null) run("Restoring…") {
        val result = context.contentResolver.openInputStream(uri)?.use { BackupArchive.restore(context, it) } ?: error("Couldn't open the file")
        describe(result)
      }
    }

  DialogCard(onDismiss, width = 560) {
    Text("Backups", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(16.dp))
    BackupCard(Icons.Outlined.TabletAndroid, "On this tablet", "Every stroke is saved the moment you write it, even without internet.")
    Spacer(Modifier.height(10.dp))
    BackupCard(Icons.Outlined.FolderZip, "Backup file", "Everything in one file: notebooks, pictures, pens, colours and your shapes. Keep it in Files, on a computer, or send it to yourself.") {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Save backup file") { saveFile.launch(BackupArchive.fileName()) }
        QuietButton("Restore from file") { openFile.launch(arrayOf(BackupArchive.MIME, "application/octet-stream")) }
      }
    }
    if (DriveBackup.available) {
      Spacer(Modifier.height(10.dp))
      DriveCard(onRun = ::run, describe = ::describe)
    }
    Spacer(Modifier.height(14.dp))
    Text(
      busy ?: message ?: "Restoring never deletes anything: notebooks from a backup are added, and if a notebook is in both places, the newest version is kept.",
      style = MaterialTheme.typography.bodySmall,
      color = if (busy != null || message != null) c.blue else c.inkFaint,
    )
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

@Composable
private fun DriveCard(onRun: (String, suspend () -> String) -> Unit, describe: (NotesRepository.MergeResult) -> String) {
  val c = Tali.colors
  val context = LocalContext.current
  val status by DriveBackup.status(context).collectAsState()
  // What to do once Google hands back a token after the consent screen.
  var afterConsent by remember { mutableStateOf<((String) -> Unit)?>(null) }
  val consent =
    rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
      val auth = if (result.resultCode == Activity.RESULT_OK) DriveBackup.authFromConsent(context, result.data) else null
      val next = afterConsent
      afterConsent = null
      if (auth is DriveBackup.Auth.Token && next != null) next(auth.value)
    }

  /** Gets a Google token (asking her to pick an account the first time), then runs [then]. */
  fun withToken(label: String, then: suspend (String) -> String) =
    onRun("Connecting to Google…") {
      when (val auth = DriveBackup.authorize(context)) {
        is DriveBackup.Auth.Token -> then(auth.value)
        is DriveBackup.Auth.NeedsConsent -> {
          afterConsent = { token -> onRun(label) { then(token) } }
          consent.launch(IntentSenderRequest.Builder(auth.intent).build())
          "Choose your Google account to continue."
        }
      }
    }

  BackupCard(if (status.enabled) Icons.Outlined.CloudDone else Icons.Outlined.CloudUpload, "Google Drive", "Backs up by itself a couple of minutes after you finish writing, into a “Tali's Notes” folder in your Drive. The app can only see its own backup, nothing else in your Drive.") {
    ToggleRow("Back up automatically", status.enabled, { on ->
      if (on) withToken("Backing up to Drive…") { token ->
        DriveBackup.enable(context)
        DriveBackup.backupNow(context, token)
        "Backed up to Google Drive."
      } else DriveBackup.disable(context)
    })
    Text(
      when {
        status.running -> "Backing up now…"
        status.error != null -> status.error!!
        status.lastSuccess > 0 -> "Last backup ${relativeTime(status.lastSuccess)}"
        else -> "No backup yet"
      },
      style = MaterialTheme.typography.bodySmall,
      color = if (status.error != null) c.danger else c.inkFaint,
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
      if (status.enabled) {
        QuietButton("Back up now") {
          withToken("Backing up to Drive…") { token ->
            DriveBackup.backupNow(context, token)
            "Backed up to Google Drive."
          }
        }
      }
      QuietButton("Restore from Drive") {
        withToken("Restoring from Drive…") { token -> describe(DriveBackup.restore(context, token)) }
      }
    }
  }
}
