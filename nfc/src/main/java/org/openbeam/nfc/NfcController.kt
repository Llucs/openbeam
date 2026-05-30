package org.openbeam.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.os.Build
import android.os.Bundle
import org.openbeam.core.SessionToken

class NfcController(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var readCallback: ReaderCallback? = null

    fun enableWrite(token: SessionToken) {
        val ndef = TokenExchange.createNdef(token)
        adapter?.setNdefPushMessage(ndef, activity)
    }

    fun disableWrite() {
        adapter?.setNdefPushMessage(null, activity)
    }

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
            adapter.enableReaderMode(activity, readCallback,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V, Bundle())
        }
    }

    fun disableRead() {
        if (adapter == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            adapter.disableReaderMode(activity)
        }
        readCallback = null
    }
}