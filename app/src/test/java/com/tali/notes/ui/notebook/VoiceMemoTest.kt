package com.tali.notes.ui.notebook

import com.tali.notes.audio.VoiceRecorder
import com.tali.notes.geometry.Pt
import com.tali.notes.model.AudioItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMemoTest {
  @Test
  fun buttonsInOneColumnKeepTheirDistance() {
    val out = spread(listOf("a" to Pt(100f, 50f), "b" to Pt(100f, 60f), "c" to Pt(100f, 300f)), spacing = 58f, size = 48f)
    assertEquals(50f, out["a"]!!.y)
    assertEquals(108f, out["b"]!!.y)
    assertEquals(300f, out["c"]!!.y)
  }

  @Test
  fun buttonsInOtherColumnsAreLeftAlone() {
    val out = spread(listOf("a" to Pt(100f, 50f), "b" to Pt(400f, 55f)), spacing = 58f, size = 48f)
    assertEquals(55f, out["b"]!!.y)
  }

  @Test
  fun aWaveHasItsBarsScaledToTheLoudest() {
    val wave = VoiceRecorder.waveOf(List(200) { if (it == 100) 0.5f else 0.1f })
    assertEquals(AudioItem.WAVE_BARS, wave.size)
    assertEquals(100, wave.max())
    assertTrue(wave.min() in 15..25)
  }

  @Test
  fun aShortRecordingIsStretched() {
    val wave = VoiceRecorder.waveOf(listOf(0.3f, 0.6f))
    assertEquals(AudioItem.WAVE_BARS, wave.size)
    assertEquals(50, wave.first())
    assertEquals(100, wave.last())
  }
}
