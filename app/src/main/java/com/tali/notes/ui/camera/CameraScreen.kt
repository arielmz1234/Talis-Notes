package com.tali.notes.ui.camera

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaActionSound
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tali.notes.R
import com.tali.notes.camera.Look
import com.tali.notes.camera.P
import com.tali.notes.camera.PageImage
import com.tali.notes.camera.Quad
import com.tali.notes.theme.Tali
import com.tali.notes.theme.TaliColorsScope
import com.tali.notes.ui.components.EaseOutStrong
import com.tali.notes.ui.components.PrimaryButton
import com.tali.notes.ui.components.Segmented
import com.tali.notes.ui.library.ConfirmDialog
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Our own camera, for + › Camera and a notebook's Take a photo, in the app's colours: the × top
 * left, a big violet shutter, tap to focus, pinch to zoom. A photo goes to the look step (Original,
 * Document, Grey, B&W), where it can be cropped (corners with a magnifier, straightened) and turned.
 * Every picture is turned by what the camera reports for the way the tablet is held, and a half turn
 * she gives one is remembered. Several photos can be taken before saving; [onSave] gets them as files
 * in the cache, upright and finished.
 */
@Composable
fun CameraScreen(onClose: () -> Unit, onSave: (List<Uri>) -> Unit) {
  CameraWindow { CameraBody(onClose, onSave) }
}

/**
 * The camera's window: the whole screen, no system bars, the app's dark colours, and laid out the
 * same in every language (like a notebook): in Hebrew or Arabic only the words change.
 */
@Composable
internal fun CameraWindow(content: @Composable () -> Unit) {
  Dialog(
    onDismissRequest = {},
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false, dismissOnBackPress = false),
  ) {
    // A camera has the whole screen: no status or navigation bar (a swipe shows them for a moment).
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
      if (window != null) {
        window.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        WindowCompat.getInsetsController(window, view).apply {
          systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          hide(WindowInsetsCompat.Type.systemBars())
        }
      }
      onDispose {}
    }
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
      // (Its words still read right to left in Hebrew and Arabic: TaliColorsScope sees the forced layout.)
      TaliColorsScope(com.tali.notes.theme.DarkColors) { content() }
    }
  }
}

/** [held] is the screen's rotation when it was taken; [turns] how many quarter turns she gave it. */
private data class Shot(val picture: Bitmap, val view: ImageBitmap, val quad: Quad, val look: Look, val held: Int? = null, val turns: Int = 0)

private class Taken(val file: File, val thumb: ImageBitmap)

private enum class Step { CROP, LOOK }

@Composable
private fun CameraBody(onClose: () -> Unit, onSave: (List<Uri>) -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val pages = remember { mutableStateListOf<Taken>() }
  var shot by remember { mutableStateOf<Shot?>(null) }
  var step by remember { mutableStateOf(Step.CROP) }
  var working by remember { mutableStateOf(false) }
  var discarding by remember { mutableStateOf(false) }
  // Opens with a quick rise; closes with a quicker fade.
  val shown = remember { Animatable(0f) }
  var closing by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    withContext(Dispatchers.IO) { clearOld(context) }
    shown.animateTo(1f, tween(240, easing = EaseOutStrong))
  }
  fun close(then: () -> Unit = onClose) {
    if (closing) return
    closing = true
    scope.launch {
      shown.animateTo(0f, tween(150))
      then()
    }
  }
  fun finish(extra: Taken?) {
    val all = pages.toList() + listOfNotNull(extra)
    close { onSave(all.map { Uri.fromFile(it.file) }) }
  }
  fun keep(s: Shot, then: (Taken) -> Unit) {
    if (working) return
    working = true
    scope.launch {
      val t = withContext(Dispatchers.Default) { runCatching { finished(context, s) }.getOrNull() }
      s.held?.let { if (s.turns % 4 == 2) learnTurn(context, it) }
      working = false
      if (t != null) then(t)
    }
  }
  fun back() {
    val s = shot
    when {
      s != null && step == Step.CROP -> step = Step.LOOK
      s != null -> shot = null
      pages.isNotEmpty() -> discarding = true
      else -> close()
    }
  }
  androidx.activity.compose.BackHandler(onBack = ::back)

  Box(
    Modifier.fillMaxSize()
      .graphicsLayer {
        alpha = shown.value
        val s = 0.97f + 0.03f * shown.value
        scaleX = s
        scaleY = s
      }
      .background(NIGHT)
  ) {
    CameraPart(
      pages = pages,
      onClose = ::back,
      onTaken = { s ->
        shot = s
        step = Step.LOOK
      },
      onSave = { finish(null) },
    )
    val s = shot
    AnimatedVisibility(s != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(140))) {
      // Keeps showing the last picture while it fades out.
      val last = remember { mutableStateOf<Shot?>(null) }
      if (s != null) last.value = s
      last.value?.let { current ->
        EditPart(
          shot = current,
          step = step,
          pages = pages.size,
          working = working,
          onChange = { shot = it },
          onStep = { step = it },
          onRetake = { shot = null },
          onAnother = { keep(current) { pages += it; shot = null } },
          onSave = { keep(current) { finish(it) } },
        )
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
        close()
      },
    )
    }
  }
}

