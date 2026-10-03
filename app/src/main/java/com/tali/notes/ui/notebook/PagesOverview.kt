package com.tali.notes.ui.notebook

import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
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
import androidx.compose.foundation.lazy.grid.items
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

/** Width over height of this notebook's pages (portrait or landscape A4), set by [PagesOverview]. */
private val LocalPageAspect = androidx.compose.runtime.staticCompositionLocalOf { com.tali.notes.canvas.Pages.PORTRAIT.aspect }

private val PAGE_ASPECT: Float
  @Composable get() = LocalPageAspect.current

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
  onMove: (from: Int, to: Int) -> Unit,
  onDelete: (Int) -> Unit,
  onRearrange: (order: List<Int>, message: String) -> Unit,
) = androidx.compose.runtime.CompositionLocalProvider(LocalPageAspect provides vm.pageLayout.aspect) {
  val c = Tali.colors
  var peek by remember { mutableStateOf<Int?>(null) }
  // The last page peeked at, so it stays drawn while the peek fades away.
  var lastPeek by remember { mutableStateOf(0) }
  var removing by remember { mutableStateOf<Int?>(null) }
  // Deleting a page asks first.
  var confirming by remember { mutableStateOf<Int?>(null) }
  // Picking several pages (to duplicate or delete them together); null when not picking.
  var picked by remember { mutableStateOf<Set<Int>?>(null) }
  var deletingPicked by remember { mutableStateOf(false) }
  // Pages puffing away together after a multi-delete, then the new order to lay them out in.
  var vanishing by remember { mutableStateOf(emptySet<Int>()) }
  var pendingOrder by remember { mutableStateOf<Pair<List<Int>, String>?>(null) }
  LaunchedEffect(pendingOrder) {
    val (order, message) = pendingOrder ?: return@LaunchedEffect
    kotlinx.coroutines.delay(com.tali.notes.ui.components.PUFF_LEAVE_MS.toLong())
    onRearrange(order, message)
    vanishing = emptySet()
    pendingOrder = null
  }
  LaunchedEffect(count) { picked = null }
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
  // Hold a page (finger or pen) and drag it to a new place: the others make way as it passes, and
  // it moves for real when let go. `order` is the pages as shown, by their real index.
  var order by remember(count, version) { mutableStateOf((0 until count).toList()) }
  var dragging by remember { mutableStateOf<Int?>(null) }
  var dragOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
  // Each page's latest picture, so after a move the tiles keep showing the right page while
  // their new pictures are made (instead of flashing blank).
  val thumbs = remember(count) { androidx.compose.runtime.mutableStateMapOf<Int, ImageBitmap>() }
  // Right after a drop the tiles settle in place without sliding back and forth.
  var settling by remember { mutableStateOf(false) }
  LaunchedEffect(version) { settling = false }
  val edgePx = with(androidx.compose.ui.platform.LocalDensity.current) { 80.dp.toPx() }
  fun itemInfo(key: Any) = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
  fun dragBy(delta: androidx.compose.ui.geometry.Offset) {
    val key = dragging ?: return
    dragOffset += delta
    val me = itemInfo(key) ?: return
    val centre = androidx.compose.ui.geometry.Offset(me.offset.x + dragOffset.x + me.size.width / 2f, me.offset.y + dragOffset.y + me.size.height / 2f)
    val target = grid.layoutInfo.visibleItemsInfo.firstOrNull { info ->
      info.key is Int && info.key != key &&
        centre.x >= info.offset.x && centre.x <= info.offset.x + info.size.width &&
        centre.y >= info.offset.y && centre.y <= info.offset.y + info.size.height
    }
    if (target != null) {
      val from = order.indexOf(key)
      val to = order.indexOf(target.key as Int)
      if (from >= 0 && to >= 0) {
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // The tile now sits where the target was: keep it under the finger.
        dragOffset += androidx.compose.ui.geometry.Offset((me.offset.x - target.offset.x).toFloat(), (me.offset.y - target.offset.y).toFloat())
      }
    }
    // Near the top or bottom edge, the grid scrolls along.
    val view = grid.layoutInfo.viewportSize.height
    val edge = edgePx
    val y = me.offset.y + dragOffset.y + me.size.height / 2f
    val scroll = when {
      y < edge -> -(edge - y) * 0.25f
      y > view - edge -> (y - (view - edge)) * 0.25f
      else -> 0f
    }
    if (scroll != 0f) {
      val moved = grid.dispatchRawDelta(scroll)
      dragOffset += androidx.compose.ui.geometry.Offset(0f, moved)
    }
  }
  fun drop() {
    val key = dragging ?: return
    dragging = null
    dragOffset = androidx.compose.ui.geometry.Offset.Zero
    val to = order.indexOf(key)
    if (to >= 0 && to != key) {
      // The pictures follow their pages to their new places.
      val moved = order.mapIndexed { pos, page -> pos to thumbs[page] }
      moved.forEach { (pos, img) -> if (img != null) thumbs[pos] = img else thumbs.remove(pos) }
      settling = true
      onMove(key, to)
    } else {
      order = (0 until count).toList()
    }
  }
  Dialog(onDismissRequest = { if (peek != null) peek = null else if (picked != null) picked = null else onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
    com.tali.notes.ui.components.KeepFullScreen()
    // A deleted page's puff drifts out over the panel's edge freely instead of being cut off by the grid.
    com.tali.notes.ui.components.PuffLayerHost(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss), contentAlignment = Alignment.Center) {
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
              if (picked != null) "Tap pages to pick them"
              else (if (count == 1) "1 page" else "$count pages") + " · tap a page to look closer, hold and drag to move it",
              style = MaterialTheme.typography.bodyMedium,
              color = c.inkMuted,
            )
          }
          androidx.compose.animation.AnimatedContent(
            targetState = picked != null,
            transitionSpec = { (fadeIn(tween(180, delayMillis = 60)) + androidx.compose.animation.slideInVertically(tween(220, easing = com.tali.notes.ui.components.EaseOutStrong)) { it / 3 }).togetherWith(fadeOut(tween(120))) },
            label = "pagesActions",
          ) { picking ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              if (picking) {
                val chosen = picked.orEmpty()
                Text(if (chosen.size == 1) "1 selected" else "${chosen.size} selected", style = MaterialTheme.typography.titleMedium, color = c.ink)
                Spacer(Modifier.width(12.dp))
                QuietButton(if (chosen.size == count) "Select none" else "Select all") { picked = if (chosen.size == count) emptySet() else (0 until count).toSet() }
                Spacer(Modifier.width(10.dp))
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(c.sunken.copy(alpha = 0.7f)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                  com.tali.notes.ui.components.ToolButton(Icons.Outlined.ContentCopy, "Duplicate", enabled = chosen.isNotEmpty()) {
                    onRearrange(com.tali.notes.canvas.Pages.withCopies(count, chosen), if (chosen.size == 1) "Page duplicated" else "${chosen.size} pages duplicated")
                    picked = null
                  }
                  com.tali.notes.ui.components.ToolButton(Icons.Outlined.DeleteOutline, "Delete", enabled = chosen.isNotEmpty()) { deletingPicked = true }
                }
                Spacer(Modifier.width(10.dp))
                PrimaryButton("Done") { picked = null }
              } else {
                if (count > 1) {
                  QuietButton("Select") { picked = emptySet() }
                  Spacer(Modifier.width(8.dp))
                }
                QuietButton("Done", onClick = onDismiss)
              }
            }
          }
        }
        Spacer(Modifier.height(18.dp))
        LazyVerticalGrid(
          // Landscape pages get wider tiles, so both kinds show a page at a readable size.
          columns = GridCells.Adaptive(if (PAGE_ASPECT > 1f) 196.dp else 132.dp),
          state = grid,
          horizontalArrangement = Arrangement.spacedBy(20.dp),
          verticalArrangement = Arrangement.spacedBy(20.dp),
          contentPadding = PaddingValues(bottom = 28.dp, top = 4.dp, start = 4.dp, end = 4.dp),
          modifier = Modifier.weight(1f),
        ) {
          items(order, key = { it }) { i ->
            val lifted = dragging == i
            val pos = order.indexOf(i)
            val chosen = picked
            PageTile(
              vm, items, i, version,
              number = pos + 1,
              selected = pos == current,
              leaving = i == removing || i in vanishing,
              picking = chosen != null,
              picked = chosen?.contains(i) == true,
              lifted = lifted,
              cached = thumbs[i],
              onPicture = { thumbs[i] = it },
              modifier = (if (lifted) Modifier.zIndex(2f).graphicsLayer { translationX = dragOffset.x; translationY = dragOffset.y }
                else Modifier.animateItem(placementSpec = if (settling) null else spring(dampingRatio = 0.85f, stiffness = 500f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold)))
                .pointerInput(i, count, chosen != null) {
                  // While picking, holding doesn't pick a page up to move it.
                  if (chosen != null) return@pointerInput
                  detectDragGesturesAfterLongPress(
                    onDragStart = { dragging = i; dragOffset = androidx.compose.ui.geometry.Offset.Zero },
                    onDrag = { change, delta -> change.consume(); dragBy(delta) },
                    onDragEnd = { drop() },
                    onDragCancel = { drop() },
                  )
                },
            ) {
              if (chosen != null) picked = (if (i in chosen) chosen - i else chosen + i)
              else {
                lastPeek = i
                peek = i
              }
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
        PagePeek(vm, items, lastPeek, version, single = count == 1, onClose = { peek = null }, onGo = { onGo(lastPeek) }) { confirming = lastPeek }
      }
    }
    if (deletingPicked) {
      val gone = picked.orEmpty()
      val all = gone.size == count
      com.tali.notes.ui.library.ConfirmDialog(
        title = if (all) "Clear every page?" else if (gone.size == 1) "Delete 1 page?" else "Delete ${gone.size} pages?",
        body = if (all) "Everything on them is removed and one empty page stays. Undo brings it back." else "They go with everything on them, and the pages after move up. Undo brings them back.",
        confirm = if (all) "Clear pages" else "Delete",
        destructive = true,
        onDismiss = { deletingPicked = false },
      ) {
        deletingPicked = false
        picked = null
        // The pages lift away in a puff together, then go.
        vanishing = gone
        pendingOrder = com.tali.notes.canvas.Pages.withoutPages(count, gone) to (if (all) "Pages cleared \u2014 undo brings them back" else if (gone.size == 1) "Page deleted \u2014 undo brings it back" else "${gone.size} pages deleted \u2014 undo brings them back")
      }
    }
    confirming?.let { i ->
      val single = count == 1
      com.tali.notes.ui.library.ConfirmDialog(
        title = if (single) "Clear this page?" else "Delete page ${i + 1}?",
        body = if (single) "Everything on it is removed. Undo brings it back." else "The page and everything on it go, and the pages after it move up. Undo brings it back.",
        confirm = if (single) "Clear page" else "Delete page",
        destructive = true,
        onDismiss = { confirming = null },
      ) {
        // The peek closes, then the page's tile lifts away before it's removed.
        confirming = null
        peek = null
        removing = i
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
private fun PageTile(
  vm: NotebookViewModel,
  items: List<CanvasItem>,
  index: Int,
  version: Int,
  number: Int,
  selected: Boolean,
  leaving: Boolean,
  picking: Boolean = false,
  picked: Boolean = false,
  lifted: Boolean,
  cached: ImageBitmap?,
  onPicture: (ImageBitmap) -> Unit,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val c = Tali.colors
  val fresh = rememberPage(vm, items, index, version, 480)
  LaunchedEffect(fresh) { fresh?.let(onPicture) }
  val image = fresh ?: cached
  // Picked up: it lifts a little (bigger shadow, a touch larger) while it's carried.
  val lift by androidx.compose.animation.core.animateFloatAsState(if (lifted) 1f else 0f, tween(160), label = "pageLift")
  val shape = RoundedCornerShape(10.dp)
  // A deleted page dissolves into a small puff of cloud.
  com.tali.notes.ui.components.PuffBox(leaving = leaving, appearing = false, modifier = modifier) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Box(
      Modifier.fillMaxWidth()
        .aspectRatio(PAGE_ASPECT)
        .graphicsLayer { scaleX = 1f + 0.05f * lift; scaleY = 1f + 0.05f * lift }
        .shadow((if (selected) 10.dp else 5.dp) + 14.dp * lift, shape, ambientColor = Color(0x224F7BE8), spotColor = Color(0x337A4ED6))
        .clip(shape)
        .background(Color.White)
        .border(if (selected || picked) 2.5.dp else 1.dp, if (selected || picked) c.accent else c.line, shape)
        .clickable(onClickLabel = if (picking) "Pick page $number" else "Look at page $number", onClick = onClick)
    ) {
      if (image != null) Image(image, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
      // While picking, a check circle in the corner: filled when picked.
      androidx.compose.animation.AnimatedVisibility(
        picking,
        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        enter = fadeIn(tween(160)) + scaleIn(tween(200, easing = com.tali.notes.ui.components.EaseOutStrong), initialScale = 0.7f),
        exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.85f),
      ) {
        val fill by androidx.compose.animation.animateColorAsState(if (picked) c.accent else Color.White.copy(alpha = 0.9f), tween(150), label = "pagePick")
        Box(
          Modifier.size(26.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(fill).border(1.5.dp, if (picked) c.accent else c.inkFaint, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          if (picked) Icon(Icons.Outlined.Check, "Picked", tint = Color.White, modifier = Modifier.size(16.dp))
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      "$number",
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
