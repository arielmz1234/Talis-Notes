package com.tali.notes.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tali.notes.R
import com.tali.notes.data.LibrarySort
import com.tali.notes.export.PdfLayout
import com.tali.notes.export.PdfQuality
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PackShape
import com.tali.notes.model.PaperStyle
import com.tali.notes.model.PenType
import com.tali.notes.model.ShapeGroup
import com.tali.notes.model.ShapeKind
import com.tali.notes.model.ShapePack

// The names of things in the app (pens, papers, line styles, shapes, subjects, PDF choices) in the
// app's language. The model keeps its English names for code and tests; screens show these.
// Made by a script from the model's names; keep it in step when adding a pen, paper or shape.

/** The name shown for this PenType, in the app's language. */
@get:StringRes
val PenType.nameRes: Int
  get() =
    when (this) {
    PenType.FINELINER -> R.string.pen_fineliner
    PenType.FOUNTAIN -> R.string.pen_fountain
    PenType.PENCIL -> R.string.pen_pencil
    PenType.CALLIGRAPHY -> R.string.pen_calligraphy
    PenType.HIGHLIGHTER -> R.string.pen_highlighter
    PenType.DASHED -> R.string.pen_dashed
    }

/** The name shown for this LineStyle, in the app's language. */
@get:StringRes
val LineStyle.nameRes: Int
  get() =
    when (this) {
    LineStyle.SOLID -> R.string.line_style_solid
    LineStyle.DOTTED -> R.string.line_style_dotted
    LineStyle.LONG_DASH -> R.string.line_style_long_dash
    LineStyle.SHORT_DASH -> R.string.line_style_short_dash
    LineStyle.DOT_DASH -> R.string.line_style_dot_dash
    LineStyle.MIXED_DASH -> R.string.line_style_mixed_dash
    }

/** The name shown for this PaperStyle, in the app's language. */
@get:StringRes
val PaperStyle.nameRes: Int
  get() =
    when (this) {
    PaperStyle.BLANK -> R.string.paper_blank
    PaperStyle.DOTS -> R.string.paper_dots
    PaperStyle.GRID -> R.string.paper_grid
    PaperStyle.LINES -> R.string.paper_lines
    PaperStyle.ENGINEERING -> R.string.paper_engineering
    PaperStyle.ISOMETRIC -> R.string.paper_isometric
    PaperStyle.CORNELL -> R.string.paper_cornell
    PaperStyle.RULED -> R.string.paper_ruled
    }

/** The name shown for this LibrarySort, in the app's language. */
@get:StringRes
val LibrarySort.nameRes: Int
  get() =
    when (this) {
    LibrarySort.EDITED -> R.string.sort_edited
    LibrarySort.OPENED -> R.string.sort_opened
    LibrarySort.CREATED -> R.string.sort_created
    LibrarySort.NAME -> R.string.sort_name
    }

/** The name shown for this PdfLayout, in the app's language. */
@get:StringRes
val PdfLayout.nameRes: Int
  get() =
    when (this) {
    PdfLayout.A4_PAGES -> R.string.pdf_layout_a4_pages
    PdfLayout.ONE_PAGE -> R.string.pdf_layout_one_page
    PdfLayout.PDF_PAGES -> R.string.pdf_layout_pdf_pages
    }

/** The name shown for this ShapeGroup, in the app's language. */
@get:StringRes
val ShapeGroup.nameRes: Int
  get() =
    when (this) {
    ShapeGroup.BASIC -> R.string.shape_group_basic
    ShapeGroup.ENGINEERING -> R.string.shape_group_engineering
    }

