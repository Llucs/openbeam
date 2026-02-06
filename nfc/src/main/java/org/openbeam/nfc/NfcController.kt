package org.openbeam.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.NfcAdapter.ReaderFlag
import android.os.Build
import android.os.Bundle
import org.openbeam.core.SessionToken

/**
 * High‑level controller for managing NFC operations. Handles enabling/disabling read mode
 * and writing a session token via Android Beam (NDEF push).
 */
class NfcController(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var readCallback: ReaderCallback? = null

    /** Enables NFC write mode by setting an NDEF push message on the adapter. */
    fun enableWrite(token: SessionToken) {
        val ndef = TokenExchange.createNdef(token)
        adapter?.setNdefPushMessage(ndef, activity)
    }

    /** Disables NFC write mode. */
    fun disableWrite() {
        adapter?.setNdefPushMessage(null, activity)
    }

    /** Enables NFC read mode. The [onTokenReceived] callback is invoked when a token is read. */
    fun enableRead(onTokenReceived: (SessionToken) -> Unit) {
        if (adapter == null) return
        readCallback = ReaderCallback { tag ->
            try {
                val ndef = android.nfc.Ndef.get(tag) ?: return@ReaderCallback
                ndef.connect()
                val message = ndef.ndefMessage ?: return@ReaderCallback
                ndef.close()
                val token = TokenExchange.parseNdef(message)
                token?.let { onTokenReceived(it) }
            } catch (_: Exception) {
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            adapter.enableReaderMode(activity, readCallback, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, Bundle())
        }
    }

    /** Disables NFC read mode. */
    fun disableRead() {
        if (adapter == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            adapter.disableReaderMode(activity)
        }
        readCallback = null
    }
}