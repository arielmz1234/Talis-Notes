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
 * A PDF or pictures came from another app: where do they go? A new notebook (named after the
 * file), the notebook she has open, or one of her recent notebooks. A backup file asks to restore.
 * [onGo] gets the notebook to add them to.
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
  val pdf = files.kind == Incoming.Kind.PDF
  var busy by remember { mutableStateOf(false) }

  DialogCard(onDismiss, width = 480) {
    Text(if (pdf) "Add “$what”" else "Add ${if (files.uris.size > 1) what else "the picture"}", style = MaterialTheme.typography.titleLarge, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.height(4.dp))
    Text(if (pdf) "Its pages go in as pages you can write on." else "Into which notebook?", style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
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

/** A new notebook for what came in, started the way her new notebooks start. Returns its id. */
private suspend fun newNotebookFor(app: TalisApp, files: Incoming.Files): String {
  val s = app.settings.settings.value
  val meta =
    app.repository.create(
      Incoming.title(files),
      s.defaultPaper,
      // A PDF's pages go onto A4 pages, one each.
      pages = if (files.kind == Incoming.Kind.PDF || s.defaultPaged) 1 else null,
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
    Text(
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
