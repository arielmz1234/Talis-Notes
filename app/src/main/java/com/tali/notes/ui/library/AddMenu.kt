package com.tali.notes.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.tali.notes.ui.camera.CameraMode
import com.tali.notes.ui.camera.CameraScreen
import com.tali.notes.R
import com.tali.notes.data.DocKind
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.EaseOutStrong
import com.tali.notes.ui.components.cloudSurface
import kotlinx.coroutines.delay

/**
 * The library's +: a round button that opens a small card of what she can add. A notebook (the
 * new-notebook card), a file (PDFs and pictures from Files or Drive), a scan or a photo (our own
 * camera screen, [CameraScreen]: pages found, cropped and straightened, like CamScanner), or photos
 * from the gallery. The + turns into × while the card is open; the card grows out of the button's corner and
 * its rows drift in one after another.
 */
@Composable
fun AddButton(
  onNotebook: () -> Unit,
  onFiles: (List<Uri>) -> Unit,
  onPhotos: (List<Uri>) -> Unit,
  onScan: (List<Uri>) -> Unit,
) {
  val c = Tali.colors
  var open by remember { mutableStateOf(false) }
  val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) onFiles(it) }
  val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS)) { if (it.isNotEmpty()) onPhotos(it) }
  // Our own camera screen, for photos and scans alike.
  var camera by remember { mutableStateOf<CameraMode?>(null) }
  camera?.let { mode ->
    CameraScreen(
      start = mode,
      onClose = { camera = null },
      onSave = { taken, uris ->
        camera = null
        if (uris.isNotEmpty()) if (taken == CameraMode.SCAN) onScan(uris) else onPhotos(uris)
      },
    )
  }

  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val turn by animateFloatAsState(if (open) 45f else 0f, tween(240, easing = EaseOutStrong), label = "plus")
  val press by animateFloatAsState(if (pressed) 0.94f else 1f, tween(if (pressed) 90 else 160), label = "addPress")
  Box {
    Box(
      Modifier.scale(press)
        .size(48.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(c.accentBrush)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.add)) { open = !open },
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.Add, stringResource(R.string.add), tint = c.onAccent, modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = turn })
    }
    AddCard(
      open = open,
      onDismiss = { open = false },
      onPick = { choice ->
        open = false
        when (choice) {
          Choice.NOTEBOOK -> onNotebook()
          Choice.FILE -> files.launch(arrayOf("application/pdf", "image/*"))
          Choice.SCAN -> camera = CameraMode.SCAN
          Choice.CAMERA -> camera = CameraMode.PHOTO
          Choice.PHOTO -> photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
      },
    )
  }
}

private enum class Choice { NOTEBOOK, FILE, SCAN, CAMERA, PHOTO }

