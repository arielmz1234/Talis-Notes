package com.tali.notes.model

import com.tali.notes.data.ItemCodec
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapePacksTest {
  private val shapes = ShapePacks.all.flatMap { it.shapes }

  @Test
  fun everySubjectHasShapesWithUniqueIds() {
    val ids = shapes.map { it.id }
    assertEquals(ids.size, ids.toSet().size)
    ShapePacks.all.forEach { assertTrue(it.name, it.shapes.size >= 5) }
  }

  @Test
  fun lineShapesRunFromEndToEnd() {
    val p0 = Pt(100f, 200f)
    val p1 = Pt(300f, 260f)
    shapes.filter { it.mode == PackMode.LINE && it.kind == null }.forEach { s ->
      val f = s.create(p0, p1).snapFeatures()
      // Its two ends are where the pen started and stopped, so they connect to what they touch.
      assertTrue(s.label, f.points.any { it.dist(p0) < 0.5f })
      assertTrue(s.label, f.points.any { it.dist(p1) < 0.5f })
    }
  }

  @Test
  fun boxShapesStayInTheirBox() {
    val p0 = Pt(50f, 50f)
    val p1 = Pt(250f, 170f)
    shapes.filter { it.mode != PackMode.LINE && it.kind == null }.forEach { s ->
      val b = Rect.around(s.create(p0, p1).paths(1f).flatten())!!
      // Arrowheads and a speech bubble's tail may reach a little past.
      assertTrue("${s.label} $b", b.left >= 40f && b.top >= 40f && b.right <= 262f && b.bottom <= 215f)
      if (s.mode == PackMode.STRETCH) assertTrue(s.label, abs(b.width - 200f) < 12f && abs(b.height - 120f) < 12f)
      assertTrue(s.label, s.create(p0, p1).snapFeatures().segments.isNotEmpty())
    }
  }

  @Test
  fun everyJointCanBeDraggedAndDroppedInPlace() {
    shapes.filter { it.template != null }.forEach { s ->
      val g = s.create(Pt(40f, 60f), Pt(240f, 200f))
      val hs = g.handles()
      assertTrue(s.label, hs.isNotEmpty())
      hs.forEachIndexed { i, h ->
        // Put back where it is: nothing changes (no creeping as she touches a dot).
        val same = g.withHandle(i, h)
        val before = g.paths(2f).flatten()
        val after = same.paths(2f).flatten()
        assertEquals("${s.label} joint $i", before.size, after.size)
        before.zip(after).forEach { (a, b) -> assertTrue("${s.label} joint $i moved", a.dist(b) < 0.6f) }
        // Dragged a long way: still a sensible drawing.
        for (d in listOf(Pt(60f, 40f), Pt(-70f, -50f))) {
          val moved = g.withHandle(i, h + d).paths(2f).flatten()
          assertTrue("${s.label} joint $i", moved.isNotEmpty() && moved.all { it.x.isFinite() && it.y.isFinite() })
        }
      }
    }
  }

  private fun cells(g: ShapeGeometry) = ((g as ParamShape).drawn() as GroupShape).parts.count { it is PolyShape && it.closed }

  @Test
  fun aLongerStackHasMoreCells() {
    val short = ShapeTemplates.stack.create(Pt(0f, 0f), Pt(70f, 100f))
    val long = ShapeTemplates.stack.create(Pt(0f, 0f), Pt(70f, 300f))
    assertTrue(cells(long) > cells(short) + 5)
    // And by its bottom dot.
    val i = short.handles().indexOfFirst { abs(it.y - 100f) < 0.5f }
    assertTrue(cells(short.withHandle(i, Pt(35f, 300f))) == cells(long))
  }

  @Test
  fun aTallerTreeHasMoreLevels() {
    fun nodes(g: ShapeGeometry) = ((g as ParamShape).drawn() as GroupShape).parts.count { it is EllipseShape }
    assertEquals(7, nodes(ShapeTemplates.binaryTree.create(Pt(0f, 0f), Pt(200f, 90f))))
    assertEquals(31, nodes(ShapeTemplates.binaryTree.create(Pt(0f, 0f), Pt(300f, 180f))))
  }

  @Test
  fun aBarsDotSetsOnlyThatBar() {
    val chart = ShapeTemplates.barChart.create(Pt(0f, 0f), Pt(160f, 110f))
    val before = (chart.drawn() as GroupShape).parts
    val moved = chart.withHandle(1, Pt(chart.handles()[1].x, 20f)) as ParamShape
    val after = (moved.drawn() as GroupShape).parts
    val changed = before.indices.filter { before[it] != after[it] }
    assertEquals(listOf(2), changed) // part 0 is the axes, then one part per bar
  }

  @Test
  fun aLineShapesEndGoesWhereItsDragged() {
    val r = ShapeTemplates.resistor.create(Pt(0f, 0f), Pt(120f, 0f))
    val moved = r.withHandle(1, Pt(60f, 90f))
    assertTrue(moved.handles()[1].dist(Pt(60f, 90f)) < 0.01f)
    assertTrue(moved.handles()[0].dist(Pt(0f, 0f)) < 0.01f)
    val start = moved.withHandle(0, Pt(-30f, 10f))
    assertTrue(start.handles()[0].dist(Pt(-30f, 10f)) < 0.01f)
    assertTrue(start.handles()[1].dist(Pt(60f, 90f)) < 0.01f)
  }

  @Test
  fun aShapeWithJointsIsStoredAndReadBack() {
    val g = ShapeTemplates.gantt.create(Pt(10f, 20f), Pt(200f, 120f)).withHandle(0, Pt(60f, 30f))
    val json = ItemCodec.json.encodeToString(ShapeGeometry.serializer(), g)
    val back = ItemCodec.json.decodeFromString(ShapeGeometry.serializer(), json)
    assertEquals(g, back)
    assertNotEquals(ShapeTemplates.gantt.create(Pt(10f, 20f), Pt(200f, 120f)), back)
  }

  /** Every subject's shapes on one sheet (app/build/shape-packs.png), joints in red, to look over. */
  @Test
  fun contactSheet() {
    val cell = 130
    val cols = 10
    val rows = ShapePacks.all.size
    val img = BufferedImage(cols * cell + 220, rows * cell, BufferedImage.TYPE_INT_RGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.color = Color.WHITE
    g.fillRect(0, 0, img.width, img.height)
    ShapePacks.all.forEachIndexed { r, pack ->
      g.color = Color(60, 40, 120)
      g.drawString(pack.name, 8, r * cell + cell / 2)
      pack.shapes.forEachIndexed { i, s ->
        val x0 = 220f + i * cell + 12f
        val y0 = r * cell + 12f
        val geo =
          when {
            s.kind != null -> s.kind.inBox(Pt(x0, y0), Pt(x0 + 90f, y0 + 90f))
            s.mode == PackMode.LINE -> s.create(Pt(x0, y0 + 45f), Pt(x0 + 100f, y0 + 45f))
            else -> s.create(Pt(x0, y0), Pt(x0 + 100f, y0 + 95f))
          }
        g.color = Color(40, 40, 60)
        g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        geo.paths(1.2f).forEach { pts ->
          for (k in 0 until pts.size - 1) g.drawLine(pts[k].x.toInt(), pts[k].y.toInt(), pts[k + 1].x.toInt(), pts[k + 1].y.toInt())
        }
        if (s.template != null) {
          g.color = Color(220, 40, 60)
          geo.handles().forEach { h -> g.fillOval(h.x.toInt() - 3, h.y.toInt() - 3, 6, 6) }
        }
        g.color = Color(120, 120, 140)
        g.drawString(s.label, (x0 - 6).toInt(), (y0 + 112).toInt())
      }
    }
    File("build").mkdirs()
    ImageIO.write(img, "png", File("build/shape-packs.png"))
  }
}
