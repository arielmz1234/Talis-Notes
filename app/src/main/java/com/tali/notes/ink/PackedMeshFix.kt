package com.tali.notes.ink

import android.graphics.ColorSpace
import android.graphics.MeshSpecification
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

/**
 * Ink 1.0.0's shader for finished strokes, corrected the way Ink itself corrected it in
 * 1.1.0-alpha04 ("floating point precision issues leading to visual artifacts in finished strokes,
 * affecting certain devices"), for as long as the app stays on 1.0.0 (the last stable version; the
 * 1.1 alphas rework the brush API every pen here is built on). Remove it when Ink is upgraded.
 *
 * What went wrong: a finished stroke's mesh is packed, every point squeezed into three bytes (12
 * bits across and 12 down, inside the stroke's box) that reach the shader as 0..1 `half` values,
 * and 1.0.0 splits them apart again with `floor` and `fract` of `15.9375 * byte`. That is exact
 * only when the graphics chip keeps those values in 32 bits. Chips that may keep `half` in 16 bits
 * (Mali, in Samsung's tablets) can't hold 255ths exactly: most points land a step or two off along
 * the edge (bumps), and one byte value in about 23 throws a point a whole box away (a triangle
 * folded over: a gap, or a stroke's end cut off sharply). Which points go wrong depends on the
 * exact bytes, so the same stroke packed again (the notebook reopened, the stroke moved) usually
 * looked right, and the emulator, which does all its maths in 32 bits, never showed any of it.
 * The anti-aliasing directions are packed the same way, and a level or upright line packs them as
 * round values, which are just the ones that break: on Tali's Tab S9 FE such lines under a pixel
 * wide vanished when zoomed out. The fixed shader rounds each byte to its whole value first
 * (`floor(255.0 * v + 0.5)`), which is exact on any chip. Seen on the emulator by making its
 * shader round the packed values to 16 bits: Ink's own tore every stroke apart, the corrected one
 * drew every pixel exactly as the 32-bit drawing did.
 *
 * How: Ink's mesh renderer builds one shader per mesh layout, from text its native code hands
 * over, and caches it in a list ([CACHE_FIELD]). That list is swapped for one that, as each
 * finished-stroke shader is added, compiles the same text with the unpacking functions replaced by
 * 1.1's ([fixed]), and puts it in place of Ink's. Ink then draws exactly as before (same mesh, same
 * uniforms, same anti-aliasing), only with points that are where they should be. Anything unexpected
 * (another Ink version, renamed internals) leaves Ink as it is and says so in the log.
 */
object PackedMeshFix {
  private const val TAG = "PackedMeshFix"
  private const val INTERNAL = "androidx.ink.rendering.android.canvas.internal"
  private const val CACHE_FIELD = "meshFormatToPackedShaderMetadata"

  /** How many finished-stroke shaders were corrected, and how many attempts failed (for tests). */
  @Volatile var corrected = 0
    private set
  @Volatile var failed = 0
    private set

