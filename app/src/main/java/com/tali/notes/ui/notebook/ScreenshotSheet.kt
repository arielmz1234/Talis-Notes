package com.tali.notes.ui.notebook

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.QuietButton
import com.tali.notes.ui.library.DialogCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A screenshot taken with the screenshot lasso: a preview, then save it to the gallery (Pictures ›
 * Tali's Notes), share it to any app, or copy it to paste on the page (hold to paste) or in another app.
 */
@Composable
fun ScreenshotSheet(file: File, onDone: () -> Unit, onHint: (String) -> Unit, onCopy: () -> Unit = {}) {
  val c = Tali.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val image by produceState<ImageBitmap?>(null, file) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() } }
  var saved by remember(file) { mutableStateOf(false) }
  val maxPreview = (LocalConfiguration.current.screenHeightDp * 0.5f).dp
  DialogCard(onDone, width = 560) {
    // Close sits in the corner, so the three things to do with it are centred under the picture.
    Box(Modifier.fillMaxWidth()) {
      Text("Screenshot", style = MaterialTheme.typography.titleLarge, color = c.ink, modifier = Modifier.align(Alignment.CenterStart))
      IconButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)) {
        Icon(Icons.Outlined.Close, "Close", tint = c.inkMuted)
      }
    }
    Spacer(Modifier.height(10.dp))
    Box(
      Modifier.fillMaxWidth().heightIn(min = 120.dp, max = maxPreview).clip(RoundedCornerShape(12.dp)).background(c.sunken).border(1.dp, c.line, RoundedCornerShape(12.dp)),
      contentAlignment = Alignment.Center,
    ) {
      image?.let { Image(it, "The screenshot", contentScale = ContentScale.Fit, modifier = Modifier.padding(6.dp)) }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
      QuietButton("Copy") {
        // Onto the page (hold anywhere to paste it) and into other apps alike.
        onCopy()
        copyImage(context, file)
        onHint("Copied — hold on the page to paste it, or paste it in another app")
        onDone()
      }
      QuietButton(if (saved) "Saved" else "Save to gallery") {
        if (saved) return@QuietButton
        scope.launch {
          val ok = withContext(Dispatchers.IO) { runCatching { saveToGallery(context, file) }.getOrDefault(false) }
          saved = ok
          onHint(if (ok) "Saved to your gallery (Pictures › Tali's Notes)" else "Couldn't save the picture")
        }
      }
      PrimaryButton("Share", Icons.Outlined.Share) { shareImage(context, file) }
    }
  }
}

private fun uriOf(context: Context, file: File) = FileProvider.getUriForFile(context, context.packageName + ".files", file)

private fun shareImage(context: Context, file: File) {
  val uri = uriOf(context, file)
  val send = Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newRawUri("", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  context.startActivity(Intent.createChooser(send, "Share screenshot"))
}

private fun copyImage(context: Context, file: File): Boolean =
  runCatching {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newUri(context.contentResolver, "Screenshot", uriOf(context, file)))
    true
  }.getOrDefault(false)

/** Saves the picture into Pictures/Tali's Notes, where the gallery finds it (no permission needed from Android 10). */
private fun saveToGallery(context: Context, file: File): Boolean {
  val values = ContentValues().apply {
    put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Tali's Notes")
    put(MediaStore.Images.Media.IS_PENDING, 1)
  }
  val resolver = context.contentResolver
  val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
  resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: return false
  values.clear()
  values.put(MediaStore.Images.Media.IS_PENDING, 0)
  resolver.update(uri, values, null, null)
  return true
}
