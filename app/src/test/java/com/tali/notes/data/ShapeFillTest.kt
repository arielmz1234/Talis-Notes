package com.tali.notes.data

import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import com.tali.notes.model.EllipseShape
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShapeFillTest {
  private val circle = EllipseShape(Pt(50f, 50f), 40f, 40f)
  private val box = Rect(8f, 8f, 92f, 92f)

  @Test
  fun fillSurvivesSaving() {
    val item = ShapeItem("s", 3, PenType.FINELINER, 0xFF000000.toInt(), 2f, circle, box, fill = 0xB39DB8F5.toInt())
    val back = ItemCodec.decode(ItemCodec.encode(item)) as ShapeItem
    assertEquals(0xB39DB8F5.toInt(), back.fill)
    assertEquals(circle, back.geometry)
  }

  @Test
  fun olderShapesHaveNoFill() {
    val back = ItemCodec.decode(ItemCodec.encode(ShapeItem("s", 3, PenType.FINELINER, 0xFF000000.toInt(), 2f, circle, box))) as ShapeItem
    assertNull(back.fill)
  }
}
