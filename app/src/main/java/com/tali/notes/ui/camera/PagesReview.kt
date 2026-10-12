package com.tali.notes.ui.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tali.notes.R
import com.tali.notes.camera.PageImage
import com.tali.notes.theme.Tali
import com.tali.notes.ui.library.ConfirmDialog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * After Google's scanner: the pages it took, each one a tap to turn, then Save. On some tablets
 * Google's scanner hands pages back upside down (the owner's Tab S6 Lite, held one way), so each
 * page starts turned by what was learned for the way the tablet was held when Scan was opened
 * ([held], the screen's rotation), and when every page is saved turned the same half turn, that's
 * learned for next time. Laid out like the camera: the same in every language, in its dark colours.
 */
@Composable
fun PagesReview(uris: List<Uri>, held: Int, onClose: () -> Unit, onSave: (List<Uri>) -> Unit) {
  CameraWindow { ReviewBody(uris, held, onClose, onSave) }
}

/** A page: where Google's scanner left it, and a small copy to show (the full one is read again on saving). */
private class Page(val uri: Uri, val thumb: ImageBitmap)

@Composable
private fun ReviewBody(uris: List<Uri>, held: Int, onClose: () -> Unit, onSave: (List<Uri>) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val learned = remember { learnedTurn(context, held, SCAN_KEY) }
  var pages by remember { mutableStateOf<List<Page>?>(null) }
  val turns = remember { mutableStateListOf<Int>() }
  var saving by remember { mutableStateOf(false) }
  var discarding by remember { mutableStateOf(false) }
  LaunchedEffect(uris) {
    val loaded =
      withContext(Dispatchers.Default) {
        clearOld(context)
        uris.mapNotNull { uri -> loadUpright(context, uri, THUMB_SIDE)?.let { Page(uri, it.asImageBitmap()) } }
      }
    turns.clear()
    turns.addAll(List(loaded.size) { learned })
    pages = loaded
  }
  fun save() {
    val list = pages ?: return
    if (saving) return
    saving = true
    scope.launch {
      val files =
        withContext(Dispatchers.Default) {
          val dir = File(context.cacheDir, "camera").apply { mkdirs() }
          list.mapIndexedNotNull { i, p ->
            runCatching {
                val picture = loadUpright(context, p.uri) ?: return@mapIndexedNotNull null
                val file = File(dir, "scan-${System.currentTimeMillis()}-$i.jpg")
                file.outputStream().use { PageImage.turned(picture, turns[i]).compress(Bitmap.CompressFormat.JPEG, 92, it) }
                Uri.fromFile(file)
              }
              .getOrNull()
          }
        }
      // Every page turned the same half turn (or turned back to upright): remember it for this way of holding.
      val same = turns.distinct().singleOrNull()
      if (same != null && same % 2 == 0) setLearnedTurn(context, held, same, SCAN_KEY)
      onSave(files)
    }
  }
  androidx.activity.compose.BackHandler { discarding = true }

  val c = Tali.colors
  Box(Modifier.fillMaxSize().background(NIGHT).windowInsetsPadding(WindowInsets.safeDrawing)) {
    Column(Modifier.fillMaxSize()) {
      // × on the left, the title in the middle, Save on the right.
      Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Outlined.Close, stringResource(R.string.close)) { discarding = true }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
          Text(stringResource(R.string.cam_check_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
          Text(stringResource(R.string.cam_check_hint), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        }
        val n = pages?.size ?: 0
        CompactButton(
          if (n > 1) stringResource(R.string.cam_save_n, n) else stringResource(R.string.save),
          Icons.Outlined.Check,
          primary = true,
          Modifier.width(150.dp).graphicsLayer { alpha = if (n == 0 || saving) 0.5f else 1f },
        ) { if (n > 0) save() }
      }
      val list = pages
      if (list == null)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
      else
        LazyVerticalGrid(
          columns = GridCells.Adaptive(220.dp),
          contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
          horizontalArrangement = Arrangement.spacedBy(16.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          modifier = Modifier.fillMaxSize(),
        ) {
          itemsIndexed(list) { i, page ->
            PageTile(i + 1, page.thumb, turns.getOrElse(i) { 0 }) { if (i < turns.size) turns[i] = turns[i] + 1 }
          }
        }
    }
  }
  if (discarding) {
    // A question is a card like any other: it follows the language's direction, unlike the camera.
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides languageDirection()) {
    ConfirmDialog(
      title = stringResource(R.string.cam_discard_title),
      body = stringResource(R.string.cam_discard_body),
      confirm = stringResource(R.string.cam_discard),
      destructive = true,
      onDismiss = { discarding = false },
      onConfirm = {
        discarding = false
        onClose()
      },
    )
    }
  }
}

/** One page, square so it still fits once turned sideways; a tap turns it a quarter clockwise. */
@Composable
private fun PageTile(number: Int, thumb: ImageBitmap, turns: Int, onTurn: () -> Unit) {
  val c = Tali.colors
  val angle by animateFloatAsState(turns * 90f, tween(260, easing = com.tali.notes.ui.components.EaseOutStrong), label = "pageTurn")
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(1f)
        .clip(RoundedCornerShape(18.dp))
        .background(c.surface)
        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.cam_turn), onClick = onTurn)
        .padding(12.dp),
      contentAlignment = Alignment.Center,
    ) {
      Image(thumb, null, Modifier.fillMaxSize().graphicsLayer { rotationZ = angle }, contentScale = ContentScale.Fit)
      Box(Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.RotateRight, null, tint = c.accent, modifier = Modifier.size(20.dp))
      }
    }
    Spacer(Modifier.height(6.dp))
    Text("$number", style = MaterialTheme.typography.labelMedium, color = c.inkMuted)
  }
}

private const val SCAN_KEY = "scan"
private const val THUMB_SIDE = 700
