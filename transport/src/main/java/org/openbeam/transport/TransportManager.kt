package org.openbeam.transport

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferMetadata
import org.openbeam.core.history.HistoryRepository

class TransportManager(private val context: Context) {
    private val wifiTransport by lazy { WifiDirectTransport(context) }
    private val bluetoothTransport by lazy { BluetoothTransport(context) }

    val wifiPeers: StateFlow<List<android.net.wifi.p2p.WifiP2pDevice>> get() = wifiTransport.peers
    val bluetoothDevices: StateFlow<List<android.bluetooth.BluetoothDevice>> get() = bluetoothTransport.devices

    val bluetooth: BluetoothTransport get() = bluetoothTransport

    fun registerReceivers() {
        wifiTransport.registerReceiver()
        bluetoothTransport.registerReceiver()
    }

    fun unregisterReceivers() {
        wifiTransport.unregisterReceiver()
        bluetoothTransport.unregisterReceiver()
    }

    fun discoverWifiPeers() {
        wifiTransport.discoverPeers()
    }

    fun discoverBluetoothDevices() {
        bluetoothTransport.discoverDevices()
    }

    fun connectWifi(device: android.net.wifi.p2p.WifiP2pDevice) {
        wifiTransport.connect(device)
    }

    fun createWifiGroup() {
        wifiTransport.createGroup()
    }

    suspend fun transfer(
        role: WifiDirectTransport.Role,
        token: SessionToken,
        metadata: TransferMetadata?,
        files: List<Uri>,
        historyRepository: HistoryRepository,
        updateProgress: (Long, Long) -> Unit
    ) {
        val transportParam = token.params["transport"] ?: "wifi"
        if (transportParam == "wifi") {
            wifiTransport.transfer(role, token, metadata, files, historyRepository, updateProgress)
        } else {
            val device = bluetoothTransport.devices.value.firstOrNull()
            if (device != null) {
                val btRole = if (role == WifiDirectTransport.Role.SENDER) BluetoothTransport.Role.SENDER else BluetoothTransport.Role.RECEIVER
                bluetoothTransport.connect(device, btRole, token, metadata, files, historyRepository, updateProgress)
            } else {
                wifiTransport.transfer(role, token, metadata, files, historyRepository, updateProgress)
            }
        }
    }
}