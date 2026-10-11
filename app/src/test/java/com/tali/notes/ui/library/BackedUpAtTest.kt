package com.tali.notes.ui.library

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

class BackedUpAtTest {
  private fun at(day: Int, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
      set(2026, Calendar.OCTOBER, day, hour, minute, 0)
      set(Calendar.MILLISECOND, 0)
    }.timeInMillis

  @Test
  fun saysTodayYesterdayOrTheDate() {
    val now = at(11, 18, 0)
    assertEquals("today, 14:20", backedUpAt(at(11, 14, 20), now))
    assertEquals("yesterday, 09:05", backedUpAt(at(10, 9, 5), now))
    assertEquals("8 Oct, 23:59", backedUpAt(at(8, 23, 59), now))
  }
}
