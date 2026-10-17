package com.pmfby.evidence.app.ui.screens

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pmfby.evidence.app.data.local.OfflineEventDao
import com.pmfby.evidence.app.data.local.OfflineEventEntity
import com.pmfby.evidence.app.work.UploadWorker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Offline upload/event queue: never claims success before WorkManager's UploadWorker gets
 * a confirmed server response (see CaseActionsRepository/UploadWorker — a row starts
 * 'pending', becomes 'uploaded' only after the real HTTP call succeeds, or 'failed' with
 * [OfflineEventEntity.lastError] set, and is retried automatically with the SAME
 * idempotency key, never a fresh one, so a flaky retry can't double-create server rows).
 */
class UploadQueueViewModel(private val appContext: Context, private val offlineEventDao: OfflineEventDao) : ViewModel() {

    val queue: StateFlow<List<OfflineEventEntity>> = offlineEventDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun retryNow() {
        UploadWorker.runOnce(appContext)
    }

    fun clearUploaded() {
        viewModelScope.launch { offlineEventDao.clearUploaded() }
    }
}
