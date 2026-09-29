package com.tali.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import androidx.ink.storage.decode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tali.notes.canvas.Document
import com.tali.notes.canvas.ItemRenderer
import com.tali.notes.data.ImageStore
import com.tali.notes.data.ItemCodec
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.Brushes
import com.tali.notes.ink.StrokeFactory
import com.tali.notes.model.InkItem
import com.tali.notes.model.PenType
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * How long a big notebook (a semester of handwriting) takes to open, and how long the visible part
 * takes to draw. Prints the numbers to logcat under "TaliBench"; asserts only that it all loads.
 */
@RunWith(AndroidJUnit4::class)
class LoadBenchmarkTest {
  private fun handwriting(n: Int): List<InkItem> =
    List(n) { i ->
      // Word-sized scribbles laid out in lines, like a page of notes.
      val x0 = (i % 20) * 60f
      val y0 = (i / 20) * 48f
      val pts = List(60) { k -> Pt(x0 + k * 0.8f, y0 + 12f * sin(k * 0.45f + i)) }
      val brush = Brushes.brush(PenType.FOUNTAIN, 0xFF1B1F2A.toInt(), 3f)
      InkItem("s$i", i.toLong(), PenType.FOUNTAIN, StrokeFactory.strokeAlong(pts, brush))
    }

  @Test
  fun bigNotebookOpensAndDraws() {
    val n = 5000
    val items = handwriting(n)
    val rows = items.map(ItemCodec::encode)

    var t = SystemClock.elapsedRealtime()
    val decoded = rows.mapNotNull(ItemCodec::decode)
    val decodeMs = SystemClock.elapsedRealtime() - t
    assertEquals(n, decoded.size)

    t = SystemClock.elapsedRealtime()
    val doc = Document(decoded)
    val docMs = SystemClock.elapsedRealtime() - t

    // Draw one screenful (about 1300 x 800 dp) the way a set of tiles would.
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val renderer = ItemRenderer(Brushes.renderer(), ImageStore(context.cacheDir))
    val bmp = Bitmap.createBitmap(1334, 800, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    t = SystemClock.elapsedRealtime()
    val visible = doc.intersecting(Rect(0f, 0f, 1334f, 800f))
    renderer.drawAll(canvas, visible, android.graphics.Matrix())
    val drawMs = SystemClock.elapsedRealtime() - t

    Log.i("TaliBench", "strokes=$n open=${decodeMs + docMs}ms firstScreen(${visible.size} strokes)=${drawMs}ms")

    // Everything else, built on all cores the way the notebook warms up in the background.
    t = SystemClock.elapsedRealtime()
    val rest = decoded.filterIsInstance<InkItem>().filter { !it.isBuilt }
    val next = java.util.concurrent.atomic.AtomicInteger(0)
    val threads = List(Runtime.getRuntime().availableProcessors().coerceIn(1, 6)) {
      Thread { while (true) { val item = rest.getOrNull(next.getAndIncrement()) ?: break; item.stroke } }.apply { start() }
    }
    threads.forEach { it.join() }
    Log.i("TaliBench", "background build of ${rest.size} strokes on ${threads.size} threads=${SystemClock.elapsedRealtime() - t}ms")
  }
}
