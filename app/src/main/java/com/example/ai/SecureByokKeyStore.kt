package com.example.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "SecureByokKeyStore"
private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "pantrypulse_byok_gemini_aes_key_v1"
private const val PREFS_NAME = "pantrypulse_byok_encrypted_vault"
private const val PREF_CIPHERTEXT = "gemini_api_key_ciphertext"
private const val PREF_IV = "gemini_api_key_iv"
private const val PREF_MASKED_PREVIEW = "gemini_api_key_preview"
private const val GCM_TAG_BITS = 128

/**
 * Hardware-backed Android Keystore (AES-256-GCM) vault for optional user-supplied
 * BYOK ("Bring Your Own Gemini API Key").
 *
 * Keys are never stored in plaintext, never logged, and never bundled into the APK.
 */
class SecureByokKeyStore(appContext: Context) {

    private val prefs = appContext.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hasByokKey = MutableStateFlow(hasStoredCiphertext())
    val hasByokKey: StateFlow<Boolean> = _hasByokKey.asStateFlow()

    private val _maskedKeyPreview = MutableStateFlow(prefs.getString(PREF_MASKED_PREVIEW, "") ?: "")
    val maskedKeyPreview: StateFlow<String> = _maskedKeyPreview.asStateFlow()

    fun saveApiKey(rawKey: String): Boolean {
        val trimmed = rawKey.trim()
        if (trimmed.length < 16) return false

        return try {
            val secretKey = getOrCreateSecretKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(trimmed.toByteArray(Charsets.UTF_8))

            val masked = buildMaskedPreview(trimmed)
            prefs.edit()
                .putString(PREF_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(PREF_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                .putString(PREF_MASKED_PREVIEW, masked)
                .apply()

            _hasByokKey.value = true
            _maskedKeyPreview.value = masked
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt BYOK key in Android Keystore", e)
            false
        }
    }

    fun getDecryptedApiKey(): String? {
        val ctB64 = prefs.getString(PREF_CIPHERTEXT, null) ?: return null
        val ivB64 = prefs.getString(PREF_IV, null) ?: return null

        return try {
            val secretKey = getExistingSecretKey() ?: return null
            val iv = Base64.decode(ivB64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ctB64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            val plainBytes = cipher.doFinal(ciphertext)
            String(plainBytes, Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to decrypt stored BYOK key; clearing corrupted entry", e)
            clearApiKey()
            null
        }
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(PREF_CIPHERTEXT)
            .remove(PREF_IV)
            .remove(PREF_MASKED_PREVIEW)
            .apply()
        _hasByokKey.value = false
        _maskedKeyPreview.value = ""
    }

    private fun hasStoredCiphertext(): Boolean {
        return !prefs.getString(PREF_CIPHERTEXT, null).isNullOrBlank() &&
            !prefs.getString(PREF_IV, null).isNullOrBlank()
    }

    private fun getExistingSecretKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun getOrCreateSecretKey(): SecretKey {
        getExistingSecretKey()?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    companion object {
        fun buildMaskedPreview(rawKey: String): String {
            val clean = rawKey.trim()
            if (clean.length <= 8) return "••••••••"
            return "${clean.take(4)}••••••••${clean.takeLast(4)}"
        }
    }
}
