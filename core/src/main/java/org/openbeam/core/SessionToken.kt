package org.openbeam.core

import java.util.UUID

data class SessionToken(
    val id: String,
    val type: TransferType,
    val tempKey: String,
    val params: Map<String, String>
) {
    companion object {
        fun generate(type: TransferType, params: Map<String, String>): SessionToken {
            val id = UUID.randomUUID().toString()
            val key = CryptoUtil.generateRandomKey()
            val mutable = params.toMutableMap()
            if (!mutable.containsKey("transport")) {
                mutable["transport"] = "wifi"
            }
            return SessionToken(id, type, key, mutable)
        }
    }
}