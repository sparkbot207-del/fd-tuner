package com.bretthalliday.fdtuner.ble

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    data class Scanning(val found: Int = 0) : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val deviceName: String, val deviceAddress: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
