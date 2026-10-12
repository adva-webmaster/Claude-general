package de.ingvoss.fotosortierer.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.ingvoss.fotosortierer.core.FilterCodec
import de.ingvoss.fotosortierer.core.MediaFilter
import de.ingvoss.fotosortierer.core.QueuePhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * Zustand der laufenden Sortiersitzung. Die Position selbst steht in Room (Status der Medien); hier
 * liegen nur Filter, Zeitpunkte und Phase, damit "Weitermachen" nach einem Neustart genau dort einsteigt,
 * wo der Nutzer aufgehört hat.
 */
data class SessionState(
    val filter: MediaFilter,
    val startedAt: Long,
    val phase: QueuePhase,
    val phase2StartedAt: Long
)

private fun decodePhase(code: Int): QueuePhase = when (code) {
    1 -> QueuePhase.ZURUECKGESTELLT
    2 -> QueuePhase.ERNEUT
    else -> QueuePhase.UNBEARBEITET
}

private fun encodePhase(phase: QueuePhase): Int = when (phase) {
    QueuePhase.UNBEARBEITET -> 0
    QueuePhase.ZURUECKGESTELLT -> 1
    QueuePhase.ERNEUT -> 2
}

class SessionStore(private val context: Context) {
    private object Keys {
        val ACTIVE = booleanPreferencesKey("active")
        val FILTER = stringPreferencesKey("filter")
        val STARTED = longPreferencesKey("started_at")
        val PHASE = intPreferencesKey("phase")
        val PHASE2 = longPreferencesKey("phase2_started_at")
    }

    /** Die zuletzt begonnene Sitzung, solange sie nicht abgeschlossen wurde. */
    val state: Flow<SessionState?> = context.sessionDataStore.data.map { prefs ->
        if (prefs[Keys.ACTIVE] != true) {
            null
        } else {
            SessionState(
                filter = FilterCodec.decode(prefs[Keys.FILTER]),
                startedAt = prefs[Keys.STARTED] ?: 0L,
                phase = decodePhase(prefs[Keys.PHASE] ?: 0),
                phase2StartedAt = prefs[Keys.PHASE2] ?: 0L
            )
        }
    }

    suspend fun current(): SessionState? = state.first()

    /**
     * Beginnt eine Sitzung. Mit Statusangabe im Filter ([MediaFilter.statuses]) ist es ein erneutes Sortieren
     * bereits entschiedener Medien ([QueuePhase.ERNEUT]); der Sitzungsbeginn ist dann der Stichtag.
     */
    suspend fun start(filter: MediaFilter, now: Long): SessionState {
        val review = filter.statuses != null
        val session = SessionState(
            filter, now,
            if (review) QueuePhase.ERNEUT else QueuePhase.UNBEARBEITET,
            if (review) now else 0L
        )
        write(session)
        return session
    }

    suspend fun enterDeferredPhase(session: SessionState, now: Long): SessionState {
        val updated = session.copy(phase = QueuePhase.ZURUECKGESTELLT, phase2StartedAt = now)
        write(updated)
        return updated
    }

    suspend fun setPhase(session: SessionState, phase: QueuePhase): SessionState {
        val updated = session.copy(phase = phase)
        write(updated)
        return updated
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it[Keys.ACTIVE] = false }
    }

    private suspend fun write(session: SessionState) {
        context.sessionDataStore.edit {
            it[Keys.ACTIVE] = true
            it[Keys.FILTER] = FilterCodec.encode(session.filter)
            it[Keys.STARTED] = session.startedAt
            it[Keys.PHASE] = encodePhase(session.phase)
            it[Keys.PHASE2] = session.phase2StartedAt
        }
    }
}
