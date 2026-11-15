package com.example.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedPayload(
    val cipherTextBase64: String,
    val ivBase64: String
)

/**
 * Provides AES-256-GCM encryption backed by AndroidKeyStore for TAGORE business audit logs
 * and sensitive offline memory entries, with an in-memory fallback for JVM/Robolectric tests.
 */
class TagoreCryptoManager {

    private val keyAlias = "tagore_aes_master_key"

    private val fallbackTestKey: SecretKey by lazy {
        val raw = "TAGORE_LOCAL_VAULT_KEY_256_BITS!".toByteArray(StandardCharsets.UTF_8)
        SecretKeySpec(raw.copyOf(32), "AES")
    }

    private fun getOrCreateKey(): SecretKey {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val existing = keyStore.getKey(keyAlias, null) as? SecretKey
            if (existing != null) {
                existing
            } else {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val spec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            fallbackTestKey
        }
    }

    fun encrypt(plainText: String): EncryptedPayload {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return EncryptedPayload(
            cipherTextBase64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(cipherTextBase64: String, ivBase64: String): String {
        return try {
            val cipherBytes = Base64.decode(cipherTextBase64, Base64.NO_WRAP)
            val ivBytes = Base64.decode(ivBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, ivBytes)
            )
            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            "[Encrypted Payload Protected]"
        }
    }
}
