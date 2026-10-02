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
import com.tali.notes.model.LineStyle
import com.tali.notes.model.ShapeGeometry
import com.tali.notes.model.ShapeItem
import com.tali.notes.model.TextItem
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.int

/** Converts canvas items to storage rows and back. Ink keeps its raw pen samples, so nothing is lost. */
object ItemCodec {
  const val KIND_INK = 0
  const val KIND_SHAPE = 1
  const val KIND_IMAGE = 2
  const val KIND_TEXT = 3

  val json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
  }

  @Serializable private class ImagePayload(val file: String, val rect: Rect, val pdfPage: Boolean = false, val front: Boolean = false)

  @Serializable private class TextPayload(val text: String, val x: Float, val y: Float, val width: Float)

  fun encode(item: CanvasItem): ItemRow {
    val b = item.bounds
    return when (item) {
      is InkItem -> {
        val bytes = ByteArrayOutputStream().also { item.stroke.inputs.encode(it) }.toByteArray()
        // A dotted or dashed stroke is stored as "PEN/STYLE" in the pen column.
        val pen = item.line.let { if (it == LineStyle.SOLID) item.pen.name else item.pen.name + "/" + it.name }
        ItemRow(item.id, item.z, KIND_INK, pen, item.color, item.stroke.brush.size, bytes, b.left, b.top, b.right, b.bottom)
      }
      is ShapeItem -> {
        // The geometry, plus "fill" when it's filled and "line" when dotted or dashed (older versions ignore extra keys).
        var obj = json.encodeToJsonElement(ShapeGeometry.serializer(), item.geometry)
        item.fill?.let { f -> obj = kotlinx.serialization.json.JsonObject(obj.jsonObject + ("fill" to kotlinx.serialization.json.JsonPrimitive(f))) }
        if (item.line != LineStyle.SOLID) obj = kotlinx.serialization.json.JsonObject(obj.jsonObject + ("line" to kotlinx.serialization.json.JsonPrimitive(item.line.name)))
        val bytes = obj.toString().encodeToByteArray()
        ItemRow(item.id, item.z, KIND_SHAPE, item.pen.name, item.color, item.size, bytes, b.left, b.top, b.right, b.bottom)
      }
      is ImageItem -> {
        val bytes = json.encodeToString(ImagePayload.serializer(), ImagePayload(item.file, item.rect, item.pdfPage, item.front)).encodeToByteArray()
        ItemRow(item.id, item.z, KIND_IMAGE, null, 0, 0f, bytes, b.left, b.top, b.right, b.bottom)
      }
      is TextItem -> {
        val bytes = json.encodeToString(TextPayload.serializer(), TextPayload(item.text, item.origin.x, item.origin.y, item.width)).encodeToByteArray()
        ItemRow(item.id, item.z, KIND_TEXT, null, item.color, item.fontSize, bytes, b.left, b.top, b.right, b.bottom)
      }
    }
  }

  /** Returns null for rows written by a newer version that this one can't read. */
  fun decode(row: ItemRow): CanvasItem? =
    runCatching {
        when (row.kind) {
          KIND_INK -> {
            val pen = PenType.valueOf(row.pen!!.substringBefore('/'))
            val line = row.pen.substringAfter('/', "").let { name -> LineStyle.entries.firstOrNull { it.name == name } ?: LineStyle.SOLID }
            // Built on first use; see InkItem.
            InkItem.deferred(row.id, row.z, pen, Rect(row.l, row.t, row.r, row.b), row.color) {
              val brush = Brushes.brush(pen, row.color, row.size, line)
              // A damaged stroke becomes an empty one rather than breaking every redraw.
              runCatching { Stroke(brush, StrokeInputBatch.decode(ByteArrayInputStream(row.payload))) }.getOrElse { Stroke(brush, MutableStrokeInputBatch()) }
            }
          }
          KIND_SHAPE -> {
            val text = row.payload.decodeToString()
            val geometry = json.decodeFromString(ShapeGeometry.serializer(), text)
            val extra = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val fill = runCatching { extra?.get("fill")?.jsonPrimitive?.int }.getOrNull()
            val line = runCatching { LineStyle.valueOf(extra?.get("line")!!.jsonPrimitive.content) }.getOrDefault(LineStyle.SOLID)
            ShapeItem(row.id, row.z, PenType.valueOf(row.pen!!), row.color, row.size, geometry, Rect(row.l, row.t, row.r, row.b), fill, line = line)
          }
          KIND_IMAGE -> {
            val p = json.decodeFromString(ImagePayload.serializer(), row.payload.decodeToString())
            ImageItem(row.id, row.z, p.file, p.rect, p.pdfPage, p.front)
          }
          KIND_TEXT -> {
            val p = json.decodeFromString(TextPayload.serializer(), row.payload.decodeToString())
            TextItem(row.id, row.z, p.text, com.tali.notes.geometry.Pt(p.x, p.y), p.width, row.size, row.color)
          }
          else -> null
        }
      }
      .getOrNull()
}
