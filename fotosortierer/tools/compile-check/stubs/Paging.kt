@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.paging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

abstract class PagingSource<Key : Any, Value : Any>

class PagingConfig(
    val pageSize: Int,
    val prefetchDistance: Int = pageSize,
    val enablePlaceholders: Boolean = true,
    val initialLoadSize: Int = pageSize * 3,
    val maxSize: Int = Int.MAX_VALUE
)

class PagingData<T : Any>

class Pager<Key : Any, Value : Any>(
    config: PagingConfig,
    initialKey: Key? = null,
    pagingSourceFactory: () -> PagingSource<Key, Value>
) {
    val flow: Flow<PagingData<Value>> get() = error("stub")
}

fun <T : Any> Flow<PagingData<T>>.cachedIn(scope: CoroutineScope): Flow<PagingData<T>> = this
