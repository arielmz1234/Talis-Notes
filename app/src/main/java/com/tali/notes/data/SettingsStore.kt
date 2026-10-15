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

/** The highlighter's own starting palette: bright, see-through-friendly tones. */
val DEFAULT_HIGHLIGHTER_COLORS =
  listOf(
    0xFFFFD60A, // yellow
    0xFFFF6FA8, // pink
    0xFF5AA9E6, // sky blue
    0xFF6EE07A, // green
    0xFFFFA24D, // orange
    0xFFB28DFF, // lilac
  ).map { it.toInt() }

/** How the library orders notebooks. The first way is newest first (A to Z for names). */
@Serializable
enum class LibrarySort(val label: String) {
  EDITED("Last edited"),
  OPENED("Last opened"),
  CREATED("Date created"),
  NAME("Name"),
}

/** Light or dark: following the tablet, or always one. */
@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(
  val pens: List<PenSlot> =
    listOf(
      PenSlot("p1", PenType.FINELINER, DEFAULT_COLORS[0], 2.4f),
      PenSlot("p2", PenType.FOUNTAIN, DEFAULT_COLORS[2], 3f),
      PenSlot("p4", PenType.FINELINER, DEFAULT_COLORS[3], 2.4f),
      PenSlot("p5", PenType.HIGHLIGHTER, DEFAULT_COLORS[7], 18f),
    ),
  val activePen: String = "p1",
  val savedColors: List<Int> = DEFAULT_COLORS,
  /** The highlighter's palette, kept apart from the pens' (same "first few in the toolbar" rule). */
  val highlighterColors: List<Int> = DEFAULT_HIGHLIGHTER_COLORS,
  /** Which pen styles the eraser can erase. Missing entries mean yes. */
  val eraserAffects: Map<PenType, Boolean> = emptyMap(),
  /** Which pen styles the lasso can pick up. Missing entries mean yes. */
  val lassoAffects: Map<PenType, Boolean> = emptyMap(),
  val lassoSelectsImages: Boolean = true,
  /** Free-form selecting: whatever the lasso tool's path passes over or touches is picked (circling still works). Off, it's the classic lasso loop. */
  val lassoFree: Boolean = false,
  /** The screenshot lasso: drawing around something takes a picture of it (to save or share) instead of picking it up. */
  val lassoShot: Boolean = false,
  /** The lasso also picks up locked items (to move or unlock them). */
  val lassoLocked: Boolean = false,
  val eraserMode: EraserMode = EraserMode.STROKE,
  val eraserSize: Float = 16f,
  val holdToShape: Boolean = true,
  /** Shapes, pictures and text lock onto each other when moved or drawn close (ends, edges, lining up). */
  val snapShapes: Boolean = true,
  /** The Shapes tool's own colour, thickness, line and fill; null until she first sets it (then it starts from her pen). */
  val shapeStyle: com.tali.notes.model.ShapeStyle? = null,
  /** The Shapes tool takes its colour, size and line from the pen in use instead of its own style. */
  val shapeFollowsPen: Boolean = false,
  val holdDelayMs: Long = 450L,
  /** null means automatic: fingers draw only on devices without a stylus. */
  val fingerDraws: Boolean? = null,
  val defaultPaper: PaperStyle = PaperStyle.DOTS,
  val lastShape: String = "LINE",
  /** Shapes she saved from her own drawings; hold-to-shape recognises them too. */
  val customShapes: List<CustomShape> = emptyList(),
  /**
   * Subjects whose shapes she starred ([com.tali.notes.model.ShapePacks]): they join the Shapes
   * panel and hold-to-shape. None at first: the panel starts with the standard shapes and hers.
   */
  val starredPacks: Set<String> = emptySet(),
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
  /** How heavy the paper's lines, grid and dots are drawn: 1 is the standard weight, 0.5..5. */
  val paperLineWeight: Float = 1f,
  /** Line thickness per built-in paper (by [PaperStyle] name), set from its edit card; missing ones use [paperLineWeight]. */
  val paperWeights: Map<String, Float> = emptyMap(),
  /** Line and paper colours per built-in paper (by [PaperStyle] name), set from its edit card. */
  val paperLooks: Map<String, com.tali.notes.model.PaperLook> = emptyMap(),
  /** The system bars (status and navigation) are hidden inside notebooks (swipe from an edge to see them). */
  val hideNavBar: Boolean = false,
  /** On A4 pages, one stroke may carry on across the gap between pages (the gap isn't exported). Off, ink stops at each page's edge. */
  val inkBetweenPages: Boolean = false,
  /** The Google Drive backup also keeps a PDF of each notebook beside it, readable anywhere. */
  val drivePdf: Boolean = false,
  /**
   * PDFs opened from other apps go straight into her notebooks, ready to write on. Off (the start),
   * they open just for looking, with a Save button.
   */
  val pdfsToLibrary: Boolean = false,
  /** How many colours a pen shows beside the pens until she sets its own number (`PenSlot.toolbarColors`). */
  val toolbarColors: Int = 3,
  /**
   * Light, dark, or following the tablet. Light to start with (the owner's call, 15 Oct 2026);
   * always written, so a chosen System default is kept even though it isn't the default.
   */
  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  @kotlinx.serialization.EncodeDefault
  val themeMode: ThemeMode = ThemeMode.LIGHT,
  /** The minimal look: no cloud bumps on bars and cards, no clouds in the sky, the home screen moved up. */
  val minimal: Boolean = false,
  /** The colour family (Settings › Colours). */
  val colorTheme: com.tali.notes.theme.ColorTheme = com.tali.notes.theme.ColorTheme.LILAC,
  /** One-time tips she has already seen. */
  val seenTips: Set<String> = emptySet(),
  /** PDFs, pictures and scans she tapped Write on: they open ready to write from then on. */
  val writeOn: Set<String> = emptySet(),
  /** Notebooks she set to View only (in the ⋯ menu): they open just for looking. */
  val viewOnly: Set<String> = emptySet(),
  /** Notebooks she opened, newest first (quick open, and sorting the library by last opened). */
  val recentNotebooks: List<String> = emptyList(),
  /** How the library orders notebooks, and whether the other way round (oldest first, Z to A). */
  val librarySort: LibrarySort = LibrarySort.EDITED,
  val librarySortReversed: Boolean = false,
) {
  /** The old Dashed pen became a fineliner drawing short dashes, now that any pen can dash. */
  fun migrated(): AppSettings {
    var s = this
    if (s.pens.any { it.type == PenType.DASHED }) {
      s = s.copy(pens = s.pens.map { if (it.type == PenType.DASHED) it.copy(type = PenType.FINELINER, line = LineStyle.SHORT_DASH) else it })
    }
    // The built-in pencil left the toolbar (October 2026): four pens to start with. Pencil is still a
    // pen type to choose, and a pen she adds herself has a random id, so only the original one goes.
    if (s.pens.size > 1 && s.pens.any { it.id == "p3" && it.type == PenType.PENCIL }) {
      val pens = s.pens.filterNot { it.id == "p3" && it.type == PenType.PENCIL }
      s = s.copy(pens = pens, activePen = if (pens.any { it.id == s.activePen }) s.activePen else pens.first().id)
    }
    // Every pen needs its own id (a change to one pen, like a star, must not reach the others).
    if (s.pens.map { it.id }.toSet().size != s.pens.size) {
      val seen = HashSet<String>()
      s = s.copy(pens = s.pens.mapIndexed { i, p -> if (seen.add(p.id)) p else p.copy(id = p.id + "-" + i).also { seen.add(it.id) } })
    }
    return s
  }

  /** The fill colour with its opacity applied, or null when the fill tool takes fills away. */
  val fillArgb: Int?
    get() = fillColor?.let { c -> ((255 * fillOpacity.coerceIn(0.15f, 1f)).toInt() shl 24) or (c and 0x00FFFFFF) }

  fun eraserHits(pen: PenType?) = pen != null && eraserAffects[pen] != false

  fun lassoHits(pen: PenType?) = if (pen == null) lassoSelectsImages else lassoAffects[pen] != false

  /** How many colours [slot] shows in the toolbar: its own number, else the app-wide one. */
  fun toolbarColorsFor(slot: PenSlot): Int = slot.toolbarColors ?: toolbarColors

  /** The palette for [slot]: the highlighter has its own. */
  fun paletteFor(slot: PenSlot): List<Int> = if (slot.type.isHighlighter) highlighterColors else savedColors

  val activeSlot: PenSlot
    get() = pens.firstOrNull { it.id == activePen } ?: pens.first()

  /** The pen the shape tool draws with: the active pen, unless that's a highlighter, then the first writing pen. */
  val shapeSlot: PenSlot
    get() = activeSlot.takeIf { it.type != PenType.HIGHLIGHTER } ?: pens.firstOrNull { it.type != PenType.HIGHLIGHTER } ?: activeSlot

  /**
   * The Shapes tool's own style, which stays as she set it whatever pen she picks (before she sets
   * one: her first pen's starting colour and size).
   */
  val shapeOwn: com.tali.notes.model.ShapeStyle
    get() = shapeStyle ?: com.tali.notes.model.ShapeStyle(DEFAULT_COLORS[0], 2.4f)

  /**
   * How the Shapes tool draws: its own style; or, with [shapeFollowsPen], the colour, size and line
   * of her writing pen, keeping its own fill and opacities.
   */
  val shapeLook: com.tali.notes.model.ShapeStyle
    get() = if (!shapeFollowsPen) shapeOwn
    else shapeSlot.let { shapeOwn.copy(color = it.color or (0xFF shl 24), size = it.size.coerceIn(com.tali.notes.model.ShapeStyle.MIN_SIZE, com.tali.notes.model.ShapeStyle.MAX_SIZE), line = it.lineStyle) }
}

class SettingsStore(context: Context) {
  private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
  private val _settings = MutableStateFlow(load().also {
    PaperRenderer.lineWeight = it.paperLineWeight
    PaperRenderer.weights = it.paperWeights
    PaperRenderer.looks = it.paperLooks
  })
  val settings: StateFlow<AppSettings> = _settings.asStateFlow()

  private fun load(): AppSettings =
    prefs.getString("json", null)?.let { runCatching { ItemCodec.json.decodeFromString(AppSettings.serializer(), it).migrated() }.getOrNull() } ?: AppSettings()

  fun update(transform: (AppSettings) -> AppSettings) {
    val next = transform(_settings.value)
    if (next == _settings.value) return
    _settings.value = next
    PaperRenderer.lineWeight = next.paperLineWeight
    PaperRenderer.weights = next.paperWeights
    PaperRenderer.looks = next.paperLooks
    // Turning all her settings (saved shapes, papers, colours…) into text and writing them takes a
    // while, so it's done off the main thread: done there, a colour picked in the pen panel froze the
    // ring's glide for long enough that it jumped. Only the newest settings are written.
    pending.set(next)
    writer.execute {
      val latest = pending.getAndSet(null) ?: return@execute
      prefs.edit().putString("json", ItemCodec.json.encodeToString(AppSettings.serializer(), latest)).apply()
    }
  }

  private val pending = java.util.concurrent.atomic.AtomicReference<AppSettings?>(null)
  private val writer = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "settings-writer").apply { isDaemon = true } }

  fun exportJson(): String = ItemCodec.json.encodeToString(AppSettings.serializer(), _settings.value)

  fun importJson(json: String) {
    runCatching { ItemCodec.json.decodeFromString(AppSettings.serializer(), json).migrated() }.getOrNull()?.let { s -> update { s } }
  }
}
