package org.openbeam

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.core.content.ContextCompat
import org.openbeam.ui.OpenBeamApp

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
        val permissions = mutableListOf(
            Manifest.permission.NFC,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.INTERNET
        )
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