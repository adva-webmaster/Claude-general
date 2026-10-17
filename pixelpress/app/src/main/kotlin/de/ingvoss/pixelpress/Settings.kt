package de.ingvoss.pixelpress

import android.content.Context
import de.ingvoss.pixelpress.core.Quality

/** Gemerkte Einstellungen (SharedPreferences). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var directory: String?
        get() = prefs.getString("directory", null)
        set(value) = prefs.edit().putString("directory", value).apply()

    fun loadOptions(): Options {
        val defaults = Options()
        return Options(
            recursive = prefs.getBoolean("recursive", defaults.recursive),
            skipExisting = prefs.getBoolean("skipExisting", defaults.skipExisting),
            copyWhenPossible = prefs.getBoolean("copyWhenPossible", defaults.copyWhenPossible),
            quality = prefs.getString("quality", null)?.let { name -> Quality.entries.firstOrNull { it.name == name } }
                ?: defaults.quality,
            deleteOriginals = prefs.getBoolean("deleteOriginals", defaults.deleteOriginals)
        )
    }

    fun saveOptions(options: Options) {
        prefs.edit()
            .putBoolean("recursive", options.recursive)
            .putBoolean("skipExisting", options.skipExisting)
            .putBoolean("copyWhenPossible", options.copyWhenPossible)
            .putString("quality", options.quality.name)
            .putBoolean("deleteOriginals", options.deleteOriginals)
            .apply()
    }
}
