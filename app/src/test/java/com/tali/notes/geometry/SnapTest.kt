package com.tali.notes.geometry

import com.tali.notes.model.LineShape
import com.tali.notes.model.PolyShape
import com.tali.notes.model.snapFeatures
import com.tali.notes.model.upgraded
import com.tali.notes.model.GroupShape
import com.tali.notes.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapTest {
  private fun rect(l: Float, t: Float, r: Float, b: Float) = PolyShape(listOf(Pt(l, t), Pt(r, t), Pt(r, b), Pt(l, b)), true)

  @Test
  fun aLineEndLocksOntoAnUprightLine() {
    val wall = LineShape(Pt(100f, 0f), Pt(100f, 200f)).snapFeatures()
    // A level line whose right end stops 6 units short of the wall.
    val beam = LineShape(Pt(0f, 80f), Pt(94f, 80f)).snapFeatures()
    val r = Snap.move(beam, listOf(wall), tol = 10f)
    assertEquals(6f, r.offset.x, 0.01f)
    assertEquals(0f, r.offset.y, 0.01f)
    assertTrue(r.guides.any { it is SnapGuide.Touch })
  }

  @Test
  fun anEndOnACornerBeatsAnEdge() {
    val box = rect(100f, 100f, 200f, 160f).snapFeatures()
    val line = LineShape(Pt(0f, 0f), Pt(96f, 97f)).snapFeatures()
    val r = Snap.move(line, listOf(box), tol = 10f)
    assertEquals(4f, r.offset.x, 0.01f)
    assertEquals(3f, r.offset.y, 0.01f)
  }

  @Test
  fun boxesLineUpTheirTopsAndMiddles() {
    val a = rect(0f, 0f, 100f, 50f).snapFeatures()
    // Far to the right, its top 5 above the first one's top.
    val b = rect(300f, -5f, 360f, 40f).snapFeatures()
    val r = Snap.move(b, listOf(a), tol = 8f)
    assertEquals(5f, r.offset.y, 0.01f)
    assertEquals(0f, r.offset.x, 0.01f)
    assertTrue(r.guides.single() is SnapGuide.Align)
  }

  @Test
  fun sameSizeBoxesShowEveryLineThatLinesUp() {
    val a = rect(0f, 0f, 100f, 50f).snapFeatures()
    val b = rect(300f, 4f, 400f, 54f).snapFeatures()
    val r = Snap.move(b, listOf(a), tol = 8f)
    assertEquals(-4f, r.offset.y, 0.01f)
    // Top, middle and bottom all line up.
    assertEquals(3, r.guides.filterIsInstance<SnapGuide.Align>().count { !it.vertical })
  }

  @Test
  fun resizingLinesUpTheFarSide() {
    val target = rect(0f, 0f, 200f, 50f).snapFeatures()
    // A box beside it, being resized from its top-left corner.
    val box = Rect(300f, 0f, 400f, 48f)
    val (s, guides) = Snap.scale(box, Pt(300f, 0f), 1.02f, listOf(target), tol = 4f)
    // Its bottom (48 × 1.02 = 49) locks to the target's bottom at 50.
    assertEquals(50f / 48f, s, 0.001f)
    assertTrue(guides.any { it is SnapGuide.Align && !it.vertical && it.at == 50f })
  }

  @Test
  fun nothingNearbyMeansNoNudge() {
    val a = rect(0f, 0f, 100f, 50f).snapFeatures()
    val b = rect(300f, 120f, 360f, 170f).snapFeatures()
    val r = Snap.move(b, listOf(a), tol = 8f)
    assertEquals(Pt.ZERO, r.offset)
    assertTrue(r.guides.isEmpty())
  }

  @Test
  fun aDraggedEndLandsOnASlantedEdge() {
    val slope = LineShape(Pt(0f, 0f), Pt(100f, 100f)).snapFeatures()
    val p = Snap.point(Pt(50f, 46f), listOf(slope), tol = 8f)
    assertNotNull(p)
    assertEquals(p!!.x, p.y, 0.01f)
    assertNull(Snap.point(Pt(50f, 30f), listOf(slope), tol = 8f))
  }

  @Test
  fun aLineKeepsItsAngleWhenItStopsOnAnEdge() {
    val wall = LineShape(Pt(100f, 0f), Pt(100f, 200f)).snapFeatures()
    // Drawn level from (0, 50) and stopping just short of the wall, 4 units low.
    val p = Snap.alongLine(Pt(0f, 50f), Pt(95f, 50f), listOf(wall), tol = 8f)
    assertEquals(Pt(100f, 50f), p)
    assertEquals(Pt(100f, 200f), Snap.corner(Pt(97f, 195f), listOf(wall), tol = 8f))
  }

  @Test
  fun aDotLinesUpWithAnotherShapesSide() {
    val box = rect(0f, 0f, 100f, 50f).snapFeatures()
    val (p, guides) = Snap.alignPoint(Pt(103f, 200f), listOf(box), tol = 6f)
    assertEquals(100f, p.x, 0.01f)
    assertEquals(200f, p.y, 0.01f)
    assertTrue(guides.single() is SnapGuide.Align)
  }

  @Test
  fun engineeringSymbolsAttachAtTheirPoint() {
    // A support drawn from its tip: the tip is where the pen landed, and it snaps (its hatching doesn't).
    val pin = ShapeKind.PIN_SUPPORT.create(Pt(200f, 100f), Pt(200f, 180f))
    val f = pin.snapFeatures()
    assertTrue(f.points.any { it.dist(Pt(200f, 100f)) < 0.01f })
    assertTrue(f.points.size < 12)
    assertEquals(180f, f.box!!.bottom + 0f, 25f)
    // A point load's tip is the end of the drag; a moment is centred where it starts.
    assertEquals(Pt(50f, 90f), (ShapeKind.POINT_LOAD.create(Pt(50f, 10f), Pt(50f, 90f)) as LineShape).b)
    assertEquals(Pt(5f, 5f), (ShapeKind.MOMENT.create(Pt(5f, 5f), Pt(45f, 5f)) as com.tali.notes.model.ArcShape).center)
    // A fixed support's wall runs from start to end.
    val wall = ShapeKind.FIXED_SUPPORT.create(Pt(0f, 0f), Pt(0f, 100f)).snapFeatures()
    assertTrue(wall.points.containsAll(listOf(Pt(0f, 0f), Pt(0f, 100f))))
    assertEquals(1, wall.segments.size)
  }

  @Test
  fun touchingHoldsFromFurtherThanLiningUp() {
    val s = SnapSession(contact = 10f, align = 5f)
    val box = rect(0f, 0f, 100f, 50f).snapFeatures()
    // 7 away: a corner landing on the box's side catches (a touch)...
    val touching = rect(107f, 10f, 160f, 40f).snapFeatures()
    assertEquals(-7f, Snap.move(touching, listOf(box), s).offset.x, 0.01f)
    // ...but a box far below with its side 7 off the box's side doesn't line up (softer).
    s.begin()
    val below = rect(107f, 300f, 160f, 340f).snapFeatures()
    assertEquals(0f, Snap.move(below, listOf(box), s).offset.x, 0.01f)
  }

  @Test
  fun pulledFreeOfALockItLetsGoUntilSheMovesAway() {
    val s = SnapSession(contact = 10f, align = 5f)
    val corner = listOf(rect(100f, 100f, 200f, 160f).snapFeatures())
    fun at(x: Float, y: Float): Pt? {
      s.begin()
      return Snap.point(Pt(x, y), corner, s).also { s.end() }
    }
    // Onto the corner, then pulled away past where it catches, then back near it: left free.
    assertEquals(Pt(100f, 100f), at(96f, 97f))
    assertNull(at(85f, 85f))
    assertNull(at(95f, 95f))
    // The edges meeting there don't pull it back onto the corner either.
    assertNull(at(97f, 98f))
    // Far away and back: it catches again.
    assertNull(at(40f, 40f))
    assertEquals(Pt(100f, 100f), at(96f, 97f))
  }

  @Test
  fun aLockSheSlidesOutOfIntoAnotherDoesntLetGo() {
    val s = SnapSession(contact = 10f, align = 5f)
    val wall = listOf(LineShape(Pt(100f, 0f), Pt(100f, 400f)).snapFeatures())
    fun at(x: Float, y: Float): Pt? {
      s.begin()
      return Snap.point(Pt(x, y), wall, s).also { s.end() }
    }
    // Sliding along the wall stays on it the whole way.
    assertEquals(100f, at(104f, 100f)!!.x, 0.01f)
    assertEquals(100f, at(104f, 200f)!!.x, 0.01f)
    assertEquals(100f, at(97f, 300f)!!.x, 0.01f)
  }

  @Test
  fun moveShowsOneCrossWhereItTouches() {
    // A box resting in a corner touches both walls at once: one cross.
    val floor = LineShape(Pt(0f, 100f), Pt(300f, 100f)).snapFeatures()
    val wall = LineShape(Pt(0f, 0f), Pt(0f, 100f)).snapFeatures()
    val box = rect(3f, 40f, 63f, 97f).snapFeatures()
    val r = Snap.move(box, listOf(floor, wall), tol = 10f)
    assertEquals(-3f, r.offset.x, 0.01f)
    assertEquals(3f, r.offset.y, 0.01f)
    assertEquals(1, r.guides.count { it is SnapGuide.Touch })
  }

  @Test
  fun resizingALevelLineDoesntJumpToAnotherShapesHeight() {
    // A level line touching another line, resized from its left end: its height is nothing, so lining
    // its "bottom" up with the other line's would have thrown its length wildly.
    val other = LineShape(Pt(200f, 50f), Pt(200f, 150f)).snapFeatures()
    val box = Rect(0f, 100f, 200f, 100f)
    val (s, _) = Snap.scale(box, Pt(0f, 98.8f), 1.3f, listOf(other), tol = 10f)
    assertEquals(1.3f, s, 0.001f)
  }

  @Test
  fun handwritingLinesUpItsMiddleOnly() {
    val box = rect(0f, 0f, 200f, 100f).snapFeatures()
    // Writing whose middle is 3 off the box's middle lines up; its own left edge 2 off the box's doesn't count.
    val writing = SnapFeatures.middleOf(Rect(2f, 300f, 196f, 340f))
    val r = Snap.move(writing, listOf(box), tol = 8f)
    assertEquals(1f, r.offset.x, 0.01f)
    assertEquals(0f, r.offset.y, 0.01f)
  }

  @Test
  fun aRectanglesSideDraggedPastTheOtherTurnsItOver() {
    val r = rect(0f, 0f, 100f, 50f)
    // The right side's dot (index 1) dragged to x = -40: the rectangle now runs from -40 to 0.
    val g = r.withHandle(1, Pt(-40f, 25f)) as PolyShape
    assertEquals(-40f, g.points.minOf { it.x }, 0.01f)
    assertEquals(0f, g.points.maxOf { it.x }, 0.01f)
    // The dot she's dragging is still under the pen.
    assertEquals(-40f, g.handles()[1].x, 0.01f)
  }

  @Test
  fun aDistributedLoadGainsArrowsAsItLengthens() {
    val load = ShapeKind.DISTRIBUTED_LOAD.create(Pt(0f, 0f), Pt(112f, 40f)) as com.tali.notes.model.LoadShape
    assertEquals(4, load.count)
    // The right end dragged out to 224: twice as many gaps, same spacing; arrows still hang down.
    val longer = load.withHandle(1, Pt(224f, 0f)) as com.tali.notes.model.LoadShape
    assertEquals(8, longer.count)
    assertEquals(40f, longer.handles()[2].y, 0.01f)
    // Old loads (a group of a line and arrows) load as one.
    val old = GroupShape(listOf(LineShape(Pt(0f, 0f), Pt(90f, 0f))) + (0..3).map { i -> LineShape(Pt(i * 30f, 0f), Pt(i * 30f, 40f), arrowEnd = true) })
    assertEquals(com.tali.notes.model.LoadShape(Pt(0f, 0f), Pt(90f, 0f), 40f, 3), old.upgraded())
  }

  @Test
  fun dimensionIsALineWithMarks() {
    val g = ShapeKind.DIMENSION.create(Pt(0f, 0f), Pt(100f, 0f))
    assertTrue(g is LineShape && g.ticks && g.arrowStart && g.arrowEnd)
    // Dots at its two ends, like any line.
    assertEquals(2, g.handles().size)
    // Old ones (three loose lines) load as the same dimension line.
    val old = GroupShape(listOf(LineShape(Pt(0f, 0f), Pt(100f, 0f), arrowStart = true, arrowEnd = true), LineShape(Pt(0f, -8f), Pt(0f, 8f)), LineShape(Pt(100f, -8f), Pt(100f, 8f))))
    assertEquals(LineShape(Pt(0f, 0f), Pt(100f, 0f), arrowStart = true, arrowEnd = true, ticks = true), old.upgraded())
  }
}