// ——— The camera ———

@Composable
private fun CameraPart(
  pages: List<Taken>,
  onClose: () -> Unit,
  onTaken: (Shot) -> Unit,
  onSave: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycle = LocalLifecycleOwner.current
  val scope = rememberCoroutineScope()
  var granted by remember { mutableStateOf(hasCamera(context)) }
  var asked by remember { mutableStateOf(false) }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
    granted = it
    asked = true
  }
  LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
  // Back from the tablet's settings with the camera allowed.
  LifecycleResumeEffect(Unit) {
    granted = hasCamera(context)
    onPauseOrDispose {}
  }
  val preview = remember { PreviewView(context).apply { setBackgroundColor(android.graphics.Color.rgb(0x0E, 0x0A, 0x18)); implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FIT_CENTER } }
  val worker = remember { Executors.newSingleThreadExecutor() }
  DisposableEffect(Unit) { onDispose { worker.shutdown() } }
  val fourThree = remember { AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY }
  val capture =
    remember {
      ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(fourThree).setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build())
        .build()
    }
  var camera by remember { mutableStateOf<Camera?>(null) }
  var failed by remember { mutableStateOf(false) }
  LaunchedEffect(granted) {
    if (!granted) return@LaunchedEffect
    runCatching {
        val provider = withContext(Dispatchers.IO) { ProcessCameraProvider.getInstance(context).get() }
        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        val p = Preview.Builder().setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(fourThree).build()).build()
        p.surfaceProvider = preview.surfaceProvider
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycle, selector, p, capture)
      }
      .onFailure { failed = true }
  }
  DisposableEffect(Unit) { onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } } }

  var taking by remember { mutableStateOf(false) }
  val blink = remember { Animatable(0f) }
  val sound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
  DisposableEffect(Unit) { onDispose { sound.release() } }
  var flash by remember { mutableStateOf(ImageCapture.FLASH_MODE_OFF) }
  fun take() {
    if (taking || camera == null) return
    taking = true
    runCatching { sound.play(MediaActionSound.SHUTTER_CLICK) }
    scope.launch {
      blink.snapTo(0.7f)
      blink.animateTo(0f, tween(260))
    }
    capture.flashMode = flash
    // Turned for the way the screen is now, so the picture comes out the way she sees it.
    val held = preview.display?.rotation ?: Surface.ROTATION_0
    capture.targetRotation = held
    val learned = learnedTurn(context, held)
    capture.takePicture(
      worker,
      object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
          val rotation = image.imageInfo.rotationDegrees
          val raw = runCatching { image.toBitmap() }.onFailure { android.util.Log.w("Camera", "decode", it) }.getOrNull()
          image.close()
          val s = raw?.let { runCatching { prepare(PageImage.turned(PageImage.scaledTo(it, MAX_SIDE), rotation / 90 + learned)).copy(held = held) }.onFailure { android.util.Log.w("Camera", "prepare", it) }.getOrNull() }
          scope.launch {
            taking = false
            if (s != null) onTaken(s)
          }
        }

        override fun onError(exception: ImageCaptureException) {
          android.util.Log.w("Camera", "capture", exception)
          scope.launch { taking = false }
        }
      },
    )
  }

  BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth > maxHeight
    var size by remember { mutableStateOf(IntSize.Zero) }
    var focus by remember { mutableStateOf<Offset?>(null) }
    val focusRing = remember { Animatable(0f) }
    Box(
      Modifier.fillMaxSize()
        .onSizeChanged { size = it }
        .pointerInput(camera) {
          detectTapGestures { at ->
            val cam = camera ?: return@detectTapGestures
            val point = preview.meteringPointFactory.createPoint(at.x, at.y)
            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
            focus = at
            scope.launch {
              focusRing.snapTo(0f)
              focusRing.animateTo(1f, tween(900))
            }
          }
        }
        .pointerInput(camera) {
          detectTransformGestures { _, _, zoom, _ ->
            val cam = camera ?: return@detectTransformGestures
            val state = cam.cameraInfo.zoomState.value ?: return@detectTransformGestures
            cam.cameraControl.setZoomRatio((state.zoomRatio * zoom).coerceIn(state.minZoomRatio, state.maxZoomRatio))
          }
        }
    ) {
      if (granted) AndroidView({ preview }, Modifier.fillMaxSize())
      // The focus ring and the shutter's flash.
      Canvas(Modifier.fillMaxSize()) {
        focus?.let { f ->
          val t = focusRing.value
          if (t < 1f) {
            val r = 34.dp.toPx() * (1.25f - 0.25f * EaseOutStrong.transform(minOf(1f, t * 3f)))
            drawCircle(Color.White.copy(alpha = (1f - t) * 0.9f), r, f, style = Stroke(1.5.dp.toPx()))
          }
        }
        if (blink.value > 0f) drawRect(Color.White.copy(alpha = blink.value))
      }
    }

    if (!granted || failed) NoCamera(asked = asked, failed = failed, onAllow = {
      val activity = context as? android.app.Activity
      if (asked && activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
      } else ask.launch(Manifest.permission.CAMERA)
    })

    val edge = Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)
    // × in the top corner, as everywhere else in the app.
    Box(edge.align(Alignment.TopStart)) { RoundButton(Icons.Outlined.Close, stringResource(R.string.close), onClick = onClose) }
    if (camera?.cameraInfo?.hasFlashUnit() == true) {
      Box(edge.align(Alignment.TopEnd)) {
        RoundButton(
          when (flash) {
            ImageCapture.FLASH_MODE_ON -> Icons.Outlined.FlashOn
            ImageCapture.FLASH_MODE_AUTO -> Icons.Outlined.FlashAuto
            else -> Icons.Outlined.FlashOff
          },
          stringResource(R.string.cam_flash),
        ) {
          flash =
            when (flash) {
              ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
              ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
              else -> ImageCapture.FLASH_MODE_OFF
            }
        }
      }
    }
    val saveSlot: @Composable () -> Unit = {
      AnimatedVisibility(pages.isNotEmpty(), enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
        pages.lastOrNull()?.let { SavePill(it.thumb, pages.size, onSave) }
      }
    }
    if (wide) {
      Column(
        edge.align(Alignment.CenterEnd).fillMaxHeight().width(120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
      ) {
        Spacer(Modifier.height(52.dp))
        Spacer(Modifier.size(52.dp))
        Shutter(taking, enabled = granted && camera != null, onClick = ::take)
        Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) { saveSlot() }
        Spacer(Modifier.height(1.dp))
      }
    } else {
      Row(
        edge.align(Alignment.BottomCenter).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Spacer(Modifier.weight(1f))
        Shutter(taking, enabled = granted && camera != null, onClick = ::take)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) { saveSlot() }
      }
    }
  }
}

