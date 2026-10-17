package com.pmfby.evidence.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pmfby.evidence.app.PmfbyEvidenceApp

/**
 * Tiny manual ViewModel factory: no Hilt in this pass (see PmfbyEvidenceApp doc), so every
 * screen builds its ViewModel from [LocalApp]'s repositories via this one-line factory
 * instead of each needing its own bespoke `ViewModelProvider.Factory` class.
 */
class SimpleViewModelFactory(private val creator: () -> ViewModel) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
}

/** The current [PmfbyEvidenceApp] instance — every screen's single entry point to repositories. */
@Composable
fun LocalApp(): PmfbyEvidenceApp = LocalContext.current.applicationContext as PmfbyEvidenceApp

@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline creator: (PmfbyEvidenceApp) -> VM): VM {
    val app = LocalApp()
    return viewModel(factory = SimpleViewModelFactory { creator(app) })
}
