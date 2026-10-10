package com.tali.notes.data

import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.Brushes
import com.tali.notes.model.AudioItem
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.ImageItem
import com.tali.notes.model.InkItem
import com.tali.notes.model.PenType
import com.tali.notes.model.LineStyle
import com.tali.notes.model.ShapeGeometry
import com.tali.notes.model.ShapeItem
import com.tali.notes.model.TextItem
import com.tali.notes.model.upgraded
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
  private const val SQUARE = "SQUARE"
  private const val LOCKED = "LOCKED"
  const val KIND_SHAPE = 1
  const val KIND_IMAGE = 2
  const val KIND_TEXT = 3
  const val KIND_AUDIO = 4

  val json = Json {
    ignoreUnknownKeys = true
    classDiscriminator = "type"
  }

  @Serializable private class ImagePayload(val file: String, val rect: Rect, val pdfPage: Boolean = false, val front: Boolean = false, val locked: Boolean = false, val walls: List<String> = emptyList(), val flipX: Boolean = false, val flipY: Boolean = false)

  @Serializable private class TextPayload(val text: String, val x: Float, val y: Float, val width: Float, val locked: Boolean = false, val height: Float = 0f)

  @Serializable private class AudioPayload(val file: String, val x: Float, val y: Float, val durationMs: Long, val wave: List<Int> = emptyList(), val created: Long = 0L, val locked: Boolean = false, val side: Int = AudioItem.RIGHT, val edge: Float = AudioItem.EDGE, val title: String = "")

  fun encode(item: CanvasItem): ItemRow {
    val b = item.bounds
    return when (item) {
      is InkItem -> {
        val bytes = ByteArrayOutputStream().also { item.stroke.inputs.encode(it) }.toByteArray()
        // A dotted or dashed stroke is stored as "PEN/STYLE" in the pen column, a square-tipped
        // highlighter as "HIGHLIGHTER/SQUARE".
        val tags = listOfNotNull(item.line.takeIf { it != LineStyle.SOLID }?.name, SQUARE.takeIf { item.square }, LOCKED.takeIf { item.locked })
        val pen = (listOf(item.pen.name) + tags).joinToString("/")
        ItemRow(item.id, item.z, KIND_INK, pen, item.color, item.stroke.brush.size, bytes, b.left, b.top, b.right, b.bottom)
      }
      is ShapeItem -> {
        // The geometry, plus "fill" when it's filled and "line" when dotted or dashed (older versions ignore extra keys).
        var obj = json.encodeToJsonElement(ShapeGeometry.serializer(), item.geometry)
        item.fill?.let { f -> obj = kotlinx.serialization.json.JsonObject(obj.jsonObject + ("fill" to kotlinx.serialization.json.JsonPrimitive(f))) }
        if (item.locked) obj = kotlinx.serialization.json.JsonObject(obj.jsonObject + ("locked" to kotlinx.serialization.json.JsonPrimitive(true)))
        if (item.line != LineStyle.SOLID) obj = kotlinx.serialization.json.JsonObject(obj.jsonObject + ("line" to kotlinx.serialization.json.JsonPrimitive(item.line.name)))
        val bytes = obj.toString().encodeToByteArray()
        ItemRow(item.id, item.z, KIND_SHAPE, item.pen.name, item.color, item.size, bytes, b.left, b.top, b.right, b.bottom)
      }
      is ImageItem -> {
        val bytes = json.encodeToString(ImagePayload.serializer(), ImagePayload(item.file, item.rect, item.pdfPage, item.front, item.locked, item.walls.toList(), item.flipX, item.flipY)).encodeToByteArray()
        ItemRow(item.id, item.z, KIND_IMAGE, null, 0, 0f, bytes, b.left, b.top, b.right, b.bottom)
      }
      is TextItem -> {
        val bytes = json.encodeToString(TextPayload.serializer(), TextPayload(item.text, item.origin.x, item.origin.y, item.width, item.locked, item.height)).encodeToByteArray()
        ItemRow(item.id, item.z, KIND_TEXT, null, item.color, item.fontSize, bytes, b.left, b.top, b.right, b.bottom)
      }
      is AudioItem -> {
        val bytes = json.encodeToString(AudioPayload.serializer(), AudioPayload(item.file, item.at.x, item.at.y, item.durationMs, item.wave, item.created, item.locked, item.side, item.edge, item.title)).encodeToByteArray()
        ItemRow(item.id, item.z, KIND_AUDIO, null, 0, 0f, bytes, b.left, b.top, b.right, b.bottom)
      }
    }
  }

  /** Returns null for rows written by a newer version that this one can't read. */
  fun decode(row: ItemRow): CanvasItem? =
    runCatching {
        when (row.kind) {
          KIND_INK -> {
            val parts = row.pen!!.split('/')
            val pen = PenType.valueOf(parts[0])
            val line = LineStyle.entries.firstOrNull { it.name in parts } ?: LineStyle.SOLID
            val square = SQUARE in parts
            // Built on first use; see InkItem.
            InkItem.deferred(row.id, row.z, pen, Rect(row.l, row.t, row.r, row.b), row.color, locked = LOCKED in parts) {
              val brush = Brushes.brush(pen, row.color, row.size, line, square)
              // A damaged stroke becomes an empty one rather than breaking every redraw.
              runCatching { Stroke(brush, StrokeInputBatch.decode(ByteArrayInputStream(row.payload))) }.getOrElse { Stroke(brush, MutableStrokeInputBatch()) }
            }
          }
          KIND_SHAPE -> {
            val text = row.payload.decodeToString()
            // Triangles saved before they had their own shape get movable corners too.
            // Dimension lines saved as three loose lines become proper dimension lines.
            val geometry = com.tali.notes.geometry.ShapeRecognizer.asTriangle(json.decodeFromString(ShapeGeometry.serializer(), text))!!.upgraded()
            val extra = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val fill = runCatching { extra?.get("fill")?.jsonPrimitive?.int }.getOrNull()
            val line = runCatching { LineStyle.valueOf(extra?.get("line")!!.jsonPrimitive.content) }.getOrDefault(LineStyle.SOLID)
            val locked = runCatching { extra?.get("locked")?.jsonPrimitive?.content == "true" }.getOrDefault(false)
            ShapeItem(row.id, row.z, PenType.valueOf(row.pen!!), row.color, row.size, geometry, Rect(row.l, row.t, row.r, row.b), fill, line = line, locked = locked)
          }
          KIND_IMAGE -> {
            val p = json.decodeFromString(ImagePayload.serializer(), row.payload.decodeToString())
            ImageItem(row.id, row.z, p.file, p.rect, p.pdfPage, p.front, p.locked, p.walls.toSet(), p.flipX, p.flipY)
          }
          KIND_TEXT -> {
            val p = json.decodeFromString(TextPayload.serializer(), row.payload.decodeToString())
            TextItem(row.id, row.z, p.text, com.tali.notes.geometry.Pt(p.x, p.y), p.width, row.size, row.color, p.locked, p.height)
          }
          KIND_AUDIO -> {
            val p = json.decodeFromString(AudioPayload.serializer(), row.payload.decodeToString())
            AudioItem(row.id, row.z, p.file, com.tali.notes.geometry.Pt(p.x, p.y), p.durationMs, p.wave, p.created, p.locked, p.side, p.edge, p.title)
          }
          else -> null
        }
      }
      .getOrNull()
}
