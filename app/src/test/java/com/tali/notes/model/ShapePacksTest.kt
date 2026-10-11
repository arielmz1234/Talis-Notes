package com.tali.notes.model

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
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapePacksTest {
  @Test
  fun everySubjectHasShapesWithUniqueIds() {
    val ids = ShapePacks.all.flatMap { p -> p.shapes.map { it.id } }
    assertEquals(ids.size, ids.toSet().size)
    ShapePacks.all.forEach { assertTrue(it.name, it.shapes.size >= 5) }
  }

  @Test
  fun lineShapesRunFromEndToEnd() {
    val p0 = Pt(100f, 200f)
    val p1 = Pt(300f, 260f)
    ShapePacks.all.flatMap { it.shapes }.filter { it.mode == PackMode.LINE && it.kind == null }.forEach { s ->
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
    ShapePacks.all.flatMap { it.shapes }.filter { it.mode != PackMode.LINE && it.kind == null }.forEach { s ->
      val b = Rect.around(s.create(p0, p1).paths(1f).flatten())!!
      // Arrowheads may reach a little past.
      assertTrue("${s.label} $b", b.left >= 40f && b.top >= 40f && b.right <= 260f && b.bottom <= 180f)
      if (s.mode == PackMode.STRETCH) assertTrue(s.label, abs(b.width - 200f) < 12f && abs(b.height - 120f) < 12f)
      assertTrue(s.label, s.create(p0, p1).snapFeatures().segments.isNotEmpty())
    }
  }

  /** Every subject's shapes on one sheet (app/build/shape-packs.png), to look over. */
  @Test
  fun contactSheet() {
    val cell = 120
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
        val x0 = 220f + i * cell + 15f
        val y0 = r * cell + 12f
        val geo = if (s.mode == PackMode.LINE) s.create(Pt(x0, y0 + 45f), Pt(x0 + 90f, y0 + 45f)) else s.create(Pt(x0, y0), Pt(x0 + 90f, y0 + 90f))
        g.color = Color(40, 40, 60)
        g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        geo.paths(1.2f).forEach { pts ->
          for (k in 0 until pts.size - 1) g.drawLine(pts[k].x.toInt(), pts[k].y.toInt(), pts[k + 1].x.toInt(), pts[k + 1].y.toInt())
        }
        g.color = Color(120, 120, 140)
        g.drawString(s.label, (x0 - 8).toInt(), (y0 + 104).toInt())
      }
    }
    File("build").mkdirs()
    ImageIO.write(img, "png", File("build/shape-packs.png"))
  }
}