  /** [renderer] with its finished-stroke shader corrected (Android 14 and later; meshes need 14). */
  fun applyTo(renderer: CanvasStrokeRenderer): CanvasStrokeRenderer {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      runCatching { install(renderer) }.onFailure { failed++; Log.w(TAG, "Ink's shader left as it is", it) }
    }
    return renderer
  }

  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  private fun install(renderer: CanvasStrokeRenderer) {
    val preferences = renderer.javaClass.getDeclaredField("rendererPreferences").apply { isAccessible = true }.get(renderer) as List<*>
    // The renderers it tries in turn: Ink's mesh renderer first, unless paths were asked for.
    val mesh = preferences.firstNotNullOfOrNull { (it as? Lazy<*>)?.value?.takeIf { r -> r.javaClass.name == "$INTERNAL.CanvasMeshRenderer" } } ?: return
    val cache = mesh.javaClass.getDeclaredField(CACHE_FIELD).apply { isAccessible = true }
    @Suppress("UNCHECKED_CAST")
    val old = cache.get(mesh) as ArrayList<Any?>
    if (old is FixingList) return
    cache.set(mesh, FixingList(mesh).also { it.addAll(old) })
  }

  /** Ink's cache of finished-stroke shaders, correcting each one as Ink adds it. */
  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
  private class FixingList(private val mesh: Any) : ArrayList<Any?>() {
    override fun add(element: Any?): Boolean {
      runCatching { fix(element as Pair<*, *>) }.onFailure { failed++; Log.w(TAG, "Ink's shader left as it is", it) }
      return super.add(element)
    }

    /** Puts a corrected shader into the cached metadata of [entry] (a mesh layout and its shader). */
    private fun fix(entry: Pair<*, *>) {
      val format = entry.first ?: return
      val metadata = entry.second ?: return
      val pointer = format.javaClass.getMethod("getNativePointer").invoke(format) as Long
      val attrTypes = IntArray(8) { -1 }
      val attrOffsets = IntArray(8) { -1 }
      val attrNames = Array(8) { ")" }
      val stride = intArrayOf(-1)
      val varyingTypes = IntArray(6) { -1 }
      val varyingNames = Array(6) { ")" }
      val uniformIds = IntArray(10) { -1 }
      val uniformUnpacking = IntArray(10) { -1 }
      val uniformNames = Array(10) { ")" }
      val vertex = arrayOf("unset vertex shader")
      val fragment = arrayOf("unset fragment shader")
      // Ink's own native call for this layout's shader (what it compiled itself).
      mesh.javaClass
        .getDeclaredMethod(
          "fillSkiaMeshSpecData",
          Long::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
          IntArray::class.java, IntArray::class.java, Array<String>::class.java, IntArray::class.java,
          IntArray::class.java, Array<String>::class.java,
          IntArray::class.java, IntArray::class.java, Array<String>::class.java,
          Array<String>::class.java, Array<String>::class.java,
        )
        .apply { isAccessible = true }
        .invoke(mesh, pointer, true, attrTypes, attrOffsets, attrNames, stride, varyingTypes, varyingNames, uniformIds, uniformUnpacking, uniformNames, vertex, fragment)
      val source = vertex[0]
      val patched = fixed(source) ?: return
      // Ink's types by their native value, as Ink maps them.
      val types = Class.forName("$INTERNAL.CanvasMeshRenderer\$Companion\$Type").enumConstants!!.associate { t ->
        (t.javaClass.getMethod("getNativeValue").invoke(t) as Int) to (t.javaClass.getMethod("getMeshSpecValue").invoke(t) as Int)
      }
      val attributes = (0 until 8).map { i -> types[attrTypes[i]]?.let { MeshSpecification.Attribute(it, attrOffsets[i], attrNames[i]) } }.takeWhile { it != null }.filterNotNull()
      val varyings = (0 until 6).map { i -> types[varyingTypes[i]]?.let { MeshSpecification.Varying(it, varyingNames[i]) } }.takeWhile { it != null }.filterNotNull()
      val spec = MeshSpecification.make(
        attributes.toTypedArray(),
        stride[0],
        varyings.toTypedArray(),
        patched,
        fragment[0],
        ColorSpace.get(ColorSpace.Named.LINEAR_EXTENDED_SRGB),
        MeshSpecification.ALPHA_TYPE_PREMULTIPLIED,
      )
      metadata.javaClass.getDeclaredField("meshSpecification").apply { isAccessible = true }.set(metadata, spec)
      corrected++
      Log.i(TAG, "Finished-stroke shader corrected (${attributes.size} attributes)")
    }
  }

  /**
   * [vertexShader] with Ink 1.0.0's packed unpacking functions replaced by 1.1's, or null when it
   * has none of them (or not as 1.0.0 wrote them). Each replacement gives the same numbers as the
   * old function when the maths is exact; it only adds the rounding to whole bytes.
   */
  fun fixed(vertexShader: String): String? {
    var out = vertexShader
    var changed = false
    for ((old, new) in REPLACEMENTS) {
      if (old in out) {
        out = out.replace(old, new)
        changed = true
      }
    }
    return out.takeIf { changed }
  }

  private val REPLACEMENTS =
    listOf(
      """
    float2 unpackFloat2PackedIntoUByte3(const float4 unpackingTransform,
                                        const half3 packedValue) {
      float mixedXY = 15.9375 * float(packedValue.y);
      float2 unpacked =
          float2(4080.0 * float(packedValue.x) + floor(mixedXY),
                 4096.0 * fract(mixedXY) + 255.0 * float(packedValue.z));
      return unpackingTransform.yw * unpacked + unpackingTransform.xz;
    }""" to
        """
    float2 unpackFloat2PackedIntoUByte3(const float4 unpackingTransform,
                                        const float3 packedValue0To255) {
      float mixedXY = packedValue0To255.y / 16.0;
      float2 unpacked = float2(
          16.0 * packedValue0To255.x + floor(mixedXY),
          4096.0 * fract(mixedXY) + packedValue0To255.z
      );
      return unpackingTransform.yw * unpacked + unpackingTransform.xz;
    }""",
      """
    float3 unpackPositionAndOpacityShift(const float4 unpackingTransform,
                                         const half4 packedValue) {
      return float3(
          unpackFloat2PackedIntoUByte3(unpackingTransform, packedValue.xyz),
          (255.0 / 127.0) * packedValue.w - 1.0);
    }""" to
        """
    float3 unpackPositionAndOpacityShift(const float4 unpackingTransform,
                                         const half4 packedValue0To1) {
      float4 packedValue0To255 = floor(255.0 * packedValue0To1 + 0.5);
      return float3(
          unpackFloat2PackedIntoUByte3(unpackingTransform, packedValue0To255.xyz),
          packedValue0To255.w / 127.0 - 1.0
      );
    }""",
      """
    float3 unpackDerivativeAndLabel(const float4 unpackingTransform,
                                    const half4 packedValue) {
      return float3(
          unpackFloat2PackedIntoUByte3(unpackingTransform, packedValue.xyz),
          255.0 * packedValue.w - 128.0);
    }""" to
        """
    float3 unpackDerivativeAndLabel(const float4 unpackingTransform,
                                    const half4 packedValue0To1) {
      float4 packedValue0To255 = floor(255.0 * packedValue0To1 + 0.5);
      return float3(
          unpackFloat2PackedIntoUByte3(unpackingTransform, packedValue0To255.xyz),
          packedValue0To255.w - 128.0
      );
    }""",
      """
    float3 unpackHSLColorShift(const half4 packedValue) {
      float mixedXY = 3.984375 * float(packedValue.y);
      float mixedYZ = 15.9375 * float(packedValue.z);
      return float3(1020.0 * float(packedValue.x) + floor(mixedXY),
                    1024.0 * fract(mixedXY) + floor(mixedYZ),
                    1024.0 * fract(mixedYZ) + 63.75 * float(packedValue.w)) /
                 511.0 -
             float3(1.0);
    }""" to
        """
    float3 unpackHSLColorShift(const half4 packedValue0To1) {
      float4 packedValue0To255 = floor(255.0 * packedValue0To1 + 0.5);
      return float3(
          4.0 * packedValue0To255.x + floor(packedValue0To255.y / 64.0),
          1024.0 * fract(packedValue0To255.y / 64.0) + floor(packedValue0To255.z / 16.0),
          1024.0 * fract(packedValue0To255.z / 16.0) + packedValue0To255.w / 4.0
      ) / 511.0 - float3(1.0);
    }""",
      """
    float2 unpackSurfaceUv(const half3 packedValue) {
      float mixedUV = 15.9375 * float(packedValue.y);
      return float2(
          (4080.0 * float(packedValue.x) + floor(mixedUV)) / 4095.0,
          (4096.0 * fract(mixedUV) + 255.0 * float(packedValue.z)) / 4095.0);
    }""" to
        """
    float2 unpackSurfaceUv(const half3 packedValue0To1) {
      float3 packedValue0To255 = floor(255.0 * packedValue0To1 + 0.5);
      return float2(
          (16.0 * packedValue0To255.x + floor(packedValue0To255.y / 16.0)),
          (4096.0 * fract(packedValue0To255.y / 16.0) + packedValue0To255.z)
      ) / 4095.0;
    }""",
      """
    float2 unpackSurfaceUv(const half4 packedValue) {
      float mixedXY = 15.9375 * float(packedValue.y);
      return float2((4080.0 * float(packedValue.x) + floor(mixedXY)) / 4095.0,
                    (1048576.0 * fract(mixedXY) +
                     65280.0 * float(packedValue.z) +
                     255.0 * float(packedValue.w)) / 1048575.0);
    }""" to
        """
    float2 unpackSurfaceUv(const half4 packedValue0To1) {
      float4 packedValue0To255 = floor(255.0 * packedValue0To1 + 0.5);
      return float2(
          (16.0 * packedValue0To255.x + floor(packedValue0To255.y / 16.0)) / 4095.0,
          (1048576.0 * fract(packedValue0To255.y / 16.0) +
              256.0 * packedValue0To255.z +
              packedValue0To255.w) / 1048575.0
      );
    }""",
    )
}
