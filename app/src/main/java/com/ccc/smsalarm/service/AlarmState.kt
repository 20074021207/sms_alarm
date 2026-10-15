package com.ccc.smsalarm.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AlarmState {
    data class AlarmInfo(
        val active: Boolean = false,
        val sender: String = "",
        val body: String = ""
    )

    private val _state = MutableStateFlow(AlarmInfo())
    val state: StateFlow<AlarmInfo> = _state

    fun trigger(sender: String, body: String) {
        _state.value = AlarmInfo(active = true, sender = sender, body = body)
    }

    fun clear() {
        _state.value = AlarmInfo()
    }
}
