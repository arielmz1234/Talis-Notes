package com.tali.notes.ui.library

import com.tali.notes.data.LibrarySort
import com.tali.notes.data.NotebookMeta
import com.tali.notes.model.PaperStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SortTest {
  private fun nb(id: String, title: String, created: Long, updated: Long) = NotebookMeta(id, title, created, updated, 0, PaperStyle.DOTS, null, 0, null)

  private val list = listOf(nb("a", "Statics 10", 1, 30), nb("b", "statics 2", 3, 10), nb("c", "Hydraulics", 2, 20))

  @Test
  fun byName() = assertEquals(listOf("c", "b", "a"), sortNotebooks(list, LibrarySort.NAME, false, emptyList()).map { it.id })

  @Test
  fun byNameTurnedRound() = assertEquals(listOf("a", "b", "c"), sortNotebooks(list, LibrarySort.NAME, true, emptyList()).map { it.id })

  @Test
  fun byEdited() = assertEquals(listOf("a", "c", "b"), sortNotebooks(list, LibrarySort.EDITED, false, emptyList()).map { it.id })

  @Test
  fun byCreatedOldestFirst() = assertEquals(listOf("a", "c", "b"), sortNotebooks(list, LibrarySort.CREATED, true, emptyList()).map { it.id })

  @Test
  fun byOpenedThenEdited() = assertEquals(listOf("b", "a", "c"), sortNotebooks(list, LibrarySort.OPENED, false, listOf("b")).map { it.id })
}