@Composable
private fun AddCard(open: Boolean, onDismiss: () -> Unit, onPick: (Choice) -> Unit) {
  val c = Tali.colors
  val visible = remember { MutableTransitionState(false) }
  visible.targetState = open
  if (!visible.currentState && !visible.targetState) return
  val density = LocalDensity.current
  val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
  val gap = with(density) { 8.dp.roundToPx() }
  // Under the button, lined up with its outer edge (its right in English, its left in Hebrew).
  val below =
    remember(ltr, gap) {
      object : PopupPositionProvider {
        override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
          val x = if (ltr) anchorBounds.right - popupContentSize.width else anchorBounds.left
          val y = anchorBounds.bottom + gap
          return IntOffset(x.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width)), y.coerceAtMost(maxOf(0, windowSize.height - popupContentSize.height)))
        }
      }
    }
  val origin = TransformOrigin(if (ltr) 1f else 0f, 0f)
  Popup(popupPositionProvider = below, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
    AnimatedVisibility(
      visibleState = visible,
      enter = fadeIn(tween(150)) + scaleIn(tween(240, easing = EaseOutStrong), initialScale = 0.9f, transformOrigin = origin),
      exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.95f, transformOrigin = origin),
    ) {
      Column(Modifier.width(330.dp).cloudSurface(22.dp).padding(8.dp)) {
        val rows =
          listOf(
            Row4(Choice.NOTEBOOK, Icons.Outlined.AutoStories, c.accent, stringResource(R.string.notebook), stringResource(R.string.blank_pages_to_write_on)),
            Row4(Choice.FILE, Icons.Outlined.UploadFile, kindTint(DocKind.PDF), stringResource(R.string.add_file), stringResource(R.string.a_pdf_or_pictures_from_your_files)),
            Row4(Choice.SCAN, Icons.Outlined.DocumentScanner, kindTint(DocKind.SCAN), stringResource(R.string.add_scan), stringResource(R.string.paper_pages_with_the_camera)),
            Row4(Choice.CAMERA, Icons.Outlined.PhotoCamera, kindTint(DocKind.PICTURE), stringResource(R.string.add_camera), stringResource(R.string.a_photo_of_the_board_or_a_page)),
            Row4(Choice.PHOTO, Icons.Outlined.Image, kindTint(DocKind.PICTURE), stringResource(R.string.add_photo), stringResource(R.string.from_your_gallery)),
          )
        rows.forEachIndexed { i, r -> AddRow(i, r) { onPick(r.choice) } }
      }
    }
  }
}

private class Row4(val choice: Choice, val icon: ImageVector, val tint: Color, val title: String, val detail: String)

@Composable
private fun AddRow(index: Int, row: Row4, onClick: () -> Unit) {
  val c = Tali.colors
  // Each row drifts up into place a moment after the one above it.
  val shown = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    delay(40L + index * 35L)
    shown.animateTo(1f, tween(260, easing = EaseOutStrong))
  }
  Row(
    Modifier.fillMaxWidth()
      .graphicsLayer {
        alpha = shown.value
        translationY = (1f - shown.value) * 10.dp.toPx()
      }
      .clip(RoundedCornerShape(16.dp))
      .clickable(role = Role.Button, onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(row.tint.copy(alpha = if (c.isDark) 0.22f else 0.12f)), contentAlignment = Alignment.Center) {
      Icon(row.icon, null, tint = row.tint, modifier = Modifier.size(23.dp))
    }
    Spacer(Modifier.width(14.dp))
    Column(Modifier.weight(1f)) {
      Text(row.title, style = MaterialTheme.typography.titleSmall, color = c.ink)
      Text(row.detail, style = MaterialTheme.typography.bodySmall, color = c.inkMuted)
    }
  }
}

/** Each kind's own colour, on its badge in the library and its row in the + card. */
@Composable
fun kindTint(kind: DocKind): Color {
  val dark = Tali.colors.isDark
  return when (kind) {
    DocKind.NOTEBOOK -> Tali.colors.accent
    DocKind.PDF -> if (dark) Color(0xFFFF8E8A) else Color(0xFFD9443F)
    DocKind.PICTURE -> if (dark) Color(0xFFF2A0C4) else Color(0xFFC93B7A)
    DocKind.SCAN -> if (dark) Color(0xFF6FD3B5) else Color(0xFF1C8466)
  }
}

fun kindIcon(kind: DocKind): ImageVector =
  when (kind) {
    DocKind.NOTEBOOK -> Icons.Outlined.AutoStories
    DocKind.PDF -> Icons.Outlined.PictureAsPdf
    DocKind.PICTURE -> Icons.Outlined.Image
    DocKind.SCAN -> Icons.Outlined.DocumentScanner
  }

@Composable
fun kindLabel(kind: DocKind): String =
  when (kind) {
    DocKind.NOTEBOOK -> stringResource(R.string.notebook)
    DocKind.PDF -> "PDF"
    DocKind.PICTURE -> stringResource(R.string.picture)
    DocKind.SCAN -> stringResource(R.string.add_scan)
  }

private const val MAX_PHOTOS = 30
