package com.tali.notes.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageClipTest {
  private val page = Rect(0f, 0f, 100f, 100f)
  private val next = Rect(0f, 120f, 100f, 220f)

  @Test
  fun insideStaysWhole() {
    val runs = PageClip.runs(listOf(Pt(10f, 10f), Pt(50f, 50f), Pt(90f, 20f)), listOf(page))
    assertEquals(listOf(PageClip.Run(0f, 2f)), runs)
  }

  @Test
  fun offTheSideIsCutAtTheEdge() {
    val runs = PageClip.runs(listOf(Pt(50f, 50f), Pt(150f, 50f)), listOf(page))
    assertEquals(1, runs.size)
    assertEquals(0.5f, runs[0].end, 1e-4f)
  }

  @Test
  fun outAndBackInMakesTwoPieces() {
    val runs = PageClip.runs(listOf(Pt(50f, 50f), Pt(150f, 50f), Pt(50f, 60f)), listOf(page))
    assertEquals(2, runs.size)
    assertEquals(0.5f, runs[0].end, 1e-4f)
    assertEquals(1.5f, runs[1].start, 1e-4f)
  }

  @Test
  fun acrossTheGapIsCutBetweenPages() {
    val runs = PageClip.runs(listOf(Pt(50f, 90f), Pt(50f, 130f)), listOf(page, next))
    assertEquals(2, runs.size)
    assertEquals(0.25f, runs[0].end, 1e-4f)
    assertEquals(0.75f, runs[1].start, 1e-4f)
  }

  @Test
  fun theColumnLetsItCrossTheGap() {
    val column = Rect(0f, 0f, 100f, 220f)
    assertEquals(listOf(PageClip.Run(0f, 1f)), PageClip.runs(listOf(Pt(50f, 90f), Pt(50f, 130f)), listOf(column)))
  }

  @Test
  fun nothingInsideIsNothing() {
    assertTrue(PageClip.runs(listOf(Pt(150f, 50f), Pt(160f, 60f)), listOf(page)).isEmpty())
  }
}
