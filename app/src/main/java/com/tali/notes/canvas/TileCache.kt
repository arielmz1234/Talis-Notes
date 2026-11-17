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
  private class Tile(val node: RenderNode, var dirty: Boolean = true, var lastUsed: Long = 0, var recordedScale: Float = 0f, var waiting: Boolean = false)

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
      onHidden(value)
    }

  var boundsOf: (String) -> Rect? = { null }

  /** Told which items were just hidden (picked up to be moved or edited). */
  var onHidden: (Set<String>) -> Unit = {}

  fun invalidate(worldRect: Rect) {
    forEachKey(worldRect.outset(2f)) { key -> tiles[key]?.dirty = true }
  }

  fun invalidateAll() = tiles.values.forEach { it.dirty = true }

  /** Re-records tiles that left out strokes still being built. Returns whether there were any. */
  fun refreshWaiting(): Boolean {
    var any = false
    tiles.values.forEach {
      if (it.waiting) {
        it.dirty = true
        any = true
      }
    }
    return any
  }

  /**
   * Once the view settles, tiles in view recorded at a noticeably different zoom are recorded
   * again. Ink sizes a stroke's soft edge by the zoom it was recorded at, so zoomed in it looked
   * blurry until she zoomed or scrolled further, and zoomed out a thin line could drop out.
   * Returns whether any were.
   */
  fun refreshForScale(visible: Rect, scale: Float): Boolean {
    var any = false
    forEachKey(visible) { key ->
      val t = tiles[key] ?: return@forEachKey
      if (t.recordedScale == 0f || t.dirty) return@forEachKey
      val ratio = scale / t.recordedScale
      if (ratio > 1.1f || ratio < 0.9f) {
        t.dirty = true
        any = true
      }
    }
    return any
  }

  /** Draws visible tiles. The canvas must already be transformed to world coordinates. */
  fun draw(canvas: Canvas, visible: Rect, scale: Float, doc: Document) {
    frame++
    forEachKey(visible) { key ->
      val tile = tiles.getOrPut(key) { Tile(RenderNode("tile")) }
      // Re-record after big zoom changes so textures and tessellation stay crisp, and when the
      // renderer dropped the recording (it does that to tiles that went off screen for a while).
      val ratio = if (tile.recordedScale == 0f) 0f else scale / tile.recordedScale
      if (tile.dirty || !tile.node.hasDisplayList() || ratio > 2.5f || (ratio in 0.01f..0.3f)) record(tile, key, scale, doc)
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
      tile.waiting = renderer.drawAll(c, doc.intersecting(rect), hint, hidden, deferUnbuilt = true)
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
