package com.tali.notes.ui.library

import com.tali.notes.later
import androidx.compose.ui.res.stringResource
import com.tali.notes.R
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
 * notebook of its own, [openPdfDraft].) [onGo] gets the notebook to add them to; [onMade] the
 * Picture item made of them on their own (already holding them).
 */
@Composable
fun IncomingCard(files: Incoming.Files, openNotebook: String?, onGo: (String) -> Unit, onMade: (String) -> Unit, onDismiss: () -> Unit) {
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
    Text(if (files.uris.size > 1) stringResource(R.string.add_these, what) else stringResource(R.string.add_the_picture), style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.into_which_notebook), style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    Spacer(Modifier.height(14.dp))
    CardBody {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // On their own: a Picture item in the library (a picture on each page), not a notebook.
        val tint = kindTint(com.tali.notes.data.DocKind.PICTURE)
        ChoiceRow(0, stringResource(R.string.on_its_own), stringResource(R.string.called_n, what), leading = {
          Box(Modifier.size(width = 52.dp, height = 38.dp).clip(RoundedCornerShape(7.dp)).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(kindIcon(com.tali.notes.data.DocKind.PICTURE), null, tint = tint, modifier = Modifier.size(22.dp))
          }
        }) {
          if (busy) return@ChoiceRow
          busy = true
          scope.launch {
            runCatching { com.tali.notes.Imports.pictures(app, files.uris, null)?.id ?: error("no pictures") }.onSuccess(onMade).onFailure {
              CrashLog.recovered(context, it)
              busy = false
            }
          }
        }
        choices.forEachIndexed { i, nb ->
          ChoiceRow(i + 1, nb.title.ifBlank { stringResource(R.string.untitled) }, if (nb.id == openNotebook) stringResource(R.string.open_now) else stringResource(R.string.edited) + relativeTime(nb.updated).replaceFirstChar { it.lowercase() }, leading = { Thumb(app, nb) }) { if (!busy) onGo(nb.id) }
        }
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { QuietButton(stringResource(R.string.cancel), onClick = onDismiss) }
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
  return app.repository.create(Incoming.title(files), s.defaultPaper, pages = 1, customPaper = s.defaultCustomPaper, landscape = landscape, draft = !s.pdfsToLibrary, kind = com.tali.notes.data.DocKind.PDF).id
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
    if (r.added > 0) add(if (r.added == 1) later(R.string.t_1_notebook_added) else later(R.string.n_notebooks_added, r.added))
    if (r.updated > 0) add(if (r.updated == 1) later(R.string.t_1_notebook_updated) else later(R.string.n_notebooks_updated, r.updated))
  }
  return if (parts.isEmpty()) later(R.string.everything_in_the_backup_was_already) else parts.joinToString(", ") + "."
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
          later(R.string.something_went_wrong_n, t.message ?: t.javaClass.simpleName)
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
      if (uris.isNotEmpty()) run(later(R.string.restoring)) {
        val r = RestoreFiles.restore(context, uris)
        // One whole-app backup file: keep backing up into it, as before.
        val kept = uris.size == 1 && com.tali.notes.backup.AutoBackup.adopt(context, uris.first())
        finished(r, if (kept) later(R.string.it_ll_keep_backing_up_into) else "")
      }
    }
  val folder =
    androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { tree ->
      if (tree != null) run(later(R.string.restoring)) { finished(RestoreFiles.restore(context, RestoreFiles.inFolder(context, tree))) }
    }

  DialogCard({ if (busy == null) onDismiss() }, width = 640) {
    Text(if (restored) stringResource(R.string.welcome_back) else stringResource(R.string.welcome_to_tali_s_notes), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(10.dp))
    com.tali.notes.ui.components.WorkingText(
      busy ?: result ?: stringResource(R.string.new_tablet_or_the_app_installed),
      style = MaterialTheme.typography.bodyMedium,
      color = if (busy != null || result != null) c.blue else c.inkMuted,
    )
    if (!restored) {
      Spacer(Modifier.height(18.dp))
      // Side by side on a tablet; on a phone, one under the other.
      val phone = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < PHONE_DP
      val tiles: @Composable (Modifier) -> Unit = { tile ->
        if (DriveBackup.available) {
          RestoreChoice(tile, Icons.Outlined.CloudDownload, stringResource(R.string.google_drive), stringResource(R.string.every_notebook_the_app_backed_up), recommended = true) {
            drive(later(R.string.restoring_from_drive)) { token ->
              val r = DriveBackup.restore(context, token)
              DriveBackup.enable(context)
              finished(r, later(R.string.google_drive_will_keep_backing_up))
            }
          }
        }
        RestoreChoice(tile, Icons.Outlined.Description, stringResource(R.string.backup_files), stringResource(R.string.pick_one_or_select_several_to)) {
          if (busy == null) files.launch(arrayOf(BackupArchive.MIME, "application/x-zip-compressed", "application/octet-stream"))
        }
        RestoreChoice(tile, Icons.Outlined.Folder, stringResource(R.string.a_folder), stringResource(R.string.every_backup_in_a_folder_on)) {
          if (busy == null) folder.launch(null)
        }
      }
      val dim = Modifier.graphicsLayer { alpha = if (busy != null) 0.5f else 1f }
      if (phone) Column(dim.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.fillMaxWidth()) }
      else Row(dim.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.weight(1f).fillMaxHeight()) }
    }
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
      if (restored) PrimaryButton(stringResource(R.string.done), onClick = onDismiss) else QuietButton(stringResource(R.string.start_fresh)) { if (busy == null) onDismiss() }
    }
  }
}

