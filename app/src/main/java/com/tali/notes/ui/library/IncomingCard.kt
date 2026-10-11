package com.tali.notes.ui.library

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import com.tali.notes.backup.DriveBackup
import com.tali.notes.backup.RestoreFiles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tali.notes.CrashLog
import com.tali.notes.Incoming
import com.tali.notes.TalisApp
import com.tali.notes.backup.BackupArchive
import com.tali.notes.data.NotebookMeta
import com.tali.notes.talisApp
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.relativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pictures came from another app: where do they go? A new notebook, the notebook she has open, or
 * one of her recent notebooks. A backup file asks to restore. (A PDF never asks: it opens in a new
 * notebook of its own, [openPdfDraft].) [onGo] gets the notebook to add them to.
 */
@Composable
fun IncomingCard(files: Incoming.Files, openNotebook: String?, onGo: (String) -> Unit, onDismiss: () -> Unit) {
  if (files.kind == Incoming.Kind.BACKUP) {
    RestoreCard(files, onDismiss)
    return
  }
  val c = Tali.colors
  val context = LocalContext.current
  val app = context.talisApp
  val scope = rememberCoroutineScope()
  val notebooks by app.repository.notebooks.collectAsState()
  val settings by app.settings.settings.collectAsState()
  val byId = notebooks.associateBy { it.id }
  // The open notebook first, then the ones she opened last.
  val choices = (listOfNotNull(openNotebook) + settings.recentNotebooks).distinct().mapNotNull { byId[it] }.take(4)
  val what = Incoming.title(files)
  var busy by remember { mutableStateOf(false) }

  DialogCard(onDismiss, width = 480) {
    Text("Add ${if (files.uris.size > 1) what else "the picture"}", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Text("Into which notebook?", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))
    CardBody {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ChoiceRow(0, "New notebook", "Called “$what”", leading = {
          Box(Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(7.dp)).background(c.accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Add, null, tint = c.accent, modifier = Modifier.size(22.dp))
          }
        }) {
          if (busy) return@ChoiceRow
          busy = true
          scope.launch {
            runCatching { newNotebookFor(app, files) }.onSuccess(onGo).onFailure {
              CrashLog.recovered(context, it)
              busy = false
            }
          }
        }
        choices.forEachIndexed { i, nb ->
          ChoiceRow(i + 1, nb.title.ifBlank { "Untitled" }, if (nb.id == openNotebook) "Open now" else "Edited " + relativeTime(nb.updated).replaceFirstChar { it.lowercase() }, leading = { Thumb(app, nb) }) { if (!busy) onGo(nb.id) }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { QuietButton("Cancel", onClick = onDismiss) }
  }
}

/**
 * A PDF from another app opens straight into a new notebook she's only looking at
 * ([com.tali.notes.data.NotebookMeta.draft]) until she saves it, on landscape pages when most of
 * the PDF's pages are wider than tall (slides). Returns its id.
 */
suspend fun openPdfDraft(app: TalisApp, files: Incoming.Files): String {
  val s = app.settings.settings.value
  val landscape = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { pdfMostlyLandscape(app, files.uris.first()) }
  // With *Add them to my notebooks* on, it's one of her notebooks straight away.
  return app.repository.create(Incoming.title(files), s.defaultPaper, pages = 1, customPaper = s.defaultCustomPaper, landscape = landscape, draft = !s.pdfsToLibrary).id
}

/** Whether most of the PDF's pages (the first 30) are wider than they are tall. */
fun pdfMostlyLandscape(context: android.content.Context, uri: android.net.Uri): Boolean =
  runCatching {
      context.contentResolver.openFileDescriptor(uri, "r")!!.use { fd ->
        android.graphics.pdf.PdfRenderer(fd).use { r ->
          val n = minOf(r.pageCount, 30)
          val wide = (0 until n).count { i -> r.openPage(i).use { it.width > it.height } }
          n > 0 && wide * 2 > n
        }
      }
    }
    .getOrDefault(false)

/** A new notebook for what came in, started the way her new notebooks start. Returns its id. */
private suspend fun newNotebookFor(app: TalisApp, files: Incoming.Files): String {
  val s = app.settings.settings.value
  val meta =
    app.repository.create(
      Incoming.title(files),
      s.defaultPaper,
      pages = if (s.defaultPaged) 1 else null,
      customPaper = s.defaultCustomPaper,
      landscape = s.defaultLandscape,
    )
  return meta.id
}

@Composable
private fun ChoiceRow(index: Int, title: String, detail: String, leading: @Composable () -> Unit, onClick: () -> Unit) {
  val c = Tali.colors
  // Rows drift in one after another, like Quick open's.
  val shown = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    delay(index * 30L)
    shown.animateTo(1f, tween(240, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)))
  }
  Row(
    Modifier.graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 8.dp.toPx()
      }
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 7.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    leading()
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(detail, style = MaterialTheme.typography.bodySmall, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}

