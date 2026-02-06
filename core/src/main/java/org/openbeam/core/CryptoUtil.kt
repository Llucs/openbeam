package org.openbeam.core

import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.AeadKeyTemplates
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import java.nio.charset.Charset

/**
 * Utility methods for cryptographic operations. Uses Google Tink to simplify handling of
 * modern encryption primitives. A persistent keyset can be installed on the device to keep
 * history entries secure; however, session tokens use transient keys shared via NFC.
 */
object CryptoUtil {
    init {
        // Ensure Tink is set up at runtime.
        AeadConfig.register()
    }

    /**
     * Generates a random 256‑bit AES key encoded in base64. This key is used for encrypting
     * handshake metadata. The key should be shared via NFC and discarded after use.
     */
    fun generateRandomKey(): String {
        val random = java.security.SecureRandom()
        val key = ByteArray(32)
        random.nextBytes(key)
        return Base64.encodeToString(key, Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /**
     * Creates an Aead instance from a base64‑encoded key. Aead is used for authenticated
     * encryption/decryption (AEAD) operations.
     *
     * @param key Base64‑encoded AES key
     */
    fun aeadFromKey(key: String): Aead {
        val rawKey = Base64.decode(key, Base64.URL_SAFE or Base64.NO_WRAP)
        return com.google.crypto.tink.subtle.AesGcmJce(rawKey)
    }

    /**
     * Encrypts plain text using the given base64 key. Additional associated data (aad) should
     * include contextual information (e.g., session ID) to strengthen security.
     */
    fun encrypt(key: String, plainText: String, aad: String): ByteArray {
        val aead = aeadFromKey(key)
        return aead.encrypt(plainText.toByteArray(Charset.forName("UTF-8")), aad.toByteArray())
    }

    /**
     * Decrypts ciphertext using the given base64 key and associated data. Returns the
     * decrypted string or throws a GeneralSecurityException if decryption fails.
     */
    fun decrypt(key: String, cipherText: ByteArray, aad: String): String {
        val aead = aeadFromKey(key)
        val bytes = aead.decrypt(cipherText, aad.toByteArray())
        return String(bytes, Charset.forName("UTF-8"))
    }
}