@Composable
private fun NoCamera(asked: Boolean, failed: Boolean, onAllow: () -> Unit) {
  val c = Tali.colors
  Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Column(Modifier.widthIn(max = 420.dp).quietSurface().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Outlined.NoPhotography, null, tint = c.accent, modifier = Modifier.size(36.dp))
      Spacer(Modifier.height(12.dp))
      Text(stringResource(if (failed) R.string.cam_unavailable else R.string.cam_allow_title), style = MaterialTheme.typography.titleMedium, color = c.ink, textAlign = TextAlign.Center)
      if (!failed) {
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.cam_allow_body), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted, textAlign = TextAlign.Center)
        if (asked) {
          Spacer(Modifier.height(18.dp))
          PrimaryButton(stringResource(R.string.cam_allow), onClick = onAllow)
        }
      }
    }
  }
}

// ——— Corners and look ———

@Composable
private fun EditPart(
  shot: Shot,
  step: Step,
  pages: Int,
  working: Boolean,
  onChange: (Shot) -> Unit,
  onStep: (Step) -> Unit,
  onRetake: () -> Unit,
  onAnother: () -> Unit,
  onSave: () -> Unit,
) {
  val scope = rememberCoroutineScope()
  var turning by remember { mutableStateOf(false) }
  fun turn() {
    if (turning) return
    turning = true
    scope.launch {
      val t = withContext(Dispatchers.Default) { PageImage.turned(shot.picture, 1) }
      onChange(shot.copy(picture = t, view = PageImage.scaledTo(t, VIEW_SIDE).asImageBitmap(), quad = shot.quad.turned(1), turns = shot.turns + 1))
      turning = false
    }
  }
  // The straightened page at screen size, then each look of it (and small ones for the choices).
  val flat by produceState<Bitmap?>(null, shot.picture, shot.quad) { value = withContext(Dispatchers.Default) { PageImage.straightened(shot.picture, shot.quad, VIEW_SIDE) } }
  val result by produceState<ImageBitmap?>(null, flat, shot.look) { value = flat?.let { f -> withContext(Dispatchers.Default) { PageImage.looked(f, shot.look).asImageBitmap() } } }
  val samples by produceState<Map<Look, ImageBitmap>>(emptyMap(), flat) {
    val f = flat ?: return@produceState
    value = withContext(Dispatchers.Default) { val small = PageImage.scaledTo(f, 200); Look.entries.associateWith { PageImage.looked(small, it).asImageBitmap() } }
  }

  BoxWithConstraints(Modifier.fillMaxSize().background(NIGHT).windowInsetsPadding(WindowInsets.safeDrawing)) {
    val wide = maxWidth > maxHeight
    val panel: @Composable (Modifier) -> Unit = { m ->
      EditPanel(
        modifier = m,
        wide = wide,
        step = step,
        pages = pages,
        working = working || turning,
        look = shot.look,
        samples = samples,
        whole = shot.quad == Quad.whole(),
        convex = shot.quad.convex,
        onLook = { onChange(shot.copy(look = it)) },
        onTurn = ::turn,
        onWhole = {
          val q = if (shot.quad == Quad.whole()) Quad.whole(0.06f) else Quad.whole()
          onChange(shot.copy(quad = q))
        },
        onStep = onStep,
        onRetake = onRetake,
        onAnother = onAnother,
        onSave = onSave,
      )
    }
    val picture: @Composable (Modifier) -> Unit = { m ->
      Box(m.padding(20.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(step, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) }, label = "editStep") { s ->
          if (s == Step.CROP) Corners(shot.view, shot.quad) { onChange(shot.copy(quad = it)) }
          else
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
              AnimatedContent(result, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(160)) }, label = "look") { r ->
                if (r != null) Image(r, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
              }
            }
        }
      }
    }
    if (wide)
      Row(Modifier.fillMaxSize()) {
        picture(Modifier.weight(1f).fillMaxHeight())
        Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) { panel(Modifier.padding(16.dp).width(300.dp)) }
      }
    else
      Column(Modifier.fillMaxSize()) {
        picture(Modifier.weight(1f).fillMaxWidth())
        panel(Modifier.padding(16.dp).fillMaxWidth())
      }
  }
}