@Composable
private fun Thumb(app: TalisApp, nb: NotebookMeta) {
  val c = Tali.colors
  val thumb = com.tali.notes.ui.components.rememberThumbnail(app.repository.thumbnailFile(nb.id), nb.thumbVersion)
  Box(Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(7.dp)).background(Color(nb.cover).copy(alpha = 0.25f)).border(1.dp, c.line, RoundedCornerShape(7.dp))) {
    if (thumb != null) Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    Box(Modifier.fillMaxHeight().width(4.dp).background(Color(nb.cover)))
  }
}

/** "2 notebooks added, 1 notebook updated." for what a restore did. */
fun describeRestore(r: com.tali.notes.data.NotesRepository.MergeResult): String {
  val parts = buildList {
    if (r.added > 0) add(if (r.added == 1) "1 notebook added" else "${r.added} notebooks added")
    if (r.updated > 0) add(if (r.updated == 1) "1 notebook updated" else "${r.updated} notebooks updated")
  }
  return if (parts.isEmpty()) "Everything in the backup was already here." else parts.joinToString(", ") + "."
}

/**
 * The first time the app opens with nothing in it (a new tablet, or the app reinstalled): offers to
 * bring every notebook back, from Google Drive (which then keeps backing up there), from backup
 * files (several at once: the Drive folder holds one per notebook), or from a whole folder.
 */
@Composable
fun WelcomeRestoreCard(onDismiss: () -> Unit) {
  val c = Tali.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf<String?>(null) }
  var result by remember { mutableStateOf<String?>(null) }
  var restored by remember { mutableStateOf(false) }

  fun run(label: String, block: suspend () -> String) {
    if (busy != null) return
    busy = label
    scope.launch {
      result =
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

  fun finished(r: com.tali.notes.data.NotesRepository.MergeResult, extra: String = ""): String {
    restored = true
    return describeRestore(r) + extra
  }

  // Until a restore finishes the choices stay up, so "Choose your Google account…" sits above them.
  val drive = rememberDriveAuth(::run)
  val files =
    androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
      if (uris.isNotEmpty()) run("Restoring…") {
        val r = RestoreFiles.restore(context, uris)
        // One whole-app backup file: keep backing up into it, as before.
        val kept = uris.size == 1 && com.tali.notes.backup.AutoBackup.adopt(context, uris.first())
        finished(r, if (kept) " It'll keep backing up into that file by itself." else "")
      }
    }
  val folder =
    androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { tree ->
      if (tree != null) run("Restoring…") { finished(RestoreFiles.restore(context, RestoreFiles.inFolder(context, tree))) }
    }

  DialogCard({ if (busy == null) onDismiss() }, width = 640) {
    Text(if (restored) "Welcome back" else "Welcome to Tali's Notes", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(10.dp))
    com.tali.notes.ui.components.WorkingText(
      busy ?: result ?: "New tablet, or the app installed again? If you have a backup, you can bring all your notebooks back now. You can also do it later from Backups, at the bottom left.",
      style = MaterialTheme.typography.bodyMedium,
      color = if (busy != null || result != null) c.blue else c.inkMuted,
    )
    if (!restored) {
      Spacer(Modifier.height(18.dp))
      Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min).graphicsLayer { alpha = if (busy != null) 0.5f else 1f }, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (DriveBackup.available) {
          RestoreChoice(Icons.Outlined.CloudDownload, "Google Drive", "Every notebook the app backed up there. It keeps backing up there too.", primary = true) {
            drive("Restoring from Drive…") { token ->
              val r = DriveBackup.restore(context, token)
              DriveBackup.enable(context)
              finished(r, " Google Drive will keep backing up from now on.")
            }
          }
        }
        RestoreChoice(Icons.Outlined.Description, "Backup files", "Pick one, or select several to bring them all back.") {
          if (busy == null) files.launch(arrayOf(BackupArchive.MIME, "application/x-zip-compressed", "application/octet-stream"))
        }
        RestoreChoice(Icons.Outlined.Folder, "A folder", "Every backup in a folder on the tablet or a memory card.") {
          if (busy == null) folder.launch(null)
        }
      }
    }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      if (restored) PrimaryButton("Done", onClick = onDismiss) else QuietButton("Start fresh") { if (busy == null) onDismiss() }
    }
  }
}

