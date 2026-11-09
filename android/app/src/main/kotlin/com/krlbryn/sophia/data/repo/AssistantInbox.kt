package com.krlbryn.sophia.data.repo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Text handed to the Study Assistant from the browser or the Android share sheet. Consumed once. */
@Singleton
class AssistantInbox @Inject constructor() {
    data class Item(val text: String, val source: String)

    private val _pending = MutableStateFlow<Item?>(null)
    val pending: StateFlow<Item?> = _pending.asStateFlow()

    fun send(text: String, source: String) {
        _pending.value = Item(text, source)
    }

    fun take(): Item? = _pending.value.also { _pending.value = null }
}
