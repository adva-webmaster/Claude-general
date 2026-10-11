@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.lifecycle.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner> = compositionLocalOf { error("stub") }

@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> = error("stub")

@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> = error("stub")
