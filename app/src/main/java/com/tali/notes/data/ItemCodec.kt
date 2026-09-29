package com.tali.notes.data

import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.Brushes
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.ImageItem
import com.tali.notes.model.InkItem
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeGeometry
import com.tali.notes.model.ShapeItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Converts canvas items to storage rows and back. Ink keeps its raw pen samples, so nothing is lost. */
object ItemCodec {
  const val KIND_INK = 0
  const val KIND_SHAPE = 1
  const val KIND_IMAGE = 2

  val json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
  }

  @Serializable private class ImagePayload(val file: String, val rect: Rect, val pdfPage: Boolean = false)

  fun encode(item: CanvasItem): ItemRow {
    val b = item.bounds
    return when (item) {
      is InkItem -> {
        val bytes = ByteArrayOutputStream().also { item.stroke.inputs.encode(it) }.toByteArray()
        ItemRow(item.id, item.z, KIND_INK, item.pen.name, item.color, item.stroke.brush.size, bytes, b.left, b.top, b.right, b.bottom)
      }
      is ShapeItem -> {
        val bytes = json.encodeToString(ShapeGeometry.serializer(), item.geometry).encodeToByteArray()
        ItemRow(item.id, item.z, KIND_SHAPE, item.pen.name, item.color, item.size, bytes, b.left, b.top, b.right, b.bottom)
      }
      is ImageItem -> {
        val bytes = json.encodeToString(ImagePayload.serializer(), ImagePayload(item.file, item.rect, item.pdfPage)).encodeToByteArray()
        ItemRow(item.id, item.z, KIND_IMAGE, null, 0, 0f, bytes, b.left, b.top, b.right, b.bottom)
      }
    }
  }

  /** Returns null for rows written by a newer version that this one can't read. */
  fun decode(row: ItemRow): CanvasItem? =
    runCatching {
        when (row.kind) {
          KIND_INK -> {
            val pen = PenType.valueOf(row.pen!!)
            // Built on first use; see InkItem.
            InkItem.deferred(row.id, row.z, pen, Rect(row.l, row.t, row.r, row.b), row.color) {
              val brush = Brushes.brush(pen, row.color, row.size)
              // A damaged stroke becomes an empty one rather than breaking every redraw.
              runCatching { Stroke(brush, StrokeInputBatch.decode(ByteArrayInputStream(row.payload))) }.getOrElse { Stroke(brush, MutableStrokeInputBatch()) }
            }
          }
          KIND_SHAPE -> {
            val geometry = json.decodeFromString(ShapeGeometry.serializer(), row.payload.decodeToString())
            ShapeItem(row.id, row.z, PenType.valueOf(row.pen!!), row.color, row.size, geometry, Rect(row.l, row.t, row.r, row.b))
          }
          KIND_IMAGE -> {
            val p = json.decodeFromString(ImagePayload.serializer(), row.payload.decodeToString())
            ImageItem(row.id, row.z, p.file, p.rect, p.pdfPage)
          }
          else -> null
        }
      }
      .getOrNull()
}
