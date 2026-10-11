package com.tali.notes.ui.library

import com.tali.notes.data.numberedName
import org.junit.Assert.assertEquals
import org.junit.Test

class FreeNameTest {
  @Test
  fun theNameItselfWhenFree() = assertEquals("New notebook", nextFreeName("New notebook", listOf("Statics")))

  @Test
  fun theNextNumberWhenTaken() = assertEquals("New notebook (2)", nextFreeName("New notebook", listOf("new notebook", "New notebook (1) ", "New notebook (3)")))

  @Test
  fun aGapIsFilled() = assertEquals("New folder (1)", nextFreeName("New folder", listOf("New folder", "New folder (2)")))

  @Test
  fun aNumberedNameCountsOnFromItsStem() = assertEquals("Slides (2)", numberedName("Slides (1)", listOf("Slides", "Slides (1)")))

  @Test
  fun caseAndSpacesDontMakeANameDifferent() = assertEquals("Statics (1)", numberedName(" Statics ", listOf("statics")))
}
