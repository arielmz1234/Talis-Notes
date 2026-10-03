package com.tali.notes.canvas

import org.junit.Assert.assertEquals
import org.junit.Test

class MovePageTest {
  private fun order(n: Int, from: Int, to: Int): List<Int> {
    val out = IntArray(n)
    for (p in 0 until n) out[Pages.movedIndex(p, from, to)] = p
    return out.toList()
  }

  @Test fun moveDown() = assertEquals(listOf(1, 2, 0, 3, 4), order(5, 0, 2))

  @Test fun moveUp() = assertEquals(listOf(0, 4, 1, 2, 3), order(5, 4, 1))

  @Test fun stayPut() = assertEquals(listOf(0, 1, 2), order(3, 1, 1))

  @Test fun deleteSome() = assertEquals(listOf(0, 2, 4), Pages.withoutPages(5, setOf(1, 3)))

  @Test fun deleteAll() = assertEquals(emptyList<Int>(), Pages.withoutPages(3, setOf(0, 1, 2)))

  @Test fun copiesSitAfterTheirPage() = assertEquals(listOf(0, 1, 1, 2, 3, 3), Pages.withCopies(4, setOf(1, 3)))
}
