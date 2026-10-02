package com.tali.notes.data

import com.tali.notes.geometry.Rect
import com.tali.notes.model.CustomPaper
import com.tali.notes.model.ImageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pictures brought to the front stay there after saving, and older pictures stay behind the writing. */
class ImageFrontTest {
  @Test
  fun frontSurvivesSaving() {
    val item = ImageItem("a", 7, "pic.jpg", Rect(0f, 0f, 100f, 50f)).inFront(true, 42)
    val back = ItemCodec.decode(ItemCodec.encode(item)) as ImageItem
    assertTrue(back.front)
    assertEquals(42L, back.z)
    assertEquals("pic.jpg", back.file)
  }

  @Test
  fun olderPicturesStayBehind() {
    // A row saved before pictures could come forward has no "front" in its payload.
    val old = ItemRow("b", 3, ItemCodec.KIND_IMAGE, null, 0, 0f, """{"file":"x.jpg","rect":{"left":0.0,"top":0.0,"right":10.0,"bottom":10.0}}""".encodeToByteArray(), 0f, 0f, 10f, 10f)
    val item = ItemCodec.decode(old) as ImageItem
    assertFalse(item.front)
  }

  @Test
  fun paperOpacityFadesLinesOnly() {
    val cp = CustomPaper("p", "Mine", lineColor = 0xFFDADADA.toInt(), opacity = 0.5f)
    assertEquals(0x7F, cp.faded(cp.lineColor) ushr 24)
    assertEquals(0xDADADA, cp.faded(cp.lineColor) and 0xFFFFFF)
    // Never fainter than 15%, so lines can't vanish completely.
    assertEquals((255 * 0.15f).toInt(), cp.copy(opacity = 0f).faded(0xFF000000.toInt()) ushr 24)
  }
}
