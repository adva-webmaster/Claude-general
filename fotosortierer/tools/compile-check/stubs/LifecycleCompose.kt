@file:Suppress("unused", "UNUSED_PARAMETER")
package androidx.lifecycle.viewmodel.compose

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

@Composable
inline fun <reified VM : ViewModel> viewModel(key: String? = null, factory: ViewModelProvider.Factory? = null): VM = error("stub")
