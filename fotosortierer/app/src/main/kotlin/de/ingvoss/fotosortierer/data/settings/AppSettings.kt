package de.ingvoss.fotosortierer.data.settings

import de.ingvoss.fotosortierer.core.MediaKind
import de.ingvoss.fotosortierer.core.SwipeMapping

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Alle Nutzereinstellungen auf einen Blick. */
data class AppSettings(
    val swipeMapping: SwipeMapping = SwipeMapping.STANDARD,
    /** true = älteste zuerst (Standard), false = neueste zuerst. */
    val oldestFirst: Boolean = true,
    val clusterRadiusMeters: Int = DEFAULT_RADIUS_METERS,
    val showPhotos: Boolean = true,
    val showVideos: Boolean = true,
    val showScreenshots: Boolean = true,
    val excludedAlbums: Set<String> = emptySet(),
    /** Favorisierte Ordner (Albumnamen): stehen in der Ordnerliste und auf der Startseite oben. */
    val favoriteAlbums: Set<String> = emptySet(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val videoSoundDefault: Boolean = false,
    val showInfoLine: Boolean = true,
    val trashHintShown: Boolean = false
) {
    /** Medientypen, die laut Einstellung ausgeblendet werden. */
    val hiddenKinds: List<MediaKind>
        get() = buildList {
            if (!showPhotos) add(MediaKind.FOTO)
            if (!showVideos) add(MediaKind.VIDEO)
            if (!showScreenshots) add(MediaKind.SCREENSHOT)
        }

    companion object {
        const val DEFAULT_RADIUS_METERS = 1000

        /** Auswahl im Einstellungsdialog (Meter). */
        val RADIUS_CHOICES = listOf(250, 500, 1000, 2000, 5000)
    }
}
