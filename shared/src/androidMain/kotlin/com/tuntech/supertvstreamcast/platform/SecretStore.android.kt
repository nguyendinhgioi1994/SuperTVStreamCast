package com.tuntech.supertvstreamcast.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Values are AES-256-GCM encrypted with a key that never leaves the Android Keystore. Each [name]
 * is a separate store, so clearing one (forgetting paired TVs) leaves the others alone.
 */
fun createSecretStore(context: Context, name: String = "tv_space_secrets"): SecretStore {
    val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    return object : SecretStore {
        private fun key(): SecretKey {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
                init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
            }.generateKey()
        }
        @Synchronized override fun get(key: String): String? = runCatching {
            val stored = Base64.decode(prefs.getString(key, null) ?: return null, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, stored, 0, IV_BYTES)) }
            cipher.doFinal(stored, IV_BYTES, stored.size - IV_BYTES).decodeToString()
        }.getOrNull()
        @Synchronized override fun put(key: String, value: String) {
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
                val stored = cipher.iv + cipher.doFinal(value.encodeToByteArray())
                prefs.edit().putString(key, Base64.encodeToString(stored, Base64.NO_WRAP)).apply()
            }
        }
        @Synchronized override fun clear() { prefs.edit().clear().apply() }
    }
}
private const val KEYSTORE = "AndroidKeyStore"
private const val ALIAS = "tv_space_secrets"
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val IV_BYTES = 12