/**
 * One way to restore: a tile with an icon, a name and a line about it. Pressed, a tile washes lilac
 * and sinks in a little. The [recommended] one (Google Drive, which keeps backing up afterwards)
 * wears an accent outline and a small *Recommended* tag, but isn't picked for her.
 */
@Composable
private fun RestoreChoice(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String, recommended: Boolean = false, onClick: () -> Unit) {
  val c = Tali.colors
  val tint = Color(if (c.isDark) 0xFF6FD3B5 else 0xFF1C8466)
  val press = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val pressed by press.collectIsPressedAsState()
  val wash by androidx.compose.animation.animateColorAsState(if (pressed) c.accentSoft else c.surface.copy(alpha = 0.7f), tween(if (pressed) 90 else 220), label = "wash")
  val sink by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.97f else 1f, tween(if (pressed) 90 else 220), label = "sink")
  Column(
    modifier.graphicsLayer { scaleX = sink; scaleY = sink }.clip(RoundedCornerShape(18.dp)).background(wash)
      .border(if (recommended) 1.5.dp else 1.dp, if (recommended) c.accent else c.line, RoundedCornerShape(18.dp)).clickable(interactionSource = press, indication = null, onClick = onClick).padding(14.dp),
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
      if (recommended) {
        Spacer(Modifier.weight(1f))
        Text(
          stringResource(R.string.recommended),
          style = MaterialTheme.typography.labelSmall,
          color = c.onAccent,
          modifier = Modifier.clip(RoundedCornerShape(50)).background(c.accentBrush).padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    }
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
    Text(if (result != null) stringResource(R.string.backup_restored) else stringResource(R.string.restore_this_backup), style = MaterialTheme.typography.titleLarge, color = c.ink)
    Spacer(Modifier.height(4.dp))
    Text(files.name ?: stringResource(R.string.backup_file), style = MaterialTheme.typography.bodySmall, color = c.inkFaint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(14.dp))
    com.tali.notes.ui.components.WorkingText(
      result ?: if (busy) stringResource(R.string.restoring) else stringResource(R.string.nothing_on_this_tablet_is_deleted),
      style = MaterialTheme.typography.bodyMedium,
      color = if (result != null || busy) c.blue else c.inkMuted,
    )
    Spacer(Modifier.height(20.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
      if (result != null) {
        PrimaryButton(stringResource(R.string.done), onClick = onDismiss)
      } else {
        QuietButton(stringResource(R.string.cancel)) { if (!busy) onDismiss() }
        PrimaryButton(stringResource(R.string.restore)) {
          if (busy) return@PrimaryButton
          busy = true
          scope.launch {
            result =
              try {
                val r = context.contentResolver.openInputStream(files.uris.first())?.use { BackupArchive.restore(context, it) } ?: error(later(R.string.couldn_t_open_the_file))
                val parts = buildList {
                  if (r.added > 0) add(if (r.added == 1) later(R.string.t_1_notebook_added) else later(R.string.n_notebooks_added, r.added))
                  if (r.updated > 0) add(if (r.updated == 1) later(R.string.t_1_notebook_updated) else later(R.string.n_notebooks_updated, r.updated))
                }
                if (parts.isEmpty()) later(R.string.everything_in_the_backup_was_already) else parts.joinToString(", ") + "."
              } catch (e: BackupArchive.InvalidBackup) {
                e.message
              } catch (t: Throwable) {
                CrashLog.recovered(context, t)
                later(R.string.something_went_wrong_n, t.message ?: t.javaClass.simpleName)
              }
            busy = false
          }
        }
      }
    }
  }
}
