@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.datastore.preferences.core

import androidx.datastore.core.DataStore

abstract class Preferences {
    class Key<T> internal constructor(val name: String)
    abstract operator fun <T> get(key: Key<T>): T?
}

abstract class MutablePreferences : Preferences() {
    abstract operator fun <T> set(key: Key<T>, value: T)
}

fun booleanPreferencesKey(name: String): Preferences.Key<Boolean> = error("stub")
fun intPreferencesKey(name: String): Preferences.Key<Int> = error("stub")
fun longPreferencesKey(name: String): Preferences.Key<Long> = error("stub")
fun stringPreferencesKey(name: String): Preferences.Key<String> = error("stub")
fun stringSetPreferencesKey(name: String): Preferences.Key<Set<String>> = error("stub")

suspend fun DataStore<Preferences>.edit(transform: suspend (MutablePreferences) -> Unit): Preferences = error("stub")