/** One way to restore: a tile with an icon, a name and a line about it. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.RestoreChoice(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, primary: Boolean = false, onClick: () -> Unit) {
  val c = Tali.colors
  val tint = if (primary) c.accent else Color(if (c.isDark) 0xFF6FD3B5 else 0xFF1C8466)
  Column(
    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(if (primary) c.accentSoft else c.surface.copy(alpha = 0.7f))
      .border(1.dp, if (primary) tint.copy(alpha = 0.35f) else c.line, RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(14.dp),
  ) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
    Spacer(Modifier.height(2.dp))
    Text(body, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
  }
}

/** Her backup file was opened with the app: restore it (merging, never deleting)? */
@Composable
private fun RestoreCard(files: Incoming.Files, onDismiss: () -> Unit) {
  val c = Tali.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<String?>(null) }
  DialogCard({ if (!busy) onDismiss() }, width = 480) {
    Text(if (result != null) "Backup restored" else "Restore this backup?", style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text(files.name ?: "Backup file", style = MaterialTheme.typography.bodySmall, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(14.dp))
    com.tali.notes.ui.components.WorkingText(
      result ?: if (busy) "Restoring…" else "Nothing on this tablet is deleted: notebooks from the backup are added, and if a notebook is in both places, the newest version is kept.",
      style = MaterialTheme.typography.bodyMedium,
      color = if (result != null || busy) c.blue else c.inkMuted,
    )
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
      if (result != null) {
        PrimaryButton("Done", onClick = onDismiss)
      } else {
        QuietButton("Cancel") { if (!busy) onDismiss() }
        PrimaryButton("Restore") {
          if (busy) return@PrimaryButton
          busy = true
          scope.launch {
            result =
              try {
                val r = context.contentResolver.openInputStream(files.uris.first())?.use { BackupArchive.restore(context, it) } ?: error("Couldn't open the file")
                val parts = buildList {
                  if (r.added > 0) add(if (r.added == 1) "1 notebook added" else "${r.added} notebooks added")
                  if (r.updated > 0) add(if (r.updated == 1) "1 notebook updated" else "${r.updated} notebooks updated")
                }
                if (parts.isEmpty()) "Everything in the backup was already here." else parts.joinToString(", ") + "."
              } catch (e: BackupArchive.InvalidBackup) {
                e.message
              } catch (t: Throwable) {
                CrashLog.recovered(context, t)
                "Something went wrong: ${t.message ?: t.javaClass.simpleName}"
              }
            busy = false
          }
        }
      }
    }
  }
}
