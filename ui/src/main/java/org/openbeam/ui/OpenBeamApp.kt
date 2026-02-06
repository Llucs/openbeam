package org.openbeam.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferMetadata
import org.openbeam.core.TransferType
import org.openbeam.core.history.HistoryRepository
import org.openbeam.transport.WifiDirectTransport
import org.openbeam.transport.BluetoothTransport

/**
 * Entry point composable for the UI layer. Sets up a navigation host and provides screens for
 * sending, receiving, and viewing history.
 */
@Composable
fun OpenBeamApp(
    transportManager: org.openbeam.transport.TransportManager,
    nfcController: org.openbeam.nfc.NfcController
) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {
        composable("home") { HomeScreen(navController) }
        composable("send") {
            SendScreen(navController, transportManager, nfcController)
        }
        composable("receive") {
            ReceiveScreen(navController, transportManager, nfcController)
        }
        composable("history") { HistoryScreen(navController) }
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("OpenBeam")
        Button(onClick = { navController.navigate("send") }) {
            Text("Enviar Arquivo")
        }
        Button(onClick = { navController.navigate("receive") }) {
            Text("Receber Arquivo")
        }
        Button(onClick = { navController.navigate("history") }) {
            Text("Histórico")
        }
    }
}

/**
 * Screen for sending files. Allows selection of a file and initiates the NFC handshake.
 */
