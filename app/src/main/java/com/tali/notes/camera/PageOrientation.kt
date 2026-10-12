package com.tali.notes.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Which way up a page from Google's document scanner is. The scanner turns each page by its own
 * reading of the text's direction, which suits Latin letters: English pages come back right, but
 * Hebrew ones (letters hanging from a top line, like Latin seen upside down) come back turned over,
 * every time (seen on the emulator with an upright Hebrew page from the gallery). So the page is read
 * once with the Latin text reader, which reports each line's angle and how sure it is: lines it reads
 * confidently (English, formulas) tell which way up the page is by their angle; lines it can't read
 * well are another script ([Reading.NO_LATIN], turned by what the page check has learned); no lines
 * at all (a drawing, a photo) leave the page as it came. Measured: English lines read at 0° and about
 * 0.9 confidence; Hebrew turned over by the scanner read at 180° and about 0.3. Background threads only.
 */
object PageOrientation {
  enum class Reading { UPRIGHT, UPSIDE_DOWN, NO_LATIN }

  fun read(page: Bitmap): Reading {
    val lines =
      runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(page, 0)), 8, TimeUnit.SECONDS).textBlocks.flatMap { it.lines } }.getOrNull()
        ?: return Reading.UPRIGHT
    val sure = lines.filter { it.confidence >= SURE }
    if (sure.size >= MIN_LINES) {
      val over = sure.count { kotlin.math.abs(it.angle) > 90f }
      return if (over * 2 > sure.size) Reading.UPSIDE_DOWN else Reading.UPRIGHT
    }
    return if (lines.size >= MIN_LINES) Reading.NO_LATIN else Reading.UPRIGHT
  }

  private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

  /** A line read this surely is real Latin text. */
  private const val SURE = 0.6f
  private const val MIN_LINES = 2
}
