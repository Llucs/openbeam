package org.openbeam.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import org.json.JSONObject
import org.openbeam.core.SessionToken
import org.openbeam.core.TransferType

object TokenExchange {
    fun createNdef(token: SessionToken): NdefMessage {
        val json = JSONObject().apply {
            put("id", token.id)
            put("type", token.type.name)
            put("tempKey", token.tempKey)
            put("params", JSONObject(token.params))
        }
        val payload = json.toString().toByteArray(Charsets.UTF_8)
        val record = NdefRecord.createMime("application/vnd.openbeam.session", payload)
        return NdefMessage(arrayOf(record))
    }

    fun parseNdef(message: NdefMessage): SessionToken? {
        val record = message.records.firstOrNull() ?: return null
        return try {
            val json = JSONObject(String(record.payload, Charsets.UTF_8))
            val id = json.getString("id")
            val type = TransferType.valueOf(json.getString("type"))
            val tempKey = json.getString("tempKey")
            val paramsObj = json.getJSONObject("params")
            val params = mutableMapOf<String, String>()
            paramsObj.keys().forEachRemaining { key -> params[key] = paramsObj.getString(key) }
            SessionToken(id, type, tempKey, params)
        } catch (e: Exception) {
            null
        }
    }
}