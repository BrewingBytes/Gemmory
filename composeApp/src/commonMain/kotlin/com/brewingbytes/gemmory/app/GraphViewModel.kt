package com.brewingbytes.gemmory.app

import androidx.compose.runtime.Composable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * A screen's ViewModel (iOS eng D5), created on first use with the [AppGraph] and its [SavedStateHandle], then kept
 * by the nearest ViewModelStoreOwner as `viewModel()` does: across rotation on Android, restored after process death
 * from the handle; in memory on iOS. Its services come in through [create], never from a platform context.
 */
@Composable
inline fun <reified VM : ViewModel> graphViewModel(key: String? = null, noinline create: AppGraph.(SavedStateHandle) -> VM): VM {
    val graph = LocalAppGraph.current
    return viewModel(key = key) { graph.create(createSavedStateHandle()) }
}
