package com.tali.notes

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language, apart from the tablet's: the system's (null), English or Hebrew.
 *
 * On Android 13 and newer it's Android's per-app language ([LocaleManager]): the choice also shows
 * in the tablet's Settings › Apps › Tali's Notes › Language, and Android restarts the screen in the
 * new language by itself. Before that, the choice is kept here and applied to the activity's
 * resources ([wrap]), restarting it on a change.
 */
object AppLanguage {
  /** The languages the app is written in, by tag. */
  val available = listOf("en", "he")

  /** Each language's name in itself, as language pickers show them. */
  fun nativeName(tag: String): String = Locale.forLanguageTag(tag).let { it.getDisplayName(it) }.replaceFirstChar { it.titlecase(Locale.forLanguageTag(tag)) }

  /** The chosen language's tag, or null to follow the tablet. */
  fun current(context: Context): String? =
    if (Build.VERSION.SDK_INT >= 33) {
      context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.let(::normalize)
    } else {
      prefs(context).getString(KEY, null)
    }

  fun set(activity: Activity, tag: String?) {
    if (tag == current(activity)) return
    if (Build.VERSION.SDK_INT >= 33) {
      activity.getSystemService(LocaleManager::class.java).applicationLocales = if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    } else {
      prefs(activity).edit().putString(KEY, tag).commit()
      activity.recreate()
    }
  }

  /** Before Android 13: the activity's context in the chosen language (unchanged when following the tablet). */
  fun wrap(base: Context): Context {
    if (Build.VERSION.SDK_INT >= 33) return base
    val tag = prefs(base).getString(KEY, null) ?: return base
    val locale = Locale.forLanguageTag(tag)
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
    return base.createConfigurationContext(config)
  }

  // Java's old code for Hebrew is "iw"; the app speaks of "he".
  private fun normalize(locale: Locale): String = if (locale.language == "iw") "he" else locale.language

  private fun prefs(context: Context) = context.getSharedPreferences("language", Context.MODE_PRIVATE)

  private const val KEY = "tag"
}
