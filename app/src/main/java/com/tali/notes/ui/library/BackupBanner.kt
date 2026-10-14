package com.tali.notes.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudUpload
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tali.notes.CrashLog
import com.tali.notes.R
import com.tali.notes.backup.DriveBackup
import com.tali.notes.later
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.WorkingText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Above the notebooks while nothing is backed up: one tap goes straight to Google's account
 * chooser and turns on Google Drive backup, which then keeps backing up by itself. The × puts it
 * away for good ([onClose]); Backups still has every way to back up. Once Drive is on it says so
 * for a moment and goes ([onFinished]).
 */
@Composable
internal fun BackupBanner(onClose: () -> Unit, onFinished: () -> Unit, modifier: Modifier = Modifier) {
  val c = Tali.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf<String?>(null) }
  var result by remember { mutableStateOf<String?>(null) }

  fun run(label: String, block: suspend () -> String) {
    if (busy != null) return
    busy = label
    scope.launch {
      result =
        try {
          block()
        } catch (t: Throwable) {
          CrashLog.recovered(context, t)
          later(R.string.something_went_wrong_n, t.message ?: t.javaClass.simpleName)
        }
      busy = null
    }
  }

  val drive = rememberDriveAuth(::run)
  val connect = {
    drive(later(R.string.backing_up_to_drive)) { token ->
      DriveBackup.enable(context)
      DriveBackup.backupNow(context, token)
      later(R.string.backed_up_to_google_drive)
    }
  }
  val status by DriveBackup.status(context).collectAsState()
  // Drive is on (from here, or from Backups): show how it went for a moment, then step aside.
  LaunchedEffect(status.enabled, busy) {
    if (status.enabled && busy == null) {
      if (result != null) delay(2500)
      onFinished()
    }
  }

  val phone = LocalConfiguration.current.screenWidthDp < PHONE_DP
  Row(
    modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(Brush.horizontalGradient(listOf(c.accentSoft, c.blueSoft)))
      .border(1.dp, c.lilac, RoundedCornerShape(16.dp))
      .clickable(onClickLabel = stringResource(R.string.connect_drive)) { if (busy == null) connect() }
      .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(36.dp).clip(CircleShape).background(c.surface.copy(alpha = 0.8f)), contentAlignment = Alignment.Center) {
      Icon(Icons.Outlined.CloudUpload, null, tint = c.accent, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(stringResource(R.string.your_notebooks_aren_t_backed_up), style = MaterialTheme.typography.titleSmall, color = c.ink)
      WorkingText(
        busy ?: result ?: stringResource(R.string.connect_your_google_drive_to_back),
        style = MaterialTheme.typography.bodySmall,
        color = if (busy != null || result != null) c.blue else c.inkMuted,
      )
    }
    // On a phone the whole banner is the button; a tablet has room for one too.
    if (!phone) {
      Spacer(Modifier.width(10.dp))
      PrimaryButton(stringResource(R.string.connect_drive)) { if (busy == null) connect() }
    }
    Spacer(Modifier.width(2.dp))
    Box(
      Modifier.size(40.dp).clip(CircleShape).clickable(onClickLabel = stringResource(R.string.close), onClick = onClose),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.Close, stringResource(R.string.close), tint = c.inkMuted, modifier = Modifier.size(20.dp))
    }
  }
}
