package com.tali.notes.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {
  @Test fun followsTheClock() {
    assertEquals("Good night", greetingFor(0))
    assertEquals("Good night", greetingFor(4))
    assertEquals("Good morning", greetingFor(5))
    assertEquals("Good morning", greetingFor(11))
    assertEquals("Good afternoon", greetingFor(12))
    assertEquals("Good afternoon", greetingFor(16))
    assertEquals("Good evening", greetingFor(17))
    assertEquals("Good evening", greetingFor(20))
    assertEquals("Good night", greetingFor(21))
    assertEquals("Good night", greetingFor(23))
  }
}