@Composable
private fun EditPanel(
  modifier: Modifier,
  wide: Boolean,
  step: Step,
  pages: Int,
  working: Boolean,
  look: Look,
  samples: Map<Look, ImageBitmap>,
  whole: Boolean,
  convex: Boolean,
  onLook: (Look) -> Unit,
  onTurn: () -> Unit,
  onWhole: () -> Unit,
  onStep: (Step) -> Unit,
  onRetake: () -> Unit,
  onAnother: () -> Unit,
  onSave: () -> Unit,
) {
  val c = Tali.colors
  Column(modifier.quietSurface().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Text(stringResource(if (step == Step.CROP) R.string.cam_crop else R.string.cam_look), style = MaterialTheme.typography.titleLarge, color = c.ink)
    if (step == Step.CROP) {
      Text(stringResource(R.string.cam_corners), style = MaterialTheme.typography.bodyMedium, color = c.inkMuted)
    } else {
      LookChoices(look, samples, columns = if (wide) 2 else 4, onLook)
    }
    if (wide) Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      SmallTool(Icons.Outlined.Replay, stringResource(R.string.cam_retake), Modifier.weight(1f), onRetake)
      SmallTool(Icons.Outlined.RotateRight, stringResource(R.string.cam_turn), Modifier.weight(1f), onTurn)
      if (step == Step.CROP) SmallTool(if (whole) Icons.Outlined.Crop else Icons.Outlined.CropFree, stringResource(if (whole) R.string.cam_crop else R.string.cam_whole), Modifier.weight(1f), onWhole)
      else SmallTool(Icons.Outlined.Crop, stringResource(R.string.cam_crop), Modifier.weight(1f)) { onStep(Step.CROP) }
    }
    val primary: @Composable (Modifier) -> Unit = { m ->
      if (step == Step.CROP) PrimaryButton(stringResource(R.string.tour_next), Icons.AutoMirrored.Outlined.ArrowForward, m.alpha(if (convex) 1f else 0.4f)) { if (convex) onStep(Step.LOOK) }
      else Unit
    }
    if (step == Step.LOOK) {
      // Side by side and compact: another page or photo, or save.
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactButton(stringResource(R.string.cam_another), Icons.Outlined.Add, primary = false, Modifier.weight(1f)) { if (!working) onAnother() }
        CompactButton(
          if (pages > 0) stringResource(R.string.cam_save_n, pages + 1) else stringResource(R.string.save),
          Icons.Outlined.Check,
          primary = true,
          Modifier.weight(1f).alpha(if (working) 0.6f else 1f),
        ) { if (!working) onSave() }
      }
    } else primary(Modifier.fillMaxWidth())
  }
}

