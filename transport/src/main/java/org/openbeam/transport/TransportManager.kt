package org.openbeam.transport

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.StateFlow
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferMetadata
import org.openbeam.core.history.HistoryRepository

/**
 * Coordinates selection and use of available transports (Wi‑Fi Direct or Bluetooth). The
 * selected transport is based on the session token parameters; if Wi‑Fi is not available
 * or discovery fails, Bluetooth is used as a fallback.
 */
class TransportManager(private val context: Context) {
    private val wifiTransport by lazy { WifiDirectTransport(context) }
    private val bluetoothTransport by lazy { BluetoothTransport(context) }

    /** Returns the current list of Wi‑Fi peers. */
    val wifiPeers: StateFlow<List<android.net.wifi.p2p.WifiP2pDevice>> get() = wifiTransport.peers
    /** Returns the current list of discoverable Bluetooth devices. */
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

    /** Creates a Wi‑Fi Direct group. Use this before receiving files. */
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
            // Use Bluetooth. We need a device selection; choose first discovered device.
            val device = bluetoothTransport.devices.value.firstOrNull()
            if (device != null) {
                bluetoothTransport.connect(device, BluetoothTransport.Role.valueOf(role.name), token, metadata, files, historyRepository, updateProgress)
            } else {
                // no device found; fallback to wifi anyway
                wifiTransport.transfer(role, token, metadata, files, historyRepository, updateProgress)
            }
        }
    }
}