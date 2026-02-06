package org.openbeam.transport

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.openbeam.core.HandshakeManager
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferMetadata
import org.openbeam.core.history.HistoryRepository
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * Implements Bluetooth classic transport for OpenBeam. This class discovers nearby devices,
 * establishes a connection via RFCOMM and transfers file(s) using the same protocol as
 * Wi‑Fi Direct.
 */
class BluetoothTransport(private val context: Context) {
    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private val uuid: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")

    private val _devices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val devices: StateFlow<List<BluetoothDevice>> = _devices.asStateFlow()

    private var receiver: BroadcastReceiver? = null

    /** Registers broadcast receiver for discovery results. */
    fun registerReceiver() {
        if (receiver != null) return
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        device?.let {
                            // Append to list
                            _devices.value = _devices.value + it
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        Log.d("BluetoothTransport", "Discovery finished")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(receiver, filter)
    }

    /** Unregisters broadcast receiver. */
    fun unregisterReceiver() {
        receiver?.let {
            try { context.unregisterReceiver(it) } catch (ignored: Exception) {}
        }
        receiver = null
    }

    /** Starts discovery of nearby Bluetooth devices. */
    fun discoverDevices() {
        adapter?.takeIf { it.isEnabled }?.apply {
            if (isDiscovering) cancelDiscovery()
            startDiscovery()
            _devices.value = emptyList()
        }
    }

    /** Connects to the given device. */
    suspend fun connect(
        device: BluetoothDevice,
        role: Role,
        token: SessionToken,
        metadata: TransferMetadata?,
        files: List<Uri>,
        historyRepository: HistoryRepository,
        updateProgress: (Long, Long) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            adapter ?: throw IllegalStateException("Bluetooth not supported")
            if (!adapter.isEnabled) {
                throw IllegalStateException("Bluetooth is disabled")
            }
            if (role == Role.SENDER) {
                // Sender acts as client; connect to server on receiver side
                var socket: BluetoothSocket? = null
                try {
                    socket = device.createRfcommSocketToServiceRecord(uuid)
                    adapter.cancelDiscovery()
                    socket.connect()
                    val output = socket.outputStream
                    // Send data
                    sendData(output, metadata!!, files, token, updateProgress)
                    // Record history
                    historyRepository.addEntry(
                        org.openbeam.core.history.HistoryEntry(
                            name = metadata.name,
                            size = metadata.size,
                            timestamp = System.currentTimeMillis(),
                            direction = "send"
                        )
                    )
                } finally {
                    try { socket?.close() } catch (ignored: Exception) {}
                }
            } else {
                // Receiver acts as server
                var server: BluetoothServerSocket? = null
                var socket: BluetoothSocket? = null
                try {
                    server = adapter.listenUsingRfcommWithServiceRecord("OpenBeam", uuid)
                    socket = server.accept()
                    val input = socket.inputStream
                    val (receivedMetadata, totalSize) = receiveData(input, token, updateProgress)
                    // Save history
                    historyRepository.addEntry(
                        org.openbeam.core.history.HistoryEntry(
                            name = receivedMetadata.name,
                            size = totalSize,
                            timestamp = System.currentTimeMillis(),
                            direction = "receive"
                        )
                    )
                } finally {
                    try { socket?.close() } catch (ignored: Exception) {}
                    try { server?.close() } catch (ignored: Exception) {}
                }
            }
        }
    }

    /**
     * Writes handshake and file data to the output stream using the same protocol as Wi‑Fi Direct.
     */
    private fun sendData(
        output: OutputStream,
        metadata: TransferMetadata,
        files: List<Uri>,
        token: SessionToken,
        updateProgress: (Long, Long) -> Unit
    ) {
        // Delegate to same implementation as WifiDirectTransport
        // We can't call static method there, so duplicate logic here
        try {
            val handshake = HandshakeManager.createHandshakeMessage(token, metadata)
            output.write(intToByteArray(handshake.size))
            output.write(handshake)
            output.write(intToByteArray(files.size))
            var bytesTransferred = 0L
            val totalSize = metadata.size
            files.forEach { uri ->
                val name = getFileNameFromUri(uri)
                val size = getFileSizeFromUri(uri)
                val nameBytes = name.toByteArray(Charsets.UTF_8)
                output.write(intToByteArray(nameBytes.size))
                output.write(nameBytes)
                output.write(longToByteArray(size))
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesTransferred += read
                        updateProgress(bytesTransferred, totalSize)
                    }
                }
            }
            output.flush()
        } catch (e: Exception) {
            Log.e("BluetoothTransport", "Error sending data", e)
        }
    }

    /**
     * Reads handshake and file data from the input stream. Returns metadata and total bytes received.
     */
    private fun receiveData(
        input: InputStream,
        token: SessionToken,
        updateProgress: (Long, Long) -> Unit
    ): Pair<TransferMetadata, Long> {
        var totalBytes = 0L
        val metadata: TransferMetadata
        try {
            val lenBytes = ByteArray(4)
            input.readFully(lenBytes)
            val handshakeLength = byteArrayToInt(lenBytes)
            val handshakeBytes = ByteArray(handshakeLength)
            input.readFully(handshakeBytes)
            metadata = HandshakeManager.parseHandshakeMessage(token, handshakeBytes)
            val countBytes = ByteArray(4)
            input.readFully(countBytes)
            val fileCount = byteArrayToInt(countBytes)
            var bytesReceived = 0L
            val totalSize = metadata.size
            for (i in 0 until fileCount) {
                val nameLenBytes = ByteArray(4)
                input.readFully(nameLenBytes)
                val nameLen = byteArrayToInt(nameLenBytes)
                val nameBytes = ByteArray(nameLen)
                input.readFully(nameBytes)
                val name = String(nameBytes, Charsets.UTF_8)
                val sizeBytes = ByteArray(8)
                input.readFully(sizeBytes)
                val fileSize = byteArrayToLong(sizeBytes)
                val destDir = context.getExternalFilesDir(null)
                val outFile = java.io.File(destDir, name)
                outFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var remaining = fileSize
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, if (remaining < buffer.size) remaining.toInt() else buffer.size)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        remaining -= read
                        bytesReceived += read
                        updateProgress(bytesReceived, totalSize)
                    }
                }
            }
            totalBytes = metadata.size
        } catch (e: Exception) {
            Log.e("BluetoothTransport", "Error receiving data", e)
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
                val index = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (index != -1) it.getLong(index) else 0L
            } else 0L
        } ?: 0L
    }

    enum class Role { SENDER, RECEIVER }
}