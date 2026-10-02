package com.tali.notes.ui.notebook

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Category
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tali.notes.data.AppSettings
import com.tali.notes.model.PenSlot
import com.tali.notes.model.CustomShape
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.Tool
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.CloudCrown
import com.tali.notes.ui.components.FloatingBar
import com.tali.notes.ui.components.GeometryIcon
import com.tali.notes.ui.components.PenGlyph
import com.tali.notes.ui.components.ShapeIcon
import com.tali.notes.ui.components.TaliIcons
import com.tali.notes.ui.components.ToolButton

@Composable
fun MainToolbar(
  settings: AppSettings,
  darkPaper: Boolean,
  tool: Tool,
  shapeKind: ShapeKind,
  customShape: CustomShape?,
  onPen: (PenSlot) -> Unit,
  penSettingsOpen: Boolean,
  onPenSettings: () -> Unit,
  onTool: (Tool) -> Unit,
  onImage: () -> Unit,
  onCamera: () -> Unit,
  onPdf: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Tali.colors
  FloatingBar(modifier.height(60.dp), crown = CloudCrown.EDGE) {
    // The pen tray: pens stand at the bottom edge, the active one lifted.
    Row(Modifier.height(56.dp).clipToBounds().padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
      settings.pens.forEach { slot ->
        val active = tool == Tool.PEN && slot.id == settings.activePen
        val lift by animateDpAsState(if (active) 2.dp else 16.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "lift")
        Box(
          Modifier.width(34.dp)
            .height(56.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = slot.type.label) { onPen(slot) },
          contentAlignment = Alignment.TopCenter,
        ) {
          // On dark paper the pens show the colour they really write in there.
          PenGlyph(slot.type, Color(if (darkPaper) com.tali.notes.ink.Brushes.onDarkPaper(slot.color) else slot.color), Modifier.offset(y = lift))
        }
      }
    }
    // The pen's settings (style, size, colour) open from the little arrow.
    val turn by animateFloatAsState(if (penSettingsOpen) 180f else 0f, label = "turn")
    Box(
      Modifier.size(28.dp, 44.dp).clip(RoundedCornerShape(10.dp)).background(if (penSettingsOpen) c.accentSoft else Color.Transparent).clickable(onClickLabel = "Pen settings", role = Role.Button, onClick = onPenSettings),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Outlined.KeyboardArrowDown, "Pen settings", tint = if (penSettingsOpen) c.accent else c.inkMuted, modifier = Modifier.size(22.dp).rotate(turn))
    }
    Divider()
    ToolButton(TaliIcons.Eraser, "Eraser", selected = tool == Tool.ERASER, blue = true) { onTool(Tool.ERASER) }
    ToolButton(TaliIcons.Lasso, "Lasso", selected = tool == Tool.LASSO, blue = true) { onTool(Tool.LASSO) }
    Box(contentAlignment = Alignment.Center) {
      ToolButton(Icons.Outlined.Category, "Shapes", selected = tool == Tool.SHAPE, blue = true) { onTool(Tool.SHAPE) }
      if (tool == Tool.SHAPE) {
        // Show which shape the shape tool will draw.
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.blueSoft).clickable { onTool(Tool.SHAPE) }, contentAlignment = Alignment.Center) {
          if (customShape != null) GeometryIcon(customShape.geometry, c.blue, size = 26.dp) else ShapeIcon(shapeKind, c.blue, size = 26.dp)
        }
      }
    }
    Box {
      var inserting by remember { mutableStateOf(false) }
      ToolButton(Icons.Outlined.AddPhotoAlternate, "Insert") { inserting = true }
      DropdownMenu(expanded = inserting, onDismissRequest = { inserting = false }, shape = RoundedCornerShape(16.dp), containerColor = c.surface) {
        DropdownMenuItem(
          text = { Column { Text("Picture"); Text("From your photos or files", style = MaterialTheme.typography.bodySmall, color = c.inkFaint) } },
          leadingIcon = { Icon(Icons.Outlined.Image, null, tint = c.blue) },
          onClick = { inserting = false; onImage() },
        )
        DropdownMenuItem(
          text = { Column { Text("Take a photo"); Text("The board, a textbook page, a sketch", style = MaterialTheme.typography.bodySmall, color = c.inkFaint) } },
          leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null, tint = c.blue) },
          onClick = { inserting = false; onCamera() },
        )
        DropdownMenuItem(
          text = { Column { Text("PDF pages"); Text("Lecture slides or a problem sheet to write on", style = MaterialTheme.typography.bodySmall, color = c.inkFaint) } },
          leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null, tint = c.blue) },
          onClick = { inserting = false; onPdf() },
        )
      }
    }
    Spacer(Modifier.width(2.dp))
  }
}

@Composable
fun UndoRedoBar(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, trailing: @Composable () -> Unit) {
  FloatingBar(Modifier.height(52.dp), crown = CloudCrown.SOFT_EDGE) {
    ToolButton(Icons.AutoMirrored.Outlined.Undo, "Undo", enabled = canUndo, onClick = onUndo)
    ToolButton(Icons.AutoMirrored.Outlined.Redo, "Redo", enabled = canRedo, onClick = onRedo)
    Divider()
    trailing()
  }
}

@Composable
private fun Divider() {
  Box(Modifier.padding(horizontal = 6.dp).width(1.dp).height(28.dp).background(Tali.colors.line))
}

@Composable
fun ColorChip(color: Color, onClick: () -> Unit) {
  Box(Modifier.size(28.dp).clip(CircleShape).background(color).clickable(onClick = onClick))
}
