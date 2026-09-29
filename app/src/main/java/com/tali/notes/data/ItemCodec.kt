package com.tali.notes.data

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

  @Serializable private class ImagePayload(val file: String, val rect: Rect)

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
        val bytes = json.encodeToString(ImagePayload.serializer(), ImagePayload(item.file, item.rect)).encodeToByteArray()
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
            val inputs = StrokeInputBatch.decode(ByteArrayInputStream(row.payload))
            InkItem(row.id, row.z, pen, Stroke(Brushes.brush(pen, row.color, row.size), inputs))
          }
          KIND_SHAPE -> {
            val geometry = json.decodeFromString(ShapeGeometry.serializer(), row.payload.decodeToString())
            ShapeItem(row.id, row.z, PenType.valueOf(row.pen!!), row.color, row.size, geometry)
          }
          KIND_IMAGE -> {
            val p = json.decodeFromString(ImagePayload.serializer(), row.payload.decodeToString())
            ImageItem(row.id, row.z, p.file, p.rect)
          }
          else -> null
        }
      }
      .getOrNull()
}
