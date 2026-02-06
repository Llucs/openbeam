package org.openbeam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.core.content.ContextCompat
import org.openbeam.ui.OpenBeamApp

/**
 * Entry point of the OpenBeam application. This activity simply hosts the Compose navigation
 * and handles runtime permission requests.
 */
class MainActivity : ComponentActivity() {
    private lateinit var transportManager: org.openbeam.transport.TransportManager
    private lateinit var nfcController: org.openbeam.nfc.NfcController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize managers
        transportManager = org.openbeam.transport.TransportManager(this)
        nfcController = org.openbeam.nfc.NfcController(this)
        // Request required permissions on start.
        requestPermissions()
        // Register broadcast receivers
        transportManager.registerReceivers()
        setContent {
            MaterialTheme {
                Surface {
                    OpenBeamApp(
                        transportManager = transportManager,
                        nfcController = nfcController
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transportManager.unregisterReceivers()
        nfcController.disableRead()
        nfcController.disableWrite()
    }

    private fun requestPermissions() {
        val permissions = listOf(
            Manifest.permission.NFC,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        ).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (permissions.isNotEmpty()) {
            val launcher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
            launcher.launch(permissions)
        }
    }
}