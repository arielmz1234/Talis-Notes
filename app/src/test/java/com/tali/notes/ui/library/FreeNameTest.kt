package com.tali.notes.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class FreeNameTest {
  @Test
  fun theNameItselfWhenFree() = assertEquals("New notebook", nextFreeName("New notebook", listOf("Statics")))

  @Test
  fun theNextNumberWhenTaken() = assertEquals("New notebook 3", nextFreeName("New notebook", listOf("new notebook", "New notebook 2 ", "New notebook 4")))

  @Test
  fun aGapIsFilled() = assertEquals("New folder 2", nextFreeName("New folder", listOf("New folder", "New folder 3")))
}
