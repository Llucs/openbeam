package org.openbeam.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WpsInfo
import android.net.wifi.p2p.WpsInfo
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.openbeam.core.HandshakeManager
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferMetadata
import org.openbeam.core.history.HistoryRepository
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class WifiDirectTransport(private val context: Context) {
    private val manager: WifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel = manager.initialize(context, context.mainLooper, null)!!

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    fun registerReceiver() {
        if (receiver != null) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        if (state != WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                            Log.w("WifiDirectTransport", "Wi‑Fi Direct is disabled")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager.requestPeers(channel) { peersList ->
                            _peers.value = peersList.deviceList.toList()
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        manager.requestConnectionInfo(channel) { info ->
                            _connectionInfo.value = info
                        }
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        // Do nothing special
                    }
                }
            }
        }
        context.registerReceiver(receiver, filter)
    }

    fun unregisterReceiver() {
        receiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (ignored: Exception) {
            }
        }
        receiver = null
    }

    fun discoverPeers() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirectTransport", "Peer discovery initiated")
            }
            override fun onFailure(reason: Int) {
                Log.e("WifiDirectTransport", "Peer discovery failed: $reason")
            }
        })
    }

    fun connect(device: WifiP2pDevice) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirectTransport", "Connection initiated")
            }
            override fun onFailure(reason: Int) {
                Log.e("WifiDirectTransport", "Connection failed: $reason")
            }
        })
    }

    fun createGroup() {
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirectTransport", "Group created successfully")
            }
            override fun onFailure(reason: Int) {
                Log.e("WifiDirectTransport", "Group creation failed: $reason")
            }
        })
    }

    suspend fun transfer(
        role: Role,
        token: SessionToken,
        metadata: TransferMetadata?,
        files: List<Uri>,
        historyRepository: HistoryRepository,
        updateProgress: (Long, Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val info = withTimeoutOrNull(30000) { connectionInfo.filterNotNull().first() }
                ?: throw IllegalStateException("Connection info not available within timeout")
            val isGroupOwner = info.isGroupOwner
            val address = info.groupOwnerAddress?.hostAddress
                ?: throw IllegalStateException("No group owner address")
            Log.d("WifiDirectTransport", "Connected: groupOwner=$isGroupOwner, address=$address")
            if (role == Role.SENDER) {
                if (isGroupOwner) {
                    ServerSocket(8988).use { serverSocket ->
                        serverSocket.accept().use { s ->
                            sendData(s.getOutputStream(), metadata!!, files, token, updateProgress)
                        }
                    }
                } else {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(address, 8988), 15000)
                        sendData(s.getOutputStream(), metadata!!, files, token, updateProgress)
                    }
                }
                val totalSize = metadata?.size ?: 0L
                historyRepository.addEntry(
                    org.openbeam.core.history.HistoryEntry(
                        name = metadata?.name ?: "",
                        size = totalSize,
                        timestamp = System.currentTimeMillis(),
                        direction = "send"
                    )
                )
            } else {
                if (isGroupOwner) {
                    ServerSocket(8988).use { serverSocket ->
                        serverSocket.accept().use { s ->
                            val (receivedMetadata, totalSize) = receiveData(s.getInputStream(), token, updateProgress)
                            historyRepository.addEntry(
                                org.openbeam.core.history.HistoryEntry(
                                    name = receivedMetadata.name,
                                    size = totalSize,
                                    timestamp = System.currentTimeMillis(),
                                    direction = "receive"
                                )
                            )
                        }
                    }
                } else {
                    Socket().use { s ->
                        s.connect(InetSocketAddress(address, 8988), 15000)
                        val (receivedMetadata, totalSize) = receiveData(s.getInputStream(), token, updateProgress)
                        historyRepository.addEntry(
                            org.openbeam.core.history.HistoryEntry(
                                name = receivedMetadata.name,
                                size = totalSize,
                                timestamp = System.currentTimeMillis(),
                                direction = "receive"
                            )
                        )
                    }
                }
            }
        }
    }

    private fun sendData(
        output: OutputStream,
        metadata: TransferMetadata,
        files: List<Uri>,
        token: SessionToken,
        updateProgress: (Long, Long) -> Unit
    ) {
        try {
            // Handshake
            val handshake = HandshakeManager.createHandshakeMessage(token, metadata)
            val handshakeLength = handshake.size
            output.write(intToByteArray(handshakeLength))
            output.write(handshake)

            // Send number of files as 4 bytes
            output.write(intToByteArray(files.size))

            var bytesTransferred = 0L
            val totalSize = metadata.size
            // For each URI, open InputStream and send data
            files.forEach { uri ->
                val name = getFileNameFromUri(uri)
                val fileSize = getFileSizeFromUri(uri)
                // Write name length and name
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                output.write(intToByteArray(nameBytes.size))
                output.write(nameBytes)
                // Write file size
                output.write(longToByteArray(fileSize))

                // Copy file bytes
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var localTransferred = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesTransferred += read
                        localTransferred += read
                        updateProgress(bytesTransferred, totalSize)
                    }
                }
            }
            output.flush()
        } catch (e: Exception) {
            Log.e("WifiDirectTransport", "Error sending data", e)
        }
    }

    private fun receiveData(
        input: InputStream,
        token: SessionToken,
        updateProgress: (Long, Long) -> Unit
    ): Pair<TransferMetadata, Long> {
        var totalBytes = 0L
        val metadata: TransferMetadata
        try {
            // Read handshake length (4 bytes)
            val lengthBytes = ByteArray(4)
            input.readFully(lengthBytes)
            val handshakeLength = byteArrayToInt(lengthBytes)
            val handshakeBytes = ByteArray(handshakeLength)
            input.readFully(handshakeBytes)
            // Parse handshake
            metadata = HandshakeManager.parseHandshakeMessage(token, handshakeBytes)
            // Read number of files
            val countBytes = ByteArray(4)
            input.readFully(countBytes)
            val fileCount = byteArrayToInt(countBytes)
            var bytesReceived = 0L
            val totalSize = metadata.size
            for (i in 0 until fileCount) {
                // Read file name length and name
                val nameLenBytes = ByteArray(4)
                input.readFully(nameLenBytes)
                val nameLen = byteArrayToInt(nameLenBytes)
                val nameBytes = ByteArray(nameLen)
                input.readFully(nameBytes)
                val name = String(nameBytes, Charsets.UTF_8)
                // Read file size
                val sizeBytes = ByteArray(8)
                input.readFully(sizeBytes)
                val fileSize = byteArrayToLong(sizeBytes)
                // Create output file
                val destDir: File? = context.getExternalFilesDir(null)
                val outFile = File(destDir, name)
                outFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var remaining = fileSize
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, if (remaining < buffer.size) remaining.toInt() else buffer.size)
                        if (read == -1) break
                        outputStream.write(buffer, 0, read)
                        remaining -= read
                        bytesReceived += read
                        updateProgress(bytesReceived, totalSize)
                    }
                }
            }
            totalBytes = metadata.size
        } catch (e: Exception) {
            Log.e("WifiDirectTransport", "Error receiving data", e)
            throw e
        }
        return metadata to totalBytes
    }

    private fun InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read == -1) throw java.io.EOFException()
            offset += read
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun longToByteArray(value: Long): ByteArray {
        return byteArrayOf(
            ((value ushr 56) and 0xFF).toByte(),
            ((value ushr 48) and 0xFF).toByte(),
            ((value ushr 40) and 0xFF).toByte(),
            ((value ushr 32) and 0xFF).toByte(),
            ((value ushr 24) and 0xFF).toByte(),
            ((value ushr 16) and 0xFF).toByte(),
            ((value ushr 8) and 0xFF).toByte(),
            (value and 0xFF).toByte()
        )
    }

    private fun byteArrayToInt(bytes: ByteArray): Int {
        return (bytes[0].toInt() and 0xFF shl 24) or
                (bytes[1].toInt() and 0xFF shl 16) or
                (bytes[2].toInt() and 0xFF shl 8) or
                (bytes[3].toInt() and 0xFF)
    }

    private fun byteArrayToLong(bytes: ByteArray): Long {
        return ((bytes[0].toLong() and 0xFF) shl 56) or
                ((bytes[1].toLong() and 0xFF) shl 48) or
                ((bytes[2].toLong() and 0xFF) shl 40) or
                ((bytes[3].toLong() and 0xFF) shl 32) or
                ((bytes[4].toLong() and 0xFF) shl 24) or
                ((bytes[5].toLong() and 0xFF) shl 16) or
                ((bytes[6].toLong() and 0xFF) shl 8) or
                (bytes[7].toLong() and 0xFF)
    }

    private fun getFileNameFromUri(uri: Uri): String {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) it.getString(nameIndex) else uri.lastPathSegment ?: "unknown"
            } else uri.lastPathSegment ?: "unknown"
        } ?: uri.lastPathSegment ?: "unknown"
    }

    private fun getFileSizeFromUri(uri: Uri): Long {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (sizeIndex != -1) it.getLong(sizeIndex) else 0L
            } else 0L
        } ?: 0L
    }

    enum class Role { SENDER, RECEIVER }
}