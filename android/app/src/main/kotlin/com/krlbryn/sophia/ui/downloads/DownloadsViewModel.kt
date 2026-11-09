package com.krlbryn.sophia.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krlbryn.sophia.data.repo.DownloadItem
import com.krlbryn.sophia.data.repo.DownloadsRepository
import com.krlbryn.sophia.data.repo.EnqueueResult
import com.krlbryn.sophia.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloads: DownloadsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    /** Null until the first query returns, so the screen does not flash "no downloads". */
    val items: StateFlow<List<DownloadItem>?> =
        downloads.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    fun open(item: DownloadItem): Boolean = downloads.open(item)

    fun cancel(item: DownloadItem) = viewModelScope.launch { downloads.cancel(item) }

    fun remove(item: DownloadItem, deleteFile: Boolean) = viewModelScope.launch { downloads.remove(item, deleteFile) }

    fun retry(item: DownloadItem, userAgent: String?, onResult: (String) -> Unit) = viewModelScope.launch {
        when (val r = downloads.retry(item, userAgent, settings.current().downloadLocation)) {
            is EnqueueResult.Started -> onResult("Downloading ${r.fileName} again")
            is EnqueueResult.Rejected -> onResult(r.message)
        }
    }
}
