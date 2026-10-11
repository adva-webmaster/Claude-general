@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.paging.compose

import androidx.compose.runtime.Composable
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class LazyPagingItems<T : Any> {
    val itemCount: Int get() = 0
    operator fun get(index: Int): T? = null
}

@Composable
fun <T : Any> Flow<PagingData<T>>.collectAsLazyPagingItems(): LazyPagingItems<T> = LazyPagingItems()

fun <T : Any> LazyPagingItems<T>.itemKey(key: ((item: T) -> Any)? = null): (index: Int) -> Any = { it }
