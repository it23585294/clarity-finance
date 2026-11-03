package com.clarity.finance.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/** Koin creates dependencies; the current AndroidX owner retains/clears the ViewModel and supplies extras. */
@Composable
inline fun <reified T : ViewModel> injectedViewModel(graph: Koin): T {
    val factory = remember(graph, T::class) {
        graph.get<ViewModelProvider.Factory> { parametersOf(T::class) }
    }
    return viewModel(factory = factory)
}
