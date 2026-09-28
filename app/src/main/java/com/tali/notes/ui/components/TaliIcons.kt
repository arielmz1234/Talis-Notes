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

  val Pages: ImageVector by lazy {
    outline("Pages", "M6 3.5 H14.5 L18.5 7.5 V20.5 H6 Z", "M14.5 3.5 V7.5 H18.5", "M9 12 H15.5", "M9 15.5 H15.5")
  }
}
