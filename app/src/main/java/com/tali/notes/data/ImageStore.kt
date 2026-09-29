package com.tali.notes.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/**
 * Pictures and PDF pages, loaded only when they come into view and kept in a memory-capped cache,
 * so a notebook with a long slide deck opens instantly and never runs the tablet out of memory.
 */
class ImageStore(private val dir: File) {
  private val budget = (Runtime.getRuntime().maxMemory() / 3).coerceAtMost(256L * 1024 * 1024).toInt()
  private val cache =
    object : LruCache<String, Bitmap>(budget) {
      override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
  private val loading = ConcurrentHashMap.newKeySet<String>()
  private val failed = ConcurrentHashMap.newKeySet<String>()
  private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
  private val main = Handler(Looper.getMainLooper())
  private val workers = Executors.newFixedThreadPool(2) { r -> Thread(r, "images").apply { priority = Thread.NORM_PRIORITY - 1 } }

  /** Called on the main thread when a requested picture has finished loading. */
  fun addListener(l: (String) -> Unit) = listeners.add(l)

  fun removeListener(l: (String) -> Unit) = listeners.remove(l)

  fun peek(file: String): Bitmap? = cache.get(file)

  fun put(file: String, bitmap: Bitmap) {
    cache.put(file, bitmap)
  }

  /** Starts loading [file] in the background if it isn't already in memory. */
  fun request(file: String) {
    if (cache.get(file) != null || file in failed || !loading.add(file)) return
    workers.execute {
      val bmp = decode(file)
      if (bmp != null) cache.put(file, bmp) else failed += file
      loading.remove(file)
      if (bmp != null) main.post { listeners.forEach { it(file) } }
    }
  }

  /** Loads right away, for PDF export and previews. */
  fun loadNow(file: String): Bitmap? = cache.get(file) ?: decode(file)?.also { cache.put(file, it) }

  private fun decode(file: String): Bitmap? =
    runCatching {
        val opts = BitmapFactory.Options().apply {
          // Photos and PDF pages have no transparency: half the memory, same look.
          inPreferredConfig = if (file.endsWith(".jpg")) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(File(dir, file).path, opts)?.also { it.prepareToDraw() }
      }
      .getOrNull()
}