@Composable
fun SendScreen(
    navController: NavHostController,
    transportManager: org.openbeam.transport.TransportManager,
    nfcController: org.openbeam.nfc.NfcController
) {
    val context = LocalContext.current
    val historyRepo = remember { org.openbeam.core.history.HistoryRepository.getInstance(context) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var metadata by remember { mutableStateOf<TransferMetadata?>(null) }
    var token by remember { mutableStateOf<SessionToken?>(null) }
    // Track whether a transfer is ongoing and the current progress ratio
    var isTransferring by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    // Observe lists of discovered peers. These StateFlows emit changes as discovery progresses.
    val wifiPeers by transportManager.wifiPeers.collectAsState()
    val bluetoothDevices by transportManager.bluetoothDevices.collectAsState()

    // Use a remembered coroutine scope tied to the composition to launch asynchronous work.
    val coroutineScope = rememberCoroutineScope()

    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            selectedUris = uris
            if (uris.isNotEmpty()) {
                val names = uris.map { uri -> getFileName(context, uri) }
                val totalSize = uris.sumOf { uri -> getFileSize(context, uri) }
                val displayName = if (uris.size == 1) names.first() else "${uris.size} arquivos"
                val type = if (uris.size == 1) TransferType.FILE else TransferType.MULTIPLE_FILES
                metadata = TransferMetadata(name = displayName, size = totalSize, uris = uris)
                token = null
            }
        }
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enviar arquivos")
        Button(onClick = {
            filePicker.launch(arrayOf("*/*"))
        }) {
            Text("Selecionar arquivos")
        }
        if (selectedUris.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("Selecionados:")
                selectedUris.forEach { uri ->
                    Text("- ${getFileName(context, uri)}")
                }
            }
        }
        metadata?.let { md ->
            // Generate a new session token and enable NFC push when the user is ready.
            Button(onClick = {
                val params = mutableMapOf<String, String>()
                params["transport"] = "wifi" // default transport
                val newToken = SessionToken.generate(
                    if (md.uris.size > 1) TransferType.MULTIPLE_FILES else TransferType.FILE,
                    params
                )
                token = newToken
                // Provide the token via NFC so the receiver knows the session parameters.
                nfcController.enableWrite(newToken)
            }) {
                Text("Gerar Token NFC")
            }
        }
        token?.let { tk ->
            // Inform the user that the token has been generated and to tap devices.
            Text("Token gerado:\n${tk.id}\nEncoste o dispositivo receptor.")
            // Discovery and connection controls appear only when a token exists and no transfer is running.
            if (!isTransferring) {
                Button(onClick = {
                    // Begin Wi‑Fi Direct peer discovery. Results will populate wifiPeers.
                    transportManager.discoverWifiPeers()
                }) {
                    Text("Buscar dispositivos Wi‑Fi Direct")
                }
                // List discovered Wi‑Fi peers and allow the user to select one to connect and send.
                if (wifiPeers.isNotEmpty()) {
                    Text("Toque em um dispositivo para conectar e enviar:")
                    wifiPeers.forEach { device ->
                        Button(
                            onClick = {
                                // Immediately connect and start the transfer. The underlying transport
                                // waits for connection info internally.
                                transportManager.connectWifi(device)
                                // Launch the transfer in a coroutine so that the UI remains responsive.
                                coroutineScope.launch {
                                    isTransferring = true
                                    // Start sending via Wi‑Fi Direct using the generated token and metadata.
                                    transportManager.transfer(
                                        role = WifiDirectTransport.Role.SENDER,
                                        token = tk,
                                        metadata = metadata,
                                        files = selectedUris,
                                        historyRepository = historyRepo
                                    ) { transferred, total ->
                                        progress = if (total > 0) transferred.toFloat() / total else 0f
                                    }
                                    isTransferring = false
                                    // After completion reset state and disable NFC push
                                    nfcController.disableWrite()
                                    selectedUris = emptyList()
                                    metadata = null
                                    token = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(device.deviceName ?: device.deviceAddress)
                        }
                    }
                }
                // Show Bluetooth devices for fallback if any discovered.
                if (bluetoothDevices.isNotEmpty()) {
                    Text("Dispositivos Bluetooth disponíveis (fallback):")
                    bluetoothDevices.forEach { device ->
                        Button(
                            onClick = {
                                // Switch to Bluetooth transport by updating the token parameter.
                                val btToken = tk.copy(params = tk.params + ("transport" to "bluetooth"))
                                token = btToken
                                // Launch transfer over Bluetooth
                                coroutineScope.launch {
                                    isTransferring = true
                                    transportManager.bluetooth.connect(
                                        device = device,
                                        role = BluetoothTransport.Role.SENDER,
                                        token = btToken,
                                        metadata = metadata,
                                        files = selectedUris,
                                        historyRepository = historyRepo
                                    ) { transferred, total ->
                                        progress = if (total > 0) transferred.toFloat() / total else 0f
                                    }
                                    isTransferring = false
                                    nfcController.disableWrite()
                                    selectedUris = emptyList()
                                    metadata = null
                                    token = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Text(device.name ?: device.address)
                        }
                    }
                }
            }
        }
        // Display progress when a transfer is in progress.
        if (isTransferring) {
            Text("Transferindo... ${(progress * 100).toInt()}%")
        }
        // Always include a back button to return to the home screen.
        Button(onClick = { navController.popBackStack() }) {
            Text("Voltar")
        }
    }
}

/**
 * Screen for receiving a file. Waits for an NFC token and then establishes a transport channel
 * to receive the data. In this simplified implementation, we just display a message.
 */
@Composable
fun ReceiveScreen(
    navController: NavHostController,
    transportManager: org.openbeam.transport.TransportManager,
    nfcController: org.openbeam.nfc.NfcController
) {
    val context = LocalContext.current
    val historyRepo = remember { org.openbeam.core.history.HistoryRepository.getInstance(context) }
    var tokenReceived by remember { mutableStateOf<SessionToken?>(null) }
    var isReceiving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var metadata by remember { mutableStateOf<TransferMetadata?>(null) }

    // Enable NFC read when composable enters
    DisposableEffect(Unit) {
        nfcController.enableRead { token ->
            tokenReceived = token
        }
        onDispose {
            nfcController.disableRead()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (tokenReceived == null) {
            Text("Aproxime o dispositivo emissor para ler o token.")
        } else {
            Text("Token recebido: ${tokenReceived!!.id}\nAguardando conexão...")
            // Once token is received, create group and wait for connection
            LaunchedEffect(tokenReceived) {
                // Become group owner to receive
                transportManager.createWifiGroup()
                isReceiving = true
                // Wait for connection and receive data
                transportManager.transfer(
                    role = WifiDirectTransport.Role.RECEIVER,
                    token = tokenReceived!!,
                    metadata = null,
                    files = emptyList(),
                    historyRepository = historyRepo
                ) { transferred, total ->
                    progress = if (total > 0) transferred.toFloat() / total else 0f
                }
                isReceiving = false
                // After transfer, reset state
                tokenReceived = null
            }
            if (isReceiving) {
                Text("Recebendo... ${(progress * 100).toInt()}%")
            }
        }
        Button(onClick = { navController.popBackStack() }) {
            Text("Voltar")
        }
    }
}

@Composable
fun HistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val historyRepo = remember { org.openbeam.core.history.HistoryRepository.getInstance(context) }
    val entries by historyRepo.getAllEntries().collectAsState(initial = emptyList())
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Histórico de transferências")
        entries.forEach { entry ->
            Text("${entry.direction.uppercase()}: ${entry.name} (${entry.size} bytes) em ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date(entry.timestamp))}")
        }
        if (entries.isEmpty()) {
            Text("Sem histórico ainda.")
        }
        Button(onClick = { navController.popBackStack() }) {
            Text("Voltar")
        }
    }
}

/**
 * Helper to get a displayable file name from a URI.
 */
private fun getFileName(context: Context, uri: Uri): String {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index != -1) it.getString(index) else uri.lastPathSegment ?: "unknown"
        } else uri.lastPathSegment ?: "unknown"
    } ?: uri.lastPathSegment ?: "unknown"
}

private fun getFileSize(context: Context, uri: Uri): Long {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (index != -1) it.getLong(index) else 0L
        } else 0L
    } ?: 0L
}
}