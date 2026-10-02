package com.tali.notes.ui.notebook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowOutward
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tali.notes.canvas.Document
import com.tali.notes.model.CanvasItem
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.components.cloudSurface

private const val PAGE_ASPECT = 1322f / 1871f

/**
 * Every page of a paged notebook at a glance, in a popup of its own. Tapping a page lifts it up
 * large (like peeking at a photo), with Delete and Go to underneath; the last tile adds a page.
 */
@Composable
fun PagesOverview(
  vm: NotebookViewModel,
  doc: Document,
  count: Int,
  current: Int,
  version: Int,
  onDismiss: () -> Unit,
  onGo: (Int) -> Unit,
  onAdd: () -> Unit,
  onDelete: (Int) -> Unit,
) {
  val c = Tali.colors
  var peek by remember { mutableStateOf<Int?>(null) }
  // The last page peeked at, so it stays drawn while the peek fades away.
  var lastPeek by remember { mutableStateOf(0) }
  var removing by remember { mutableStateOf<Int?>(null) }
  LaunchedEffect(removing) {
    val i = removing ?: return@LaunchedEffect
    kotlinx.coroutines.delay(com.tali.notes.ui.components.PUFF_LEAVE_MS.toLong())
    onDelete(i)
    removing = null
  }
  // A snapshot of the notebook for the background renderer; refreshed after every edit.
  val items = remember(version, count) { doc.all.toList() }
  val grid = rememberLazyGridState()
  LaunchedEffect(Unit) { grid.scrollToItem(current.coerceIn(0, count - 1)) }
  Dialog(onDismissRequest = { if (peek != null) peek = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.Center) {
      Column(
        Modifier.fillMaxWidth(0.86f)
          .fillMaxHeight(0.88f)
          .cloudSurface(26.dp, shadowDepth = 12.dp)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
          .padding(start = 28.dp, end = 28.dp, top = 24.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text("Pages", style = MaterialTheme.typography.titleLarge, color = c.ink)
            Text(
              (if (count == 1) "1 page" else "$count pages") + " · tap a page to look closer",
              style = MaterialTheme.typography.bodyMedium,
              color = c.inkMuted,
            )
          }
          QuietButton("Done", onClick = onDismiss)
        }
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
          columns = GridCells.Adaptive(132.dp),
          state = grid,
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp),
          contentPadding = PaddingValues(bottom = 28.dp, top = 4.dp, start = 4.dp, end = 4.dp),
          modifier = Modifier.weight(1f),
        ) {
          items(count, key = { it }) { i ->
            PageTile(vm, items, i, version, selected = i == current, leaving = i == removing, modifier = Modifier.animateItem()) {
              lastPeek = i
              peek = i
            }
          }
          item(key = "add") { Box(Modifier.animateItem()) { AddPageTile(onAdd) } }
        }
      }
      AnimatedVisibility(
        visible = peek != null,
        enter = fadeIn(tween(160)) + scaleIn(tween(220), initialScale = 0.86f),
        exit = fadeOut(tween(130)) + scaleOut(tween(130), targetScale = 0.92f),
      ) {
        PagePeek(vm, items, lastPeek, version, single = count == 1, onClose = { peek = null }, onGo = { onGo(lastPeek) }) {
          // The peek closes, then the page's tile lifts away before it's removed.
          peek = null
          removing = lastPeek
        }
      }
    }
  }
}

@Composable
private fun rememberPage(vm: NotebookViewModel, items: List<CanvasItem>, index: Int, version: Int, widthPx: Int): ImageBitmap? {
  val state =
    produceState<ImageBitmap?>(null, index, version, items, widthPx) {
      // Don't keep showing the old page while the new picture is made (after a delete, say).
      value = null
      value = vm.pageThumbnail(items, index, widthPx)?.asImageBitmap()
    }
  return state.value
}

@Composable
private fun PageTile(vm: NotebookViewModel, items: List<CanvasItem>, index: Int, version: Int, selected: Boolean, leaving: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  val c = Tali.colors
  val image = rememberPage(vm, items, index, version, 480)
  val shape = RoundedCornerShape(10.dp)
  // A deleted page dissolves into a small puff of cloud.
  com.tali.notes.ui.components.PuffBox(leaving = leaving, appearing = false, modifier = modifier) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(PAGE_ASPECT)
        .shadow(if (selected) 10.dp else 5.dp, shape, ambientColor = Color(0x224F7BE8), spotColor = Color(0x337A4ED6))
        .clip(shape)
        .background(Color.White)
        .border(if (selected) 2.5.dp else 1.dp, if (selected) c.accent else c.line, shape)
        .clickable(onClickLabel = "Look at page ${index + 1}", onClick = onClick)
    ) {
      if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
    Spacer(Modifier.height(8.dp))
    Text(
      "${index + 1}",
      style = MaterialTheme.typography.labelLarge,
      color = if (selected) c.onAccent else c.inkMuted,
      modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) c.accent else Color.Transparent).padding(horizontal = 10.dp, vertical = 2.dp),
    )
  }
  }
}

@Composable
private fun AddPageTile(onAdd: () -> Unit) {
  val c = Tali.colors
  val line = c.inkFaint
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(PAGE_ASPECT)
        .clip(RoundedCornerShape(10.dp))
        .drawBehind {
          drawRoundRect(
            line.copy(alpha = 0.6f),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))),
          )
        }
        .clickable(onClickLabel = "Add a page", onClick = onAdd),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.Add, null, tint = c.accent, modifier = Modifier.size(30.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text("Add page", style = MaterialTheme.typography.labelLarge, color = c.inkMuted, modifier = Modifier.padding(vertical = 2.dp))
  }
}

/**
 * One page lifted up large over the dimmed overview. Its number sits above it in a soft grey, with
 * two round buttons at the end of that line: delete, and go to the page.
 */
@Composable
private fun PagePeek(
  vm: NotebookViewModel,
  items: List<CanvasItem>,
  index: Int,
  version: Int,
  single: Boolean,
  onClose: () -> Unit,
  onGo: () -> Unit,
  onDelete: () -> Unit,
) {
  val c = Tali.colors
  val small = rememberPage(vm, items, index, version, 480)
  val large = rememberPage(vm, items, index, version, 1100)
  BoxWithConstraints(
    Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    contentAlignment = Alignment.Center,
  ) {
    // As tall as the screen allows lying down; held upright the width decides, so it never fills it.
    val pageWidth = minOf((maxHeight - 72.dp) * 0.92f * PAGE_ASPECT, maxWidth * 0.8f)
    Column(Modifier.width(pageWidth)) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text("Page ${index + 1}", style = MaterialTheme.typography.titleMedium, color = c.inkFaint, modifier = Modifier.weight(1f).padding(start = 2.dp))
        PeekButton(Icons.Outlined.DeleteOutline, if (single) "Clear page" else "Delete page", c.danger, onDelete)
        Spacer(Modifier.width(10.dp))
        PeekButton(Icons.Outlined.ArrowOutward, "Go to page", c.accent, onGo)
      }
      Box(
        Modifier.fillMaxWidth()
          .aspectRatio(PAGE_ASPECT)
          .shadow(24.dp, RoundedCornerShape(14.dp))
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White)
          .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
      ) {
        // The small picture stands in until the sharp one is ready.
        (large ?: small)?.let { Image(it, "Page ${index + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
      }
    }
  }
}

@Composable
private fun PeekButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
  Box(
    Modifier.size(44.dp).shadow(6.dp, CircleShape).clip(CircleShape).background(Color.White).clickable(onClickLabel = label, role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
  }
}
