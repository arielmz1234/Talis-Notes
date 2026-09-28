package com.tali.notes.canvas

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RenderNode
import com.tali.notes.geometry.Rect
import kotlin.math.floor

/**
 * Splits the infinite canvas into square tiles, each recorded once into a GPU display list.
 * Panning and zooming just replay the lists; only tiles touched by an edit are re-recorded.
 * This keeps big notebooks smooth on the Tab S6 Lite.
 */
class TileCache(private val renderer: ItemRenderer) {
  private class Tile(val node: RenderNode, var dirty: Boolean = true, var lastUsed: Long = 0, var recordedScale: Float = 0f)

  private val tiles = HashMap<Long, Tile>()
  private var frame = 0L
  private val hint = Matrix()

  /** Items hidden from tiles while they're being dragged or edited (drawn live on top instead). */
  var hidden: Set<String> = emptySet()
    set(value) {
      if (field == value) return
      val changed = (field - value) + (value - field)
      field = value
      changed.forEach { id -> boundsOf(id)?.let(::invalidate) }
    }

  var boundsOf: (String) -> Rect? = { null }

  fun invalidate(worldRect: Rect) {
    forEachKey(worldRect.outset(2f)) { key -> tiles[key]?.dirty = true }
  }

  fun invalidateAll() = tiles.values.forEach { it.dirty = true }

  /** Draws visible tiles. The canvas must already be transformed to world coordinates. */
  fun draw(canvas: Canvas, visible: Rect, scale: Float, doc: Document) {
    frame++
    forEachKey(visible) { key ->
      val tile = tiles.getOrPut(key) { Tile(RenderNode("tile")) }
      // Re-record after big zoom changes so textures and tessellation stay crisp.
      val ratio = if (tile.recordedScale == 0f) 0f else scale / tile.recordedScale
      if (tile.dirty || ratio > 2.5f || (ratio in 0.01f..0.3f)) record(tile, key, scale, doc)
      tile.lastUsed = frame
      canvas.drawRenderNode(tile.node)
    }
    if (tiles.size > MAX_TILES) evict()
  }

  private fun record(tile: Tile, key: Long, scale: Float, doc: Document) {
    val tx = (key shr 32).toInt()
    val ty = key.toInt()
    val left = tx * SIZE
    val top = ty * SIZE
    val rect = Rect(left.toFloat(), top.toFloat(), (left + SIZE).toFloat(), (top + SIZE).toFloat())
    tile.node.setPosition(left, top, left + SIZE, top + SIZE)
    tile.node.clipToBounds = true
    val c = tile.node.beginRecording()
    try {
      c.translate(-left.toFloat(), -top.toFloat())
      hint.setScale(scale, scale)
      renderer.drawAll(c, doc.intersecting(rect), hint, hidden)
    } finally {
      tile.node.endRecording()
    }
    tile.dirty = false
    tile.recordedScale = scale
  }

  private fun evict() {
    tiles.entries.sortedBy { it.value.lastUsed }.take(tiles.size - MAX_TILES / 2).forEach {
      it.value.node.discardDisplayList()
      tiles.remove(it.key)
    }
  }

  private inline fun forEachKey(r: Rect, action: (Long) -> Unit) {
    val x0 = floor(r.left / SIZE).toInt()
    val x1 = floor(r.right / SIZE).toInt()
    val y0 = floor(r.top / SIZE).toInt()
    val y1 = floor(r.bottom / SIZE).toInt()
    for (ty in y0..y1) for (tx in x0..x1) action((tx.toLong() shl 32) or (ty.toLong() and 0xFFFFFFFFL))
  }

  companion object {
    /** Tile side in world units. */
    const val SIZE = 768
    private const val MAX_TILES = 160
  }
}
