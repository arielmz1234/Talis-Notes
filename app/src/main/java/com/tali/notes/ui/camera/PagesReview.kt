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
 * After Google's scanner: the pages it took, each one a tap to turn, then Save (Turn all turns every
 * page). Google's scanner turns Hebrew pages upside down (it reads text direction as if it were Latin),
 * so each page is read first ([PageOrientation]): clear Latin text decides; a page without any
 * (Hebrew, or no text) starts turned by what's been learned for such pages, a half turn at first.
 * When she saves such pages all turned the same way, that's learned. Laid out like the camera: the
 * same in every language, in its dark colours.
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
  // How pages without Latin text come out right: turned over at first (Google turns Hebrew over).
  val learned = remember { context.getSharedPreferences("camera", android.content.Context.MODE_PRIVATE).getInt(NO_LATIN_KEY, 2) }
  val readings = remember { mutableStateListOf<com.tali.notes.camera.PageOrientation.Reading>() }
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
    // Each page read for its text's direction (Google's scanner turns Hebrew pages over).
    val read =
      withContext(Dispatchers.Default) {
        loaded.map { p ->
          runCatching { loadUpright(context, p.uri, CHECK_SIDE)?.let { com.tali.notes.camera.PageOrientation.read(it) } }.getOrNull() ?: com.tali.notes.camera.PageOrientation.Reading.UPRIGHT
        }
      }
    readings.clear()
    readings.addAll(read)
    turns.addAll(
      read.map {
        when (it) {
          com.tali.notes.camera.PageOrientation.Reading.UPRIGHT -> 0
          com.tali.notes.camera.PageOrientation.Reading.UPSIDE_DOWN -> 2
          com.tali.notes.camera.PageOrientation.Reading.NO_LATIN -> learned
        }
      }
    )
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
      // Pages without Latin text, all saved turned the same way (upright or over): that's how they come out from now on.
      val noLatin = turns.filterIndexed { i, _ -> readings.getOrNull(i) == com.tali.notes.camera.PageOrientation.Reading.NO_LATIN }.map { ((it % 4) + 4) % 4 }.distinct()
      if (noLatin.size == 1 && noLatin[0] % 2 == 0) context.getSharedPreferences("camera", android.content.Context.MODE_PRIVATE).edit().putInt(NO_LATIN_KEY, noLatin[0]).apply()
      onSave(files)
    }
  }
  androidx.activity.compose.BackHandler { discarding = true }

  val c = Tali.colors
  Box(Modifier.fillMaxSize().background(NIGHT).windowInsetsPadding(WindowInsets.safeDrawing)) {
    Column(Modifier.fillMaxSize()) {
      // × on the left, the title in the middle, Turn all and Save on the right; on a phone the
      // buttons go on a line of their own under the title.
      val n = pages?.size ?: 0
      val phone = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < com.tali.notes.ui.library.PHONE_DP
      val buttons: @Composable (Modifier, Modifier) -> Unit = { turnMod, saveMod ->
        if (n > 1) {
          CompactButton(stringResource(R.string.cam_turn_all), Icons.Outlined.RotateRight, primary = false, turnMod) { for (i in turns.indices) turns[i] = turns[i] + 1 }
          Spacer(Modifier.width(8.dp))
        }
        CompactButton(
          if (n > 1) stringResource(R.string.cam_save_n, n) else stringResource(R.string.save),
          Icons.Outlined.Check,
          primary = true,
          saveMod.graphicsLayer { alpha = if (n == 0 || saving) 0.5f else 1f },
        ) { if (n > 0) save() }
      }
      Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(Icons.Outlined.Close, stringResource(R.string.close)) { discarding = true }
        Column(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalAlignment = if (phone) Alignment.Start else Alignment.CenterHorizontally) {
          Text(stringResource(R.string.cam_check_title), style = MaterialTheme.typography.titleLarge, color = c.ink)
          Text(stringResource(R.string.cam_check_hint), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
        }
        if (!phone) buttons(Modifier.width(130.dp), Modifier.width(150.dp))
      }
      if (phone) Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) { buttons(Modifier.weight(1f), Modifier.weight(1f)) }
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

private const val NO_LATIN_KEY = "no_latin_turn"
/** Read at this size to tell which way up a page is. */
private const val CHECK_SIDE = 1600
private const val THUMB_SIDE = 700
