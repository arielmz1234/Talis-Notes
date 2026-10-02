package com.tali.notes.data

import android.content.Context
import com.tali.notes.canvas.PaperRenderer
import com.tali.notes.model.CustomPaper
import com.tali.notes.model.CustomShape
import com.tali.notes.model.EraserMode
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenSlot
import com.tali.notes.model.PenType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Curated starting palette: inks first, then accents, then highlighter tones. */
val DEFAULT_COLORS =
  listOf(
    0xFF1B1F2A, // ink black
    0xFF4A5160, // graphite
    0xFF1F5FD1, // blueprint blue
    0xFFD83A3A, // red
    0xFF1E9E6A, // green
    0xFFF08A24, // orange
    0xFF7A4FD8, // violet
    0xFFFFD60A, // highlighter yellow
    0xFFFF6FA8, // pink
    0xFF9B6BF2, // lilac
    0xFF5AA9E6, // sky blue
  ).map { it.toInt() }

@Serializable
data class AppSettings(
  val pens: List<PenSlot> =
    listOf(
      PenSlot("p1", PenType.FINELINER, DEFAULT_COLORS[0], 2.4f),
      PenSlot("p2", PenType.FOUNTAIN, DEFAULT_COLORS[2], 3f),
      PenSlot("p3", PenType.PENCIL, DEFAULT_COLORS[1], 2.6f),
      PenSlot("p4", PenType.FINELINER, DEFAULT_COLORS[3], 2.4f),
      PenSlot("p5", PenType.HIGHLIGHTER, DEFAULT_COLORS[7], 18f),
    ),
  val activePen: String = "p1",
  val savedColors: List<Int> = DEFAULT_COLORS,
  /** Which pen styles the eraser can erase. Missing entries mean yes. */
  val eraserAffects: Map<PenType, Boolean> = emptyMap(),
  /** Which pen styles the lasso can pick up. Missing entries mean yes. */
  val lassoAffects: Map<PenType, Boolean> = emptyMap(),
  val lassoSelectsImages: Boolean = true,
  val eraserMode: EraserMode = EraserMode.STROKE,
  val eraserSize: Float = 16f,
  val holdToShape: Boolean = true,
  val holdDelayMs: Long = 450L,
  /** null means automatic: fingers draw only on devices without a stylus. */
  val fingerDraws: Boolean? = null,
  val defaultPaper: PaperStyle = PaperStyle.DOTS,
  val lastShape: String = "LINE",
  /** Shapes she saved from her own drawings; hold-to-shape recognises them too. */
  val customShapes: List<CustomShape> = emptyList(),
  /** Paper backgrounds she made (pictures or designed patterns), offered in every notebook. */
  val customPapers: List<CustomPaper> = emptyList(),
  /** The paper new notebooks start with when it's one of hers, otherwise [defaultPaper]. */
  val defaultCustomPaper: String? = null,
  /** New notebooks start as A4 pages unless she last chose an endless canvas. */
  val defaultPaged: Boolean = true,
  /** New paged notebooks start with the orientation she last chose. */
  val defaultLandscape: Boolean = false,
  /** The fill tool's colour, or null to take fills away. */
  val fillColor: Int? = 0xFF9DB8F5.toInt(),
  /** How strongly fills cover the paper, 0.15..1. */
  val fillOpacity: Float = 0.7f,
  /** Faint A4 page edges on the canvas; A4 export then follows exactly these pages. */
  val pageGuides: Boolean = false,
  /** Smart undo and redo: a quick two-finger tap undoes, a three-finger tap redoes. */
  val smartUndo: Boolean = true,
  /** How heavy the paper's lines, grid and dots are drawn: 1 is the standard weight, 0.5..3. */
  val paperLineWeight: Float = 1f,
  /** One-time tips she has already seen. */
  val seenTips: Set<String> = emptySet(),
) {
  /** The old Dashed pen became a fineliner drawing short dashes, now that any pen can dash. */
  fun migrated(): AppSettings =
    if (pens.none { it.type == PenType.DASHED }) this
    else copy(pens = pens.map { if (it.type == PenType.DASHED) it.copy(type = PenType.FINELINER, line = LineStyle.SHORT_DASH) else it })

  /** The fill colour with its opacity applied, or null when the fill tool takes fills away. */
  val fillArgb: Int?
    get() = fillColor?.let { c -> ((255 * fillOpacity.coerceIn(0.15f, 1f)).toInt() shl 24) or (c and 0x00FFFFFF) }

  fun eraserHits(pen: PenType?) = pen != null && eraserAffects[pen] != false

  fun lassoHits(pen: PenType?) = if (pen == null) lassoSelectsImages else lassoAffects[pen] != false

  val activeSlot: PenSlot
    get() = pens.firstOrNull { it.id == activePen } ?: pens.first()

  /** The pen the shape tool draws with: the active pen, unless that's a highlighter, then the first writing pen. */
  val shapeSlot: PenSlot
    get() = activeSlot.takeIf { it.type != PenType.HIGHLIGHTER } ?: pens.firstOrNull { it.type != PenType.HIGHLIGHTER } ?: activeSlot
}

class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val _settings = MutableStateFlow(load().also { PaperRenderer.lineWeight = it.paperLineWeight })
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private fun load(): AppSettings =
    prefs.getString("json", null)?.let { runCatching { ItemCodec.json.decodeFromString(AppSettings.serializer(), it).migrated() }.getOrNull() } ?: AppSettings()

  fun update(transform: (AppSettings) -> AppSettings) {
    val next = transform(_settings.value)
    if (next == _settings.value) return
    _settings.value = next
    PaperRenderer.lineWeight = next.paperLineWeight
    prefs.edit().putString("json", ItemCodec.json.encodeToString(AppSettings.serializer(), next)).apply()
  }

  fun exportJson(): String = ItemCodec.json.encodeToString(AppSettings.serializer(), _settings.value)

  fun importJson(json: String) {
    runCatching { ItemCodec.json.decodeFromString(AppSettings.serializer(), json).migrated() }.getOrNull()?.let { s -> update { s } }
  }
}