/** The screen's one surface: a faint lilac glass with a hairline edge, no cloud bumps (kept minimal). */
private fun Modifier.quietSurface() = this.clip(RoundedCornerShape(24.dp)).background(Color(0xFF1A1428)).border(1.dp, Color(0xFFB79CFF).copy(alpha = 0.16f), RoundedCornerShape(24.dp))

/** A smaller button for the two side by side under the looks. */
@Composable
internal fun CompactButton(text: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val c = Tali.colors
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed) 0.96f else 1f, tween(if (pressed) 80 else 160), label = "compact")
  Row(
    modifier
      .graphicsLayer {
        scaleX = press
        scaleY = press
      }
      .height(40.dp)
      .clip(RoundedCornerShape(12.dp))
      .then(if (primary) Modifier.background(c.accentBrush) else Modifier.background(c.accentSoft))
      .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
      .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    val ink = if (primary) c.onAccent else c.accent
    Icon(icon, null, tint = ink, modifier = Modifier.size(17.dp))
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelMedium, color = ink, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
  }
}

private fun Modifier.alpha(a: Float) = this.graphicsLayer { alpha = a }

@Composable
private fun LookChoices(look: Look, samples: Map<Look, ImageBitmap>, columns: Int, onLook: (Look) -> Unit) {
  val c = Tali.colors
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Look.entries.chunked(columns).forEach { row ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        row.forEach { l ->
          val on = l == look
          val ring by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "lookRing")
          Column(
            Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(role = Role.RadioButton) { onLook(l) }.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Box(
              Modifier.fillMaxWidth().height(if (columns == 2) 84.dp else 64.dp).clip(RoundedCornerShape(12.dp)).background(c.sunken)
                .border(2.dp, c.accent.copy(alpha = ring), RoundedCornerShape(12.dp)).padding(4.dp),
              contentAlignment = Alignment.Center,
            ) {
              samples[l]?.let { Image(it, null, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Fit) }
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(lookName(l)), style = MaterialTheme.typography.labelMedium, color = if (on) c.ink else c.inkMuted, maxLines = 1)
          }
        }
      }
    }
  }
}

