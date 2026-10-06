package com.tali.notes.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tali.notes.model.AudioItem
import java.io.File
import java.util.UUID
import kotlin.math.ln
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A finished recording: its file (in the audio folder), length and sound wave. */
class Recorded(val file: String, val durationMs: Long, val wave: List<Int>)

/**
 * Records and plays voice memos. One recording and one playback at a time; everything it shows
 * (levels while recording, where playback is) is Compose state, so the memo buttons follow it.
 * Its own [scope] (the notebook's) drives the level and progress readings.
 */
class VoiceRecorder(private val context: Context, private val dir: File, private val scope: CoroutineScope) {
  private var recorder: MediaRecorder? = null
  private var file: File? = null
  private var startedAt = 0L
  private var sampling: Job? = null

  /** Whether a recording is going on. */
  var recording by mutableStateOf(false)
    private set

  /** How loud the recording has been, one reading per [SAMPLE_MS] (0..1), for the live sound wave. */
  val levels = mutableStateListOf<Float>()

  /** How long the recording has run, in ms (updated with the levels). */
  var elapsedMs by mutableLongStateOf(0L)
    private set

  /** Starts recording; false if the microphone couldn't be opened. */
  fun start(): Boolean {
    if (recording) return true
    val out = File(dir.apply { mkdirs() }, "memo-${UUID.randomUUID()}.m4a")
    val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
    val ok =
      runCatching {
          r.setAudioSource(MediaRecorder.AudioSource.MIC)
          r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
          r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
          r.setAudioChannels(1)
          r.setAudioSamplingRate(44_100)
          r.setAudioEncodingBitRate(96_000)
          r.setOutputFile(out.path)
          r.prepare()
          r.start()
        }
        .isSuccess
    if (!ok) {
      runCatching { r.release() }
      out.delete()
      return false
    }
    stopPlayback()
    recorder = r
    file = out
    startedAt = SystemClock.elapsedRealtime()
    levels.clear()
    elapsedMs = 0L
    recording = true
    sampling =
      scope.launch {
        while (isActive && recording) {
          val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
          levels += level(amp)
          elapsedMs = SystemClock.elapsedRealtime() - startedAt
          delay(SAMPLE_MS)
        }
      }
    return true
  }

  /** Stops and keeps the recording; null if there was nothing worth keeping (under half a second). */
  fun stop(): Recorded? {
    val r = recorder ?: return null
    val out = file
    val duration = SystemClock.elapsedRealtime() - startedAt
    recording = false
    sampling?.cancel()
    recorder = null
    file = null
    // Stopping straight after starting throws (nothing was recorded): drop the file then.
    val saved = runCatching { r.stop() }.isSuccess
    runCatching { r.release() }
    if (!saved || out == null || duration < MIN_MS) {
      out?.delete()
      return null
    }
    return Recorded(out.name, duration, waveOf(levels.toList()))
  }

  /** Stops and throws the recording away. */
  fun cancel() {
    val r = recorder ?: return
    recording = false
    sampling?.cancel()
    runCatching { r.stop() }
    runCatching { r.release() }
    file?.delete()
    recorder = null
    file = null
  }

  // ---- Playback ----

  private var player: MediaPlayer? = null
  private var ticking: Job? = null

  /** The memo playing or paused, or null. */
  var current by mutableStateOf<String?>(null)
    private set

  var playing by mutableStateOf(false)
    private set

  /** How far into [current] playback is, 0..1. */
  var progress by mutableFloatStateOf(0f)
    private set

  var positionMs by mutableLongStateOf(0L)
    private set

  /** Plays [memo] (from where it was paused if it's the current one); false if it can't be read. */
  fun play(memo: AudioItem): Boolean {
    if (recording) return false
    if (current == memo.id && player != null) {
      val p = player!!
      // At the end, playing again starts over.
      if (progress >= 0.999f) runCatching { p.seekTo(0) }
      runCatching { p.start() }.onFailure { return false }
      playing = true
      tick()
      return true
    }
    stopPlayback()
    val f = File(dir, memo.file)
    if (!f.exists()) return false
    val p = MediaPlayer()
    val ok =
      runCatching {
          p.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
          p.setDataSource(f.path)
          p.prepare()
          p.setOnCompletionListener {
            playing = false
            progress = 1f
            positionMs = it.duration.toLong()
          }
          p.start()
        }
        .isSuccess
    if (!ok) {
      runCatching { p.release() }
      return false
    }
    player = p
    current = memo.id
    playing = true
    progress = 0f
    positionMs = 0L
    tick()
    return true
  }

  fun pause() {
    runCatching { player?.pause() }
    playing = false
  }

  /** Jumps to [fraction] of the memo playing (or paused). */
  fun seek(fraction: Float) {
    val p = player ?: return
    val d = runCatching { p.duration }.getOrDefault(0)
    if (d <= 0) return
    val to = (fraction.coerceIn(0f, 1f) * d).toInt()
    runCatching { p.seekTo(to) }
    positionMs = to.toLong()
    progress = to.toFloat() / d
  }

  fun stopPlayback() {
    ticking?.cancel()
    runCatching { player?.stop() }
    runCatching { player?.release() }
    player = null
    current = null
    playing = false
    progress = 0f
    positionMs = 0L
  }

  /** Follows playback about once a frame while it plays. */
  private fun tick() {
    ticking?.cancel()
    ticking =
      scope.launch {
        while (isActive && playing) {
          val p = player ?: break
          val d = runCatching { p.duration }.getOrDefault(0)
          val at = runCatching { p.currentPosition }.getOrDefault(0)
          if (d > 0) {
            positionMs = at.toLong()
            progress = (at.toFloat() / d).coerceIn(0f, 1f)
          }
          delay(16)
        }
      }
  }

  /** Stops everything; a recording going on is kept and returned. */
  fun release(): Recorded? {
    val kept = if (recording) stop() else null
    stopPlayback()
    return kept
  }

  companion object {
    const val SAMPLE_MS = 50L
    private const val MIN_MS = 500L

    /** A microphone reading (0..32767) as a loudness that looks right as a bar (0..1). */
    fun level(amp: Int): Float = (ln(1f + max(0, amp) / 200f) / ln(1f + 32767f / 200f)).coerceIn(0f, 1f)

    /** [levels] squeezed (or stretched) into [AudioItem.WAVE_BARS] bars of 0..100, the loudest in each. */
    fun waveOf(levels: List<Float>): List<Int> {
      val n = AudioItem.WAVE_BARS
      if (levels.isEmpty()) return List(n) { 0 }
      // Scaled to its loudest moment, but never more than a speaking voice would be: a quiet room stays low.
      val peak = levels.max().coerceAtLeast(0.45f)
      return List(n) { i ->
        val from = i * levels.size / n
        val to = maxOf(from + 1, (i + 1) * levels.size / n).coerceAtMost(levels.size)
        val v = levels.subList(from.coerceAtMost(levels.size - 1), to).max()
        ((v / peak) * 100f).toInt().coerceIn(0, 100)
      }
    }
  }
}
