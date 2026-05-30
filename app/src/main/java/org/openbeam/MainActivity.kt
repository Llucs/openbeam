package org.openbeam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import org.openbeam.ui.OpenBeamApp
import org.openbeam.ui.theme.OpenBeamTheme

class MainActivity : ComponentActivity() {
    private lateinit var transportManager: org.openbeam.transport.TransportManager
    private lateinit var nfcController: org.openbeam.nfc.NfcController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        transportManager = org.openbeam.transport.TransportManager(this)
        nfcController = org.openbeam.nfc.NfcController(this)
        requestPermissions()
        transportManager.registerReceivers()
        val sharedUris = extractSharedUris(intent)
        setContent {
            OpenBeamTheme {
                OpenBeamApp(
                    transportManager = transportManager,
                    nfcController = nfcController,
                    sharedUris = sharedUris,
                    onStartService = { TransferService.start(this@MainActivity) },
                    onStopService = { TransferService.stop(this@MainActivity) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        transportManager.unregisterReceivers()
        nfcController.disableRead()
        nfcController.disableWrite()
    }

    private fun extractSharedUris(intent: Intent?): List<Uri> {
        if (intent?.action == Intent.ACTION_SEND && intent.type != null) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            uri?.let { return listOf(it) }
        }
        if (intent?.action == Intent.ACTION_SEND_MULTIPLE && intent.type != null) {
            val uris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            uris?.let { return it.toList() }
        }
        return emptyList()
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.NFC,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed)
        }
    }
}
