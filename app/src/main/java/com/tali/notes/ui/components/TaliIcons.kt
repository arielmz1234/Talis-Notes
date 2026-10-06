package com.tali.notes.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Outline icons Material doesn't have, drawn on the same 24 dp grid with a matching stroke. */
object TaliIcons {
  private fun outline(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
      .apply {
        paths.forEach { d ->
          addPath(
            pathData = addPathNodes(d),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
          )
        }
      }
      .build()

  /** Short round dashes along an ellipse, as path data: a dotted loop drawn with the icon's stroke. */
  private fun dottedEllipse(cx: Float, cy: Float, rx: Float, ry: Float, dashes: Int, start: Float = 0f): String = buildString {
    val step = (2 * Math.PI / dashes).toFloat()
    for (i in 0 until dashes) {
      val t0 = start + i * step
      val pts = (0..2).map { k -> t0 + step * 0.42f * k / 2f }.map { t -> (cx + rx * kotlin.math.cos(t)) to (cy + ry * kotlin.math.sin(t)) }
      append("M${pts[0].first} ${pts[0].second} L${pts[1].first} ${pts[1].second} L${pts[2].first} ${pts[2].second} ")
    }
  }

  /** The lasso (loop) selection: the lasso drawn dotted, with its tail. */
  val LassoDotted: ImageVector by lazy {
    outline("Lasso", dottedEllipse(12f, 9.3f, 8.2f, 5.5f, 12), "M8.2 14.2 C6.9 15.4 7.2 17.1 8.8 17.5 C10.2 17.9 10.4 19.2 9.4 20.4")
  }

  /** Free-form selection (trace over or touch things): a dotted circle. */
  val FreeSelect: ImageVector by lazy {
    outline("Free form", dottedEllipse(12f, 12f, 8.4f, 8.4f, 12, 0.13f))
  }

  /** A filled half and its outlined reflection, either side of a gap. */
  private fun mirror(name: String, filled: String, outlined: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
      .apply {
        addPath(pathData = addPathNodes(filled), fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineJoin = StrokeJoin.Round)
        addPath(pathData = addPathNodes(outlined), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.6f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
      }
      .build()

  /** Mirror left to right: a tall wedge filled on the left, its reflection outlined on the right. */
  val MirrorHorizontal: ImageVector by lazy {
    mirror("Mirror left to right", "M10.4 4.6 L10.4 19.4 L4.2 16.6 L5.6 7.2 Z", "M13.6 4.6 L13.6 19.4 L19.8 16.6 L18.4 7.2 Z")
  }

  /** Mirror upside down: a wide wedge filled on top, its reflection outlined below. */
  val MirrorVertical: ImageVector by lazy {
    mirror("Mirror upside down", "M4.6 10.4 L19.4 10.4 L16.6 4.2 L7.2 5.6 Z", "M4.6 13.6 L19.4 13.6 L16.6 19.8 L7.2 18.4 Z")
  }

  val Eraser: ImageVector by lazy {
    outline(
      "Eraser",
      "M13.4 4.2 L20.3 11.1 L11.6 19.8 L7.2 19.8 L3.6 16.2 Z",
      "M8.6 9 L15.6 16",
      "M14.5 19.8 L20.5 19.8",
    )
  }

  val Lasso: ImageVector by lazy {
    outline(
      "Lasso",
      "M12 3.8 C7.3 3.8 3.8 6.3 3.8 9.3 C3.8 12.3 7.3 14.8 12 14.8 C16.7 14.8 20.2 12.3 20.2 9.3 C20.2 6.3 16.7 3.8 12 3.8 Z",
      "M8.2 14.2 C6.9 15.4 7.2 17.1 8.8 17.5 C10.2 17.9 10.4 19.2 9.4 20.4",
    )
  }

  /** A laser pointer: a slim pen body aimed down-left, with a beam to a small dot that glows. */
  val Laser: ImageVector by lazy {
    outline(
      "Laser",
      "M20.2 3.8 L13.4 10.6",
      "M21.4 5 L14.6 11.8 L12.2 9.4 L19 2.6 Z",
      "M10.6 13.4 L7.4 16.6",
      "M5.6 20.4 A1.9 1.9 0 1 1 5.6 16.6 A1.9 1.9 0 1 1 5.6 20.4 Z",
      "M3 15.2 L2.2 14.4",
      "M8.6 21 L9.4 21.8",
    )
  }

  /**
   * Quick open (recent notebooks): a notebook with its spine and a line of writing, and a rewind
   * clock badge over its lower-right corner. The notebook's outline stops short of the clock, so
   * the badge sits in front of it with a little air around it.
   */
  val RecentNotebook: ImageVector by lazy {
    outline(
      "Recent notebooks",
      "M10.4 19 H5 A1.5 1.5 0 0 1 3.5 17.5 V4 A1.5 1.5 0 0 1 5 2.5 H13 A1.5 1.5 0 0 1 14.5 4 V10.9",
      "M6.6 2.5 V19",
      "M9 6.6 H11.9",
      "M9 9.6 H11",
      "M12.47 14.89 A5 5 0 1 1 12.47 19.11",
      "M14.35 14.21 L12.47 14.89 L11.79 13.01",
      "M17 14.6 V17 L18.7 18.1",
    )
  }

  val Pages: ImageVector by lazy {
    outline("Pages", "M6 3.5 H14.5 L18.5 7.5 V20.5 H6 Z", "M14.5 3.5 V7.5 H18.5", "M9 12 H15.5", "M9 15.5 H15.5")
  }
}
