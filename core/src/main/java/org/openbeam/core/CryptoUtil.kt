package org.openbeam.core

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.subtle.AesGcmJce
import java.security.SecureRandom

object CryptoUtil {
    init {
        AeadConfig.register()
    }

    fun generateRandomKey(): String {
        val random = SecureRandom()
        val key = ByteArray(32)
        random.nextBytes(key)
        return Base64.encodeToString(key, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    fun aeadFromKey(key: String): Aead {
        val rawKey = Base64.decode(key, Base64.URL_SAFE or Base64.NO_WRAP)
        return AesGcmJce(rawKey)
    }

    fun encrypt(key: String, plainText: String, aad: String): ByteArray {
        val aead = aeadFromKey(key)
        return aead.encrypt(plainText.toByteArray(Charsets.UTF_8), aad.toByteArray())
    }

    fun decrypt(key: String, cipherText: ByteArray, aad: String): String {
        val aead = aeadFromKey(key)
        val bytes = aead.decrypt(cipherText, aad.toByteArray())
        return String(bytes, Charsets.UTF_8)
    }
}