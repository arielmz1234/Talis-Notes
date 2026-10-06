package com.tali.notes.data

import com.tali.notes.model.PenSlot
import com.tali.notes.model.PenType
import org.junit.Assert.assertEquals
import org.junit.Test

class PencilDroppedTest {
  private val five =
    listOf(
      PenSlot("p1", PenType.FINELINER, 0, 2.4f),
      PenSlot("p2", PenType.FOUNTAIN, 0, 3f),
      PenSlot("p3", PenType.PENCIL, 0, 2.6f),
      PenSlot("p4", PenType.FINELINER, 0, 2.4f),
      PenSlot("p5", PenType.HIGHLIGHTER, 0, 18f),
    )

  @Test
  fun newSettingsStartWithFourPens() {
    assertEquals(4, AppSettings().pens.size)
    assertEquals(false, AppSettings().pens.any { it.type == PenType.PENCIL })
  }

  @Test
  fun theBuiltInPencilLeavesSavedSettings() {
    val s = AppSettings(pens = five, activePen = "p3").migrated()
    assertEquals(listOf("p1", "p2", "p4", "p5"), s.pens.map { it.id })
    assertEquals("p1", s.activePen)
  }

  @Test
  fun aPencilSheAddedOrMadeStays() {
    // Her own pencil (random id), and the third pen after she changed it to another type.
    val pens = five.map { if (it.id == "p3") it.copy(type = PenType.FOUNTAIN) else it } + PenSlot("a1b2c3d4", PenType.PENCIL, 0, 2f)
    assertEquals(6, AppSettings(pens = pens, activePen = "a1b2c3d4").migrated().pens.size)
  }
}
