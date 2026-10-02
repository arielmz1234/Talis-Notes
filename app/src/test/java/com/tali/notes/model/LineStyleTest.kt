package com.tali.notes.model

import com.tali.notes.data.AppSettings
import com.tali.notes.geometry.Pt
import com.tali.notes.ink.StrokeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineStyleTest {
  private val line = listOf(Pt(0f, 0f), Pt(600f, 0f))

  @Test
  fun shortDashesHaveTheirPatternsLengths() {
    val size = 2f
    val pieces = StrokeFactory.dashes(line, size, LineStyle.SHORT_DASH)
    assertTrue(pieces.size > 50)
    // Pieces are cut a pen-width short (the round ends add it back); gaps a pen-width long.
    val first = pieces[0]
    assertEquals((2.4f - 1f) * size, first.last().x - first.first().x, 1e-3f)
    assertEquals((2f + 1f) * size, pieces[1].first().x - first.last().x, 1e-3f)
  }

  @Test
  fun dotAndDashAlternates() {
    val pieces = StrokeFactory.dashes(line, 2f, LineStyle.DOT_DASH)
    val lengths = pieces.take(4).map { it.last().x - it.first().x }
    assertTrue(lengths[0] > 4f && lengths[1] < 0.5f && lengths[2] > 4f && lengths[3] < 0.5f)
  }

  @Test
  fun shortPathsStaySolid() {
    val barb = listOf(Pt(0f, 0f), Pt(12f, 0f))
    assertEquals(listOf(barb), StrokeFactory.dashes(barb, 2f, LineStyle.LONG_DASH))
  }

  @Test
  fun aNewColourTakesTheOldestQuickSpotInPlace() {
    var pen = PenSlot("p", PenType.FINELINER, 0xFF1B1F2A.toInt(), 2f)
    val start = pen.quickColors
    assertEquals(3, start.size)
    pen = pen.pick(start[1]).pick(start[0]).pick(start[2])
    // start[1] is now the one used longest ago, so the new colour goes in its place.
    pen = pen.pick(0xFF00FF00.toInt())
    assertEquals(listOf(start[0], 0xFF00FF00.toInt(), start[2]), pen.quickColors)
    assertEquals(0xFF00FF00.toInt(), pen.color)
    // Picking one already there moves nothing.
    assertEquals(pen.quickColors, pen.pick(start[2]).quickColors)
  }

  @Test
  fun anUnusualColourIsShownAmongTheQuickOnes() {
    val pen = PenSlot("p", PenType.FINELINER, 0xFF123456.toInt(), 2f)
    assertTrue(0xFF123456.toInt() in pen.quickColors)
  }

  @Test
  fun theOldDashedPenBecomesAFinelinerDrawingShortDashes() {
    val s = AppSettings(pens = listOf(PenSlot("d", PenType.DASHED, 0, 2f))).migrated()
    assertEquals(PenType.FINELINER, s.pens[0].type)
    assertEquals(LineStyle.SHORT_DASH, s.pens[0].lineStyle)
  }

  @Test
  fun aHighlighterIsAlwaysSolid() {
    assertEquals(LineStyle.SOLID, PenSlot("h", PenType.HIGHLIGHTER, 0, 18f, line = LineStyle.DOTTED).lineStyle)
  }
}
