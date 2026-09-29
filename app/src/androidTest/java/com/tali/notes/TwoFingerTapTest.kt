package com.tali.notes

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tali.notes.canvas.CanvasHost
import com.tali.notes.canvas.Change
import com.tali.notes.canvas.Document
import com.tali.notes.canvas.NoteCanvasView
import com.tali.notes.canvas.SelectionInfo
import com.tali.notes.data.AppSettings
import com.tali.notes.geometry.Pt
import com.tali.notes.model.LineShape
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Two-finger tap undoes and three-finger tap redoes; a two-finger drag only pans. */
@RunWith(AndroidJUnit4::class)
class TwoFingerTapTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  private fun setUp(fingerDraws: Boolean): Pair<NoteCanvasView, Document> {
    lateinit var view: NoteCanvasView
    val doc = Document(emptyList())
    instrumentation.runOnMainSync {
      view =
        NoteCanvasView(
          // The view needs a context tied to a display (motion prediction asks for its refresh rate).
          instrumentation.targetContext.createDisplayContext(
            instrumentation.targetContext.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(android.view.Display.DEFAULT_DISPLAY)
          ),
          object : CanvasHost {
            override fun onSelectionChanged(selection: SelectionInfo?) = Unit

            override fun onZoomChanged(zoom: Float) = Unit
          },
        )
      view.settings = AppSettings(fingerDraws = fingerDraws)
      view.measure(View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY))
      view.layout(0, 0, 1200, 800)
      view.attach(doc, null)
      doc.commit(Change(emptyList(), listOf(ShapeItem(doc.newId(), doc.nextZ(), PenType.FINELINER, 0xFF000000.toInt(), 2.4f, LineShape(Pt(10f, 10f), Pt(200f, 10f))))))
    }
    return view to doc
  }

  private fun event(down: Long, t: Long, action: Int, points: List<Pair<Float, Float>>): MotionEvent {
    val props = Array(points.size) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
    val coords = Array(points.size) { i -> MotionEvent.PointerCoords().apply { x = points[i].first; y = points[i].second; pressure = 1f; size = 1f } }
    return MotionEvent.obtain(down, t, action, points.size, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
  }

  /** Puts [fingers] fingers down one after another, optionally drags them by [dx], then lifts them all. */
  private fun tap(view: NoteCanvasView, fingers: Int, dx: Float = 0f) {
    val down = SystemClock.uptimeMillis()
    val base = List(fingers) { i -> (400f + i * 120f) to 400f }
    instrumentation.runOnMainSync {
      view.dispatchTouchEvent(event(down, down, MotionEvent.ACTION_DOWN, base.take(1)))
      for (n in 2..fingers) view.dispatchTouchEvent(event(down, down + n, MotionEvent.ACTION_POINTER_DOWN or ((n - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), base.take(n)))
      var moved = base
      if (dx != 0f) {
        for (step in 1..6) {
          moved = base.map { (x, y) -> (x + dx * step / 6) to y }
          view.dispatchTouchEvent(event(down, down + 10 + step * 10, MotionEvent.ACTION_MOVE, moved))
        }
      }
      for (n in fingers downTo 2) view.dispatchTouchEvent(event(down, down + 90, MotionEvent.ACTION_POINTER_UP or ((n - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), moved.take(n)))
      view.dispatchTouchEvent(event(down, down + 100, MotionEvent.ACTION_UP, moved.take(1)))
    }
  }

  @Test
  fun twoFingerTapUndoesAndThreeFingerTapRedoes() {
    val (view, doc) = setUp(fingerDraws = false)
    assertEquals(1, doc.all.size)
    tap(view, 2)
    assertEquals(0, doc.all.size)
    tap(view, 3)
    assertEquals(1, doc.all.size)
  }

  @Test
  fun twoFingerTapUndoesWhenFingersDraw() {
    val (view, doc) = setUp(fingerDraws = true)
    tap(view, 2)
    assertEquals(0, doc.all.size)
  }

  @Test
  fun twoFingerDragDoesNotUndo() {
    val (view, doc) = setUp(fingerDraws = false)
    tap(view, 2, dx = 200f)
    assertEquals(1, doc.all.size)
  }
}