private fun lookName(l: Look) =
  when (l) {
    Look.ORIGINAL -> R.string.look_original
    Look.DOCUMENT -> R.string.look_document
    Look.GREY -> R.string.look_grey
    Look.BLACK_WHITE -> R.string.look_bw
  }

/** The picture with its four corners to drag; a magnifier shows what's under the finger. */
@Composable
private fun Corners(picture: ImageBitmap, quad: Quad, onQuad: (Quad) -> Unit) {
  val accent = Tali.colors.accent
  val current by rememberUpdatedState(quad)
  var held by remember { mutableStateOf(-1) }
  var finger by remember { mutableStateOf(Offset.Zero) }
  var area by remember { mutableStateOf(Rect.Zero) }
  Canvas(
    Modifier.fillMaxSize().pointerInput(Unit) {
      val reach = 48.dp.toPx()
      detectDragGestures(
        onDragStart = { at ->
          val r = area
          val ps = current.points.map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
          val i = ps.indices.minBy { (ps[it] - at).getDistance() }
          held = if ((ps[i] - at).getDistance() < reach) i else -1
          finger = at
        },
        onDragEnd = { held = -1 },
        onDragCancel = { held = -1 },
        onDrag = { change, delta ->
          if (held < 0) return@detectDragGestures
          change.consume()
          finger += delta
          val r = area
          val p = current.points[held]
          val nx = (p.x + delta.x / r.width).coerceIn(0f, 1f)
          val ny = (p.y + delta.y / r.height).coerceIn(0f, 1f)
          onQuad(current.with(held, P(nx, ny)))
        },
      )
    }
  ) {
    val r = fitted(size.width, size.height, picture.width.toFloat() / picture.height)
    area = r
    drawImage(picture, dstOffset = IntOffset(r.left.toInt(), r.top.toInt()), dstSize = IntSize(r.width.toInt(), r.height.toInt()))
    drawQuad(quad, r, 1f, accent, shade = true)
    if (held >= 0) {
      // The magnifier sits in the top corner away from the finger, 2.5× what's under the corner.
      val p = quad.points[held]
      val at = Offset(r.left + p.x * r.width, r.top + p.y * r.height)
      val radius = 58.dp.toPx()
      val centre = Offset(if (at.x < size.width / 2) size.width - radius - 12.dp.toPx() else radius + 12.dp.toPx(), radius + 12.dp.toPx())
      val zoom = 2.5f
      val scale = r.width / picture.width
      val srcHalf = radius / zoom / scale
      val sx = p.x * picture.width
      val sy = p.y * picture.height
      val circle = Path().apply { addOval(Rect(centre, radius)) }
      clipPath(circle) {
        drawRect(NIGHT, Offset(centre.x - radius, centre.y - radius), androidx.compose.ui.geometry.Size(radius * 2, radius * 2))
        val l = (sx - srcHalf).toInt()
        val t = (sy - srcHalf).toInt()
        val side = (srcHalf * 2).toInt().coerceAtLeast(1)
        val cl = l.coerceIn(0, picture.width - 1)
        val ct = t.coerceIn(0, picture.height - 1)
        val cw = minOf(side - (cl - l), picture.width - cl).coerceAtLeast(1)
        val ch = minOf(side - (ct - t), picture.height - ct).coerceAtLeast(1)
        val k = radius * 2 / side
        drawImage(
          picture,
          srcOffset = IntOffset(cl, ct),
          srcSize = IntSize(cw, ch),
          dstOffset = IntOffset((centre.x - radius + (cl - l) * k).toInt(), (centre.y - radius + (ct - t) * k).toInt()),
          dstSize = IntSize((cw * k).toInt(), (ch * k).toInt()),
        )
      }
      drawCircle(Color.White, radius, centre, style = Stroke(3.dp.toPx()))
      drawLine(accent, centre - Offset(10.dp.toPx(), 0f), centre + Offset(10.dp.toPx(), 0f), 2.dp.toPx())
      drawLine(accent, centre - Offset(0f, 10.dp.toPx()), centre + Offset(0f, 10.dp.toPx()), 2.dp.toPx())
    }
  }
}

