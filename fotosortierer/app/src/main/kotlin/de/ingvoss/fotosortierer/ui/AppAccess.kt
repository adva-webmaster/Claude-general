package de.ingvoss.fotosortierer.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.ingvoss.fotosortierer.AppContainer
import de.ingvoss.fotosortierer.FotoApp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Context.appContainer(): AppContainer = (applicationContext as FotoApp).container

/** ViewModel mit manueller Abhängigkeitsübergabe aus dem [AppContainer]. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val container = LocalContext.current.appContainer()
    return viewModel(key = key, factory = viewModelFactory { initializer { create(container) } })
}

private val takenFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d. MMM yyyy, HH:mm", Locale.GERMANY)

/** Aufnahmezeit für die Infozeile; null bei Medien ohne Datum. */
fun formatTaken(utcMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String? =
    if (utcMillis <= 0L) null else takenFormat.format(Instant.ofEpochMilli(utcMillis).atZone(zone))

