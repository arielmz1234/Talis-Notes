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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tali.notes.data.AppSettings
import com.tali.notes.model.PenSlot
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.Tool
import com.tali.notes.theme.Tali
import com.tali.notes.ui.components.FloatingBar
import com.tali.notes.ui.components.PenGlyph
import com.tali.notes.ui.components.ShapeIcon
import com.tali.notes.ui.components.TaliIcons
import com.tali.notes.ui.components.ToolButton

@Composable
fun MainToolbar(
  settings: AppSettings,
  tool: Tool,
  shapeKind: ShapeKind,
  onPen: (PenSlot) -> Unit,
  onTool: (Tool) -> Unit,
  onImage: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val c = Tali.colors
  FloatingBar(modifier.height(60.dp)) {
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
          PenGlyph(slot.type, Color(slot.color), Modifier.offset(y = lift))
        }
      }
    }
    Divider()
    ToolButton(TaliIcons.Eraser, "Eraser", selected = tool == Tool.ERASER) { onTool(Tool.ERASER) }
    ToolButton(TaliIcons.Lasso, "Lasso", selected = tool == Tool.LASSO) { onTool(Tool.LASSO) }
    Box(contentAlignment = Alignment.Center) {
      ToolButton(Icons.Outlined.Category, "Shapes", selected = tool == Tool.SHAPE) { onTool(Tool.SHAPE) }
      if (tool == Tool.SHAPE) {
        // Show which shape the shape tool will draw.
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.accentSoft).clickable { onTool(Tool.SHAPE) }, contentAlignment = Alignment.Center) {
          ShapeIcon(shapeKind, c.accent, size = 26.dp)
        }
      }
    }
    ToolButton(Icons.Outlined.AddPhotoAlternate, "Insert image", onClick = onImage)
    Spacer(Modifier.width(2.dp))
  }
}

@Composable
fun UndoRedoBar(canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, trailing: @Composable () -> Unit) {
  FloatingBar(Modifier.height(52.dp)) {
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
