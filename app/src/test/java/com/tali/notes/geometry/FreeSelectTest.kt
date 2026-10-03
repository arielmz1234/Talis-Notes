package com.tali.notes.geometry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSelectTest {
  private val stroke = listOf(Pt(0f, 0f), Pt(100f, 0f))

  @Test fun tracingAcrossPicksItUp() = assertTrue(Lasso.nearPath(stroke, listOf(Pt(50f, -40f), Pt(50f, 40f)), 5f))

  @Test fun passingCloseCounts() = assertTrue(Lasso.nearPath(stroke, listOf(Pt(-20f, 8f), Pt(120f, 8f)), 10f))

  @Test fun passingFarDoesNot() = assertFalse(Lasso.nearPath(stroke, listOf(Pt(-20f, 30f), Pt(120f, 30f)), 10f))

  @Test fun touchingASinglePoint() = assertTrue(Lasso.nearPath(stroke, listOf(Pt(30f, 4f)), 5f))

  @Test fun pictureTouched() = assertTrue(Lasso.nearRect(Rect(0f, 0f, 50f, 50f), listOf(Pt(-30f, 25f), Pt(80f, 25f)), 5f))

  @Test fun pictureMissed() = assertFalse(Lasso.nearRect(Rect(0f, 0f, 50f, 50f), listOf(Pt(-30f, 90f), Pt(80f, 90f)), 5f))
}
