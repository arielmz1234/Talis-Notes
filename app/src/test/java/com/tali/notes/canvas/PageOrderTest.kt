package com.tali.notes.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class PageOrderTest {
  @Test fun deleteSome() = assertEquals(listOf(0, 2, 4), Pages.withoutPages(5, setOf(1, 3)))

  @Test fun deleteAll() = assertEquals(emptyList<Int>(), Pages.withoutPages(3, setOf(0, 1, 2)))

  @Test fun copiesSitAfterTheirPage() = assertEquals(listOf(0, 1, 1, 2, 3, 3), Pages.withCopies(4, setOf(1, 3)))
}
