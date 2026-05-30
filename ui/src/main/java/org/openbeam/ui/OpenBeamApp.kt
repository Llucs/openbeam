package org.openbeam.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferMetadata
import org.openbeam.core.TransferType
import org.openbeam.core.history.HistoryEntry
import org.openbeam.core.history.HistoryRepository
import org.openbeam.transport.BluetoothTransport
import org.openbeam.transport.WifiDirectTransport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OpenBeamApp(
    transportManager: org.openbeam.transport.TransportManager,
    nfcController: org.openbeam.nfc.NfcController,
    sharedUris: List<Uri> = emptyList(),
    onStartService: () -> Unit = {},
    onStopService: () -> Unit = {}
) {
    val navController = rememberNavController()
    val startDest = if (sharedUris.isNotEmpty()) "send" else "home"
    NavHost(navController = navController, startDestination = startDest) {
        composable("home") { HomeScreen(navController) }
        composable("send") {
            SendScreen(
                navController = navController,
                transportManager = transportManager,
                nfcController = nfcController,
                preSelectedUris = sharedUris,
                onStartService = onStartService,
                onStopService = onStopService
            )
        }
        composable("receive") {
            ReceiveScreen(
                navController = navController,
                transportManager = transportManager,
                nfcController = nfcController,
                onStartService = onStartService,
                onStopService = onStopService
            )
        }
        composable("history") { HistoryScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
    }
}

@Composable
fun HomeScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Nfc,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "OpenBeam",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        MenuCard(
            icon = Icons.Default.Send,
            title = "Enviar Arquivo",
            description = "Selecione arquivos e transfira para dispositivos próximos",
            onClick = { navController.navigate("send") }
        )
        MenuCard(
            icon = Icons.Default.Wifi,
            title = "Receber Arquivo",
            description = "Aproxime o dispositivo para receber",
            onClick = { navController.navigate("receive") }
        )
        MenuCard(
            icon = Icons.Default.History,
            title = "Histórico",
            description = "Visualize transferências anteriores",
            onClick = { navController.navigate("history") }
        )
        MenuCard(
            icon = Icons.Default.Settings,
            title = "Configurações",
            description = "Preferências do aplicativo",
            onClick = { navController.navigate("settings") }
        )
    }
}

