package de.ingvoss.fotosortierer.data.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Wie viel vom Medienbestand die App sehen darf. */
enum class AccessLevel {
    /** Kein Zugriff. */
    NONE,

    /** Nur ausgewählte Fotos/Videos (Android 14) oder nur eine der beiden Medienarten. */
    PARTIAL,

    /** Alle Fotos und Videos. */
    FULL
}

/** Versionsabhängige Berechtigungslogik (minSdk 30, Android 13/14 mit granularen Berechtigungen). */
object MediaAccess {
    /** Berechtigungen, die beim Anfordern gemeinsam abgefragt werden. */
    fun permissionsToRequest(): Array<String> = when {
        Build.VERSION.SDK_INT >= 34 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        Build.VERSION.SDK_INT >= 33 -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_MEDIA_LOCATION
        )
    }

    fun level(context: Context): AccessLevel {
        if (Build.VERSION.SDK_INT >= 33) {
            val images = granted(context, Manifest.permission.READ_MEDIA_IMAGES)
            val videos = granted(context, Manifest.permission.READ_MEDIA_VIDEO)
            return when {
                images && videos -> AccessLevel.FULL
                images || videos -> AccessLevel.PARTIAL
                Build.VERSION.SDK_INT >= 34 && granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ->
                    AccessLevel.PARTIAL
                else -> AccessLevel.NONE
            }
        }
        return if (granted(context, Manifest.permission.READ_EXTERNAL_STORAGE)) AccessLevel.FULL else AccessLevel.NONE
    }

    /** Ohne diese Berechtigung liefert MediaStore die Fotos ohne GPS-Daten (EXIF wird geschwärzt). */
    fun hasLocationAccess(context: Context): Boolean = granted(context, Manifest.permission.ACCESS_MEDIA_LOCATION)

    private fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