private fun DrawScope.drawQuad(q: Quad, r: Rect, alpha: Float, accent: Color, shade: Boolean = false) {
  val ps = q.points.map { Offset(r.left + it.x * r.width, r.top + it.y * r.height) }
  val path = Path().apply {
    moveTo(ps[0].x, ps[0].y)
    for (i in 1..3) lineTo(ps[i].x, ps[i].y)
    close()
  }
  if (shade) {
    // Outside the page darkened, so the page stands out.
    val outside = Path().apply {
      fillType = PathFillType.EvenOdd
      addRect(r)
      addPath(path)
    }
    drawPath(outside, Color.Black.copy(alpha = 0.5f))
  } else drawPath(path, accent.copy(alpha = 0.18f * alpha))
  drawPath(path, accent.copy(alpha = alpha), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
  val dot = if (shade) 11.dp.toPx() else 5.dp.toPx()
  for (p in ps) {
    drawCircle(Color.White.copy(alpha = alpha), dot, p)
    drawCircle(accent.copy(alpha = alpha), dot, p, style = Stroke(3.dp.toPx()))
  }
}

/** Where a picture of [aspect] (width / height) sits when fitted inside [w]×[h]. */
private fun fitted(w: Float, h: Float, aspect: Float): Rect {
  if (w <= 0f || h <= 0f) return Rect.Zero
  return if (w / h > aspect) {
    val pw = h * aspect
    Rect((w - pw) / 2f, 0f, (w + pw) / 2f, h)
  } else {
    val ph = w / aspect
    Rect(0f, (h - ph) / 2f, w, (h + ph) / 2f)
  }
}

// ——— Pieces ———

@Composable
private fun Shutter(busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
  val c = Tali.colors
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed || busy) 0.88f else 1f, tween(if (pressed) 80 else 200, easing = EaseOutStrong), label = "shutter")
  val spin = androidx.compose.animation.core.rememberInfiniteTransition(label = "busy")
  val angle by spin.animateFloat(0f, 360f, androidx.compose.animation.core.infiniteRepeatable(tween(900, easing = androidx.compose.animation.core.LinearEasing)), label = "busyAngle")
  Box(
    Modifier.size(84.dp)
      .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
      .border(4.dp, Color.White, CircleShape)
      .then(
        if (busy) Modifier.drawWithContent {
          drawContent()
          drawArc(c.accent, angle, 90f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        } else Modifier
      )
      .padding(8.dp)
      .graphicsLayer {
        scaleX = press
        scaleY = press
      }
      .clip(CircleShape)
      .background(c.accentBrush)
      .clickable(interactionSource = interaction, indication = null, enabled = enabled && !busy, role = Role.Button, onClickLabel = stringResource(R.string.cam_take), onClick = onClick)
  )
}

@Composable
internal fun RoundButton(icon: ImageVector, label: String, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val press by animateFloatAsState(if (pressed) 0.92f else 1f, tween(if (pressed) 80 else 160), label = "round")
  Box(
    Modifier.graphicsLayer {
        scaleX = press
        scaleY = press
      }
      .size(52.dp)
      .clip(CircleShape)
      .background(GLASS)
      .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
      .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
  }
}

