package com.tali.notes.canvas

import android.graphics.Canvas
import android.graphics.Paint

/** The padlock the canvas shows when something is locked or unlocked (drawn in screen pixels). */
object LockGlyph {
  private val lockChip = Paint(Paint.ANTI_ALIAS_FLAG)
  private val lockInk = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
  }
  private val lockPath = android.graphics.Path()

  /** A padlock centred at ([cx], [cy]); [open] 0 is shut, 1 is the shackle lifted and swung open. */
  fun draw(canvas: Canvas, density: Float, dark: Boolean, cx: Float, cy: Float, alpha: Float, scale: Float, open: Float) {
    if (alpha <= 0.01f) return
    // A little larger than a toolbar icon, so the lock is easy to see happen.
    val d = density * 1.3f
    canvas.save()
    canvas.translate(cx, cy)
    canvas.scale(scale, scale)
    lockChip.color = if (dark) 0xFF2E2848.toInt() else 0xFFFFFFFF.toInt()
    lockChip.alpha = (235 * alpha).toInt()
    lockChip.setShadowLayer(8f * d, 0f, 2f * d, if (dark) 0x55000000 else 0x333B2A7A)
    canvas.drawCircle(0f, 0f, 21f * d, lockChip)
    val ink = if (dark) 0xFFC8B4FF.toInt() else 0xFF7A4FD8.toInt()
    // The body.
    lockInk.style = Paint.Style.FILL
    lockInk.color = ink
    lockInk.alpha = (255 * alpha).toInt()
    canvas.drawRoundRect(-7.5f * d, -1.5f * d, 7.5f * d, 9.5f * d, 2.6f * d, 2.6f * d, lockInk)
    // The keyhole.
    lockInk.color = lockChip.color
    lockInk.alpha = (255 * alpha).toInt()
    canvas.drawCircle(0f, 3.4f * d, 1.5f * d, lockInk)
    // The shackle: lifts and swings round its right leg as it opens.
    lockInk.style = Paint.Style.STROKE
    lockInk.strokeWidth = 2.2f * d
    lockInk.color = ink
    lockInk.alpha = (255 * alpha).toInt()
    canvas.save()
    canvas.translate(0f, -3.5f * d * open)
    canvas.rotate(-28f * open, 4.6f * d, -1.5f * d)
    lockPath.rewind()
    lockPath.moveTo(-4.6f * d, -1.2f * d)
    lockPath.lineTo(-4.6f * d, -4.5f * d)
    lockPath.arcTo(-4.6f * d, -9.1f * d, 4.6f * d, 0.1f * d, 180f, 180f, false)
    lockPath.lineTo(4.6f * d, -1.2f * d)
    canvas.drawPath(lockPath, lockInk)
    canvas.restore()
    canvas.restore()
  }
}
