package com.tali.notes

/**
 * Reads the parts of an Android native crash record (a "tombstone", Android's tombstone.proto)
 * that say what went wrong: the signal and fault address, the cause, the abort message, and the
 * backtrace of the thread that crashed, before the other threads' names. Pulling plain words out
 * listed every thread in no order and the report was cut off before the one that crashed.
 */
internal object Tombstone {
  private class Reader(val b: ByteArray, var p: Int = 0, val end: Int = b.size) {
    fun more() = p < end

    fun varint(): Long {
      var shift = 0
      var r = 0L
      while (true) {
        val x = b[p++].toInt() and 0xff
        r = r or ((x and 0x7f).toLong() shl shift)
        if (x < 0x80) return r
        shift += 7
      }
    }

    /** Calls [f] with each field number, wire type and (for length-delimited fields) a sub-reader. */
    fun fields(f: (Int, Int, Reader?, Long) -> Unit) {
      while (more()) {
        val key = varint()
        val field = (key ushr 3).toInt()
        when ((key and 7).toInt()) {
          0 -> f(field, 0, null, varint())
          1 -> p += 8
          2 -> {
            val n = varint().toInt()
            f(field, 2, Reader(b, p, p + n), 0)
            p += n
          }
          5 -> p += 4
          else -> error("wire type")
        }
      }
    }

    fun text() = String(b, p, end - p, Charsets.UTF_8)
  }

  private class Frame(var function: String = "", var offset: Long = 0, var file: String = "", var relPc: Long = 0)

  private class Thread(var id: Long = 0, var name: String = "", val frames: MutableList<Frame> = ArrayList())

  fun describe(bytes: ByteArray): String? {
    var tid = -1L
    var signal = ""
    var fault = ""
    var abort = ""
    val causes = ArrayList<String>()
    val threads = ArrayList<Thread>()
    Reader(bytes).fields { field, _, r, v ->
      when (field) {
        6 -> tid = v
        10 -> {
          var name = ""
          var code = ""
          r!!.fields { f, _, s, n ->
            when (f) {
              2 -> name = s!!.text()
              4 -> code = s!!.text()
              9 -> fault = "0x" + n.toString(16)
            }
          }
          signal = "$name $code".trim()
        }
        14 -> abort = r!!.text()
        15 -> r!!.fields { f, _, s, _ -> if (f == 1) causes += s!!.text() }
        16 ->
          r!!.fields { f, _, s, _ ->
            if (f == 2) {
              val t = Thread()
              s!!.fields { tf, _, ts, tn ->
                when (tf) {
                  1 -> t.id = tn
                  2 -> t.name = ts!!.text()
                  4 -> {
                    val fr = Frame()
                    ts!!.fields { ff, _, fs, fn ->
                      when (ff) {
                        1 -> fr.relPc = fn
                        4 -> fr.function = fs!!.text()
                        5 -> fr.offset = fn
                        6 -> fr.file = fs!!.text()
                      }
                    }
                    t.frames += fr
                  }
                }
              }
              threads += t
            }
          }
      }
    }
    if (threads.isEmpty()) return null
    val crashed = threads.firstOrNull { it.id == tid }
    return buildString {
      append("Signal: ").append(signal)
      if (fault.isNotEmpty()) append(" at ").append(fault)
      append('\n')
      causes.forEach { append("Cause: ").append(it).append('\n') }
      if (abort.isNotEmpty()) append("Abort: ").append(abort).append('\n')
      if (crashed != null) {
        append("Crashed thread: ").append(crashed.name).append(" (").append(crashed.id).append(")\n")
        crashed.frames.take(30).forEachIndexed { i, f ->
          append("  #").append(i.toString().padStart(2, '0')).append(" pc ").append(f.relPc.toString(16)).append("  ")
          append(f.file.substringAfterLast('/'))
          if (f.function.isNotEmpty()) append(" (").append(f.function.take(160)).append('+').append(f.offset).append(')')
          append('\n')
        }
      }
      append("Other threads: ").append(threads.filter { it !== crashed }.joinToString(", ") { it.name })
    }
  }
}