@Composable
private fun SavePill(thumb: ImageBitmap, count: Int, onClick: () -> Unit) {
  val c = Tali.colors
  Row(
    Modifier.height(56.dp).clip(RoundedCornerShape(18.dp)).background(c.accentBrush).clickable(role = Role.Button, onClick = onClick).padding(start = 6.dp, end = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box {
      Image(thumb, null, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, Color.White, RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
      Box(Modifier.align(Alignment.TopEnd).offset(6, -6).size(20.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        Text("$count", style = MaterialTheme.typography.labelSmall, color = c.accent)
      }
    }
    Spacer(Modifier.width(10.dp))
    Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge, color = c.onAccent)
  }
}

private fun Modifier.offset(x: Int, y: Int) = this.then(Modifier.graphicsLayer { translationX = x.dp.toPx(); translationY = y.dp.toPx() })

@Composable
private fun SmallTool(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
  val c = Tali.colors
  Column(
    modifier.clip(RoundedCornerShape(14.dp)).background(c.sunken).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(icon, null, tint = c.accent, modifier = Modifier.size(22.dp))
    Spacer(Modifier.height(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall, color = c.ink, maxLines = 2, textAlign = TextAlign.Center)
  }
}

// ——— Pictures ———

private fun hasCamera(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

/**
 * Some tablets report their camera upside down for one way of holding them (Google's scanner turned
 * pages upside down on a Tab S6 Lite). When she turns a picture a half turn before keeping it, that's
 * remembered for that way of holding the tablet and later pictures come out turned already; another
 * half turn undoes it. Quarter turns aren't learned: a page taken sideways is often on purpose.
 */
private fun learnedTurn(context: Context, held: Int): Int = context.getSharedPreferences("camera", Context.MODE_PRIVATE).getInt("turn$held", 0)

private fun learnTurn(context: Context, held: Int) {
  context.getSharedPreferences("camera", Context.MODE_PRIVATE).edit().putInt("turn$held", (learnedTurn(context, held) + 2) % 4).apply()
}

/** A just-taken photo made ready: uncropped, as it is. */
private fun prepare(picture: Bitmap): Shot = Shot(picture, PageImage.scaledTo(picture, VIEW_SIDE).asImageBitmap(), Quad.whole(), Look.ORIGINAL)

/** The finished photo, written to the cache as a JPEG. */
private fun finished(context: Context, s: Shot): Taken {
  val flat = if (s.quad == Quad.whole()) s.picture else PageImage.straightened(s.picture, s.quad, MAX_SIDE)
  val out = PageImage.looked(flat, s.look)
  val dir = File(context.cacheDir, "camera").apply { mkdirs() }
  val file = File(dir, "page-${System.currentTimeMillis()}-${(Math.random() * 1e6).toInt()}.jpg")
  file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 92, it) }
  return Taken(file, PageImage.scaledTo(out, 160).asImageBitmap())
}

/** Pages left from earlier visits (already copied into notebooks) are cleared out after a day. */
internal fun clearOld(context: Context) {
  val day = System.currentTimeMillis() - 24 * 3600_000L
  File(context.cacheDir, "camera").listFiles()?.forEach { if (it.lastModified() < day) it.delete() }
}

internal val NIGHT = Color(0xFF0E0A18)
private val GLASS = Color(0xFF1B1430).copy(alpha = 0.62f)
internal const val MAX_SIDE = 3000

/** The language's own direction (right to left in Hebrew and Arabic), whatever the screen around it uses. */
@Composable
internal fun languageDirection(): androidx.compose.ui.unit.LayoutDirection =
  if (androidx.compose.ui.platform.LocalConfiguration.current.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) androidx.compose.ui.unit.LayoutDirection.Rtl
  else androidx.compose.ui.unit.LayoutDirection.Ltr

private const val VIEW_SIDE = 1600