@Composable
private fun MenuCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SendScreen(
    navController: NavHostController,
    transportManager: org.openbeam.transport.TransportManager,
    nfcController: org.openbeam.nfc.NfcController,
    preSelectedUris: List<Uri> = emptyList(),
    onStartService: () -> Unit = {},
    onStopService: () -> Unit = {}
) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository.getInstance(context) }
    var selectedUris by remember { mutableStateOf(preSelectedUris) }
    var metadata by remember { mutableStateOf<TransferMetadata?>(null) }
    var token by remember { mutableStateOf<SessionToken?>(null) }
    var isTransferring by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var transferredBytes by remember { mutableStateOf(0L) }
    var totalBytes by remember { mutableStateOf(0L) }

    val wifiPeers by transportManager.wifiPeers.collectAsState()
    val bluetoothDevices by transportManager.bluetoothDevices.collectAsState()

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(preSelectedUris) {
        if (preSelectedUris.isNotEmpty()) {
            selectedUris = preSelectedUris
            val names = preSelectedUris.map { getFileName(context, it) }
            val totalSize = preSelectedUris.sumOf { getFileSize(context, it) }
            val displayName = if (preSelectedUris.size == 1) names.first() else "${preSelectedUris.size} arquivos"
            metadata = TransferMetadata(displayName, totalSize, preSelectedUris)
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
        onResult = { uris ->
            selectedUris = uris
            if (uris.isNotEmpty()) {
                val names = uris.map { getFileName(context, it) }
                val totalSize = uris.sumOf { getFileSize(context, it) }
                val displayName = if (uris.size == 1) names.first() else "${uris.size} arquivos"
                metadata = TransferMetadata(displayName, totalSize, uris)
                token = null
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Enviar arquivos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        if (!isTransferring) {
            Button(
                onClick = { filePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Selecionar arquivos")
            }
        }
        if (selectedUris.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Selecionados:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    selectedUris.forEach { uri ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = getFileName(context, uri),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        if (isTransferring) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Transferindo...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        metadata?.let { md ->
            if (!isTransferring) {
                Button(
                    onClick = {
                        val params = mutableMapOf("transport" to "wifi")
                        val newToken = SessionToken.generate(
                            if (md.uris.size > 1) TransferType.MULTIPLE_FILES else TransferType.FILE,
                            params
                        )
                        token = newToken
                        nfcController.enableWrite(newToken)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Nfc, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Gerar Token NFC")
                }
            }
        }
        token?.let { tk ->
            val currentMetadata = metadata
            val currentFiles = selectedUris
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Token gerado",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        tk.id.take(16) + "...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Encoste o dispositivo receptor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (!isTransferring) {
                OutlinedButton(
                    onClick = { transportManager.discoverWifiPeers() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buscar dispositivos Wi-Fi Direct")
                }
                if (wifiPeers.isNotEmpty()) {
                    Text(
                        "Dispositivos Wi-Fi Direct:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    wifiPeers.forEach { device ->
                        ElevatedCard(
                            onClick = {
                                transportManager.connectWifi(device)
                                onStartService()
                                coroutineScope.launch {
                                    isTransferring = true
                                    transportManager.transfer(
                                        role = WifiDirectTransport.Role.SENDER,
                                        token = tk,
                                        metadata = currentMetadata,
                                        files = currentFiles,
                                        historyRepository = historyRepo
                                    ) { transferred, total ->
                                        progress = if (total > 0) transferred.toFloat() / total else 0f
                                        transferredBytes = transferred
                                        totalBytes = total
                                    }
                                    isTransferring = false
                                    onStopService()
                                    nfcController.disableWrite()
                                    selectedUris = emptyList()
                                    metadata = null
                                    token = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Wifi,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    device.deviceName ?: device.deviceAddress,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
                if (bluetoothDevices.isNotEmpty()) {
                    Text(
                        "Dispositivos Bluetooth (fallback):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    bluetoothDevices.forEach { device ->
                        ElevatedCard(
                            onClick = {
                                val btToken = tk.copy(params = tk.params + ("transport" to "bluetooth"))
                                token = btToken
                                onStartService()
                                coroutineScope.launch {
                                    isTransferring = true
                                    transportManager.bluetooth.connect(
                                        device = device,
                                        role = BluetoothTransport.Role.SENDER,
                                        token = btToken,
                                        metadata = currentMetadata,
                                        files = currentFiles,
                                        historyRepository = historyRepo
                                    ) { transferred, total ->
                                        progress = if (total > 0) transferred.toFloat() / total else 0f
                                        transferredBytes = transferred
                                        totalBytes = total
                                    }
                                    isTransferring = false
                                    onStopService()
                                    nfcController.disableWrite()
                                    selectedUris = emptyList()
                                    metadata = null
                                    token = null
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    device.name ?: device.address,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()) {
            Text("Voltar")
        }
    }
}

@Composable
fun ReceiveScreen(
    navController: NavHostController,
    transportManager: org.openbeam.transport.TransportManager,
    nfcController: org.openbeam.nfc.NfcController,
    onStartService: () -> Unit = {},
    onStopService: () -> Unit = {}
) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository.getInstance(context) }
    var tokenReceived by remember { mutableStateOf<SessionToken?>(null) }
    var isReceiving by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    DisposableEffect(Unit) {
        nfcController.enableRead { token ->
            tokenReceived = token
        }
        onDispose {
            nfcController.disableRead()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (tokenReceived == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Aproxime o dispositivo emissor",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "para ler o token NFC",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Token recebido",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Aguardando conexão...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LaunchedEffect(tokenReceived) {
                    transportManager.createWifiGroup()
                    onStartService()
                    isReceiving = true
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
                    onStopService()
                    tokenReceived = null
                }
                if (isReceiving) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(navController: NavHostController) {
    val context = LocalContext.current
    val historyRepo = remember { HistoryRepository.getInstance(context) }
    val entries by historyRepo.getAllEntries().collectAsState(initial = emptyList())
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Histórico",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Nenhuma transferência ainda",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryItem(entry, dateFormat)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntry, dateFormat: SimpleDateFormat) {
    val isSent = entry.direction == "send"
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isSent) Icons.Default.Send else Icons.Default.Wifi,
                contentDescription = null,
                tint = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSent) "Enviado" else "Recebido",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateFormat.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatSize(entry.size),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsScreen(navController: NavHostController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Configurações",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsCard(
                icon = Icons.Default.Wifi,
                title = "Transporte padrão",
                description = "Wi-Fi Direct (recomendado)"
            )
            SettingsCard(
                icon = Icons.Default.Bluetooth,
                title = "Bluetooth fallback",
                description = "Ativado quando Wi-Fi não disponível"
            )
            SettingsCard(
                icon = Icons.Default.Nfc,
                title = "NFC",
                description = "Usado para troca inicial de token"
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = { navController.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Voltar")
        }
    }
}

@Composable
private fun SettingsCard(icon: ImageVector, title: String, description: String) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

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