/** The name shown for this ShapeKind, in the app's language. */
@get:StringRes
val ShapeKind.nameRes: Int
  get() =
    when (this) {
    ShapeKind.LINE -> R.string.shape_line
    ShapeKind.ARROW -> R.string.shape_arrow
    ShapeKind.DOUBLE_ARROW -> R.string.shape_double_arrow
    ShapeKind.CIRCLE -> R.string.shape_circle
    ShapeKind.ELLIPSE -> R.string.shape_ellipse
    ShapeKind.ARC -> R.string.shape_arc
    ShapeKind.CURVE -> R.string.shape_curve
    ShapeKind.PART_CIRCLE -> R.string.shape_part_circle
    ShapeKind.TRIANGLE -> R.string.shape_triangle
    ShapeKind.SQUARE -> R.string.shape_square
    ShapeKind.RECTANGLE -> R.string.shape_rectangle
    ShapeKind.DIAMOND -> R.string.shape_diamond
    ShapeKind.PARALLELOGRAM -> R.string.shape_parallelogram
    ShapeKind.TRAPEZOID -> R.string.shape_trapezoid
    ShapeKind.PENTAGON -> R.string.shape_pentagon
    ShapeKind.HEXAGON -> R.string.shape_hexagon
    ShapeKind.STAR -> R.string.shape_star
    ShapeKind.PIN_SUPPORT -> R.string.shape_pin_support
    ShapeKind.ROLLER_SUPPORT -> R.string.shape_roller_support
    ShapeKind.FIXED_SUPPORT -> R.string.shape_fixed_support
    ShapeKind.POINT_LOAD -> R.string.shape_point_load
    ShapeKind.DISTRIBUTED_LOAD -> R.string.shape_distributed_load
    ShapeKind.MOMENT -> R.string.shape_moment
    ShapeKind.DIMENSION -> R.string.shape_dimension
    }

@get:StringRes
val PdfQuality.nameRes: Int
  get() =
    when (this) {
    PdfQuality.BEST -> R.string.pdf_quality_best
    PdfQuality.BALANCED -> R.string.pdf_quality_balanced
    PdfQuality.LIGHT -> R.string.pdf_quality_light
    }

@get:StringRes
val PdfQuality.detailRes: Int
  get() =
    when (this) {
    PdfQuality.BEST -> R.string.pdf_quality_best_detail
    PdfQuality.BALANCED -> R.string.pdf_quality_balanced_detail
    PdfQuality.LIGHT -> R.string.pdf_quality_light_detail
    }

private val subjectNames = mapOf(
  "social" to R.string.subject_social,
  "business" to R.string.subject_business,
  "computing" to R.string.subject_computing,
  "law" to R.string.subject_law,
  "health" to R.string.subject_health,
  "mechanical" to R.string.subject_mechanical,
  "civil" to R.string.subject_civil,
  "electrical" to R.string.subject_electrical,
  "psychology" to R.string.subject_psychology,
  "economics" to R.string.subject_economics,
  "design" to R.string.subject_design,
)

private val subjectShapeNames = mapOf(
  "person" to R.string.subject_shape_person,
  "group" to R.string.subject_shape_group,
  "network" to R.string.subject_shape_network,
  "genogram" to R.string.subject_shape_genogram,
  "male" to R.string.subject_shape_male,
  "female" to R.string.subject_shape_female,
  "speech" to R.string.subject_shape_speech,
  "globe" to R.string.subject_shape_globe,
  "timeline" to R.string.subject_shape_timeline,
  "venn" to R.string.subject_shape_venn,
  "org-chart" to R.string.subject_shape_org_chart,
  "bar-chart" to R.string.subject_shape_bar_chart,
  "pie-chart" to R.string.subject_shape_pie_chart,
  "growth" to R.string.subject_shape_growth,
  "funnel" to R.string.subject_shape_funnel,
  "swot" to R.string.subject_shape_swot,
  "gantt" to R.string.subject_shape_gantt,
  "target" to R.string.subject_shape_target,
  "coins" to R.string.subject_shape_coins,
  "document" to R.string.subject_shape_document,
  "database" to R.string.subject_shape_database,
  "terminal" to R.string.subject_shape_terminal,
  "subroutine" to R.string.subject_shape_subroutine,
  "binary-tree" to R.string.subject_shape_binary_tree,
  "graph" to R.string.subject_shape_graph,
  "array" to R.string.subject_shape_array,
  "linked-list" to R.string.subject_shape_linked_list,
  "stack" to R.string.subject_shape_stack,
  "computer" to R.string.subject_shape_computer,
  "cloud" to R.string.subject_shape_cloud,
  "scales" to R.string.subject_shape_scales,
  "gavel" to R.string.subject_shape_gavel,
  "courthouse" to R.string.subject_shape_courthouse,
  "column" to R.string.subject_shape_column,
  "contract" to R.string.subject_shape_contract,
  "shield" to R.string.subject_shape_shield,
  "court-levels" to R.string.subject_shape_court_levels,
  "parties" to R.string.subject_shape_parties,
  "handcuffs" to R.string.subject_shape_handcuffs,
  "law-book" to R.string.subject_shape_law_book,
  "med-cross" to R.string.subject_shape_med_cross,
  "heart" to R.string.subject_shape_heart,
  "heartbeat" to R.string.subject_shape_heartbeat,
  "capsule" to R.string.subject_shape_capsule,
  "syringe" to R.string.subject_shape_syringe,
  "thermometer" to R.string.subject_shape_thermometer,
  "stethoscope" to R.string.subject_shape_stethoscope,
  "body" to R.string.subject_shape_body,
  "iv-bag" to R.string.subject_shape_iv_bag,
  "drop" to R.string.subject_shape_drop,
  "gear" to R.string.subject_shape_gear,
  "spring" to R.string.subject_shape_spring,
  "damper" to R.string.subject_shape_damper,
  "bearing" to R.string.subject_shape_bearing,
  "pulley" to R.string.subject_shape_pulley,
  "lever" to R.string.subject_shape_lever,
  "piston" to R.string.subject_shape_piston,
  "mass" to R.string.subject_shape_mass,
  "shaft" to R.string.subject_shape_shaft,
  "bolt" to R.string.subject_shape_bolt,
  "i-beam" to R.string.subject_shape_i_beam,
  "truss" to R.string.subject_shape_truss,
  "level" to R.string.subject_shape_level,
  "resistor" to R.string.subject_shape_resistor,
  "capacitor" to R.string.subject_shape_capacitor,
  "inductor" to R.string.subject_shape_inductor,
  "diode" to R.string.subject_shape_diode,
  "battery" to R.string.subject_shape_battery,
  "ac-source" to R.string.subject_shape_ac_source,
  "switch" to R.string.subject_shape_switch,
  "lamp" to R.string.subject_shape_lamp,
  "ground" to R.string.subject_shape_ground,
  "and-gate" to R.string.subject_shape_and_gate,
  "brain" to R.string.subject_shape_brain,
  "head" to R.string.subject_shape_head,
  "neuron" to R.string.subject_shape_neuron,
  "bell-curve" to R.string.subject_shape_bell_curve,
  "maslow" to R.string.subject_shape_maslow,
  "thought" to R.string.subject_shape_thought,
  "eye" to R.string.subject_shape_eye,
  "face" to R.string.subject_shape_face,
  "cycle" to R.string.subject_shape_cycle,
  "scatter" to R.string.subject_shape_scatter,
  "supply-demand" to R.string.subject_shape_supply_demand,
  "axes" to R.string.subject_shape_axes,
  "demand" to R.string.subject_shape_demand,
  "indifference" to R.string.subject_shape_indifference,
  "ppf" to R.string.subject_shape_ppf,
  "cost-curves" to R.string.subject_shape_cost_curves,
  "lorenz" to R.string.subject_shape_lorenz,
  "circular-flow" to R.string.subject_shape_circular_flow,
  "business-cycle" to R.string.subject_shape_business_cycle,
  "laffer" to R.string.subject_shape_laffer,
  "colour-wheel" to R.string.subject_shape_colour_wheel,
  "thirds" to R.string.subject_shape_thirds,
  "golden-spiral" to R.string.subject_shape_golden_spiral,
  "perspective" to R.string.subject_shape_perspective,
  "brush" to R.string.subject_shape_brush,
  "pencil" to R.string.subject_shape_pencil,
  "palette" to R.string.subject_shape_palette,
  "frame" to R.string.subject_shape_frame,
  "cube" to R.string.subject_shape_cube,
  "easel" to R.string.subject_shape_easel,
)

/** A subject's name in the app's language. */
@Composable
fun ShapePack.title(): String = subjectNames[id]?.let { stringResource(it) } ?: name

/** A subject shape's name in the app's language (a built-in symbol uses its own name). */
@Composable
fun PackShape.title(): String = kind?.let { stringResource(it.nameRes) } ?: subjectShapeNames[id]?.let { stringResource(it) } ?: label
