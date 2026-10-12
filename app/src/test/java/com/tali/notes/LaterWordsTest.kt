package com.tali.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.RuntimeEnvironment

/** Saved messages (a backup's error, a card's note) follow a change of language. */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [35], application = android.app.Application::class)
class LaterWordsTest {
  @Test
  fun savedInOneLanguageShownInAnother() {
    val saved = later(R.string.backup_failed_n, "timeout")
    assertEquals("Backup failed: timeout", shown(saved))
    RuntimeEnvironment.setQualifiers("es")
    assertEquals("La copia falló: timeout", shown(saved))
    RuntimeEnvironment.setQualifiers("iw")
    assertEquals("הגיבוי נכשל: timeout", shown(saved))
  }

  @Test
  fun numbersPiecesAndPlainTextAllWork() {
    val joined = later(R.string.n_notebooks_added, 3) + ", " + later(R.string.t_1_notebook_updated) + "."
    assertEquals("3 notebooks added, 1 notebook updated.", shown(joined))
    assertEquals("Just some text", shown("Just some text"))
  }
}
