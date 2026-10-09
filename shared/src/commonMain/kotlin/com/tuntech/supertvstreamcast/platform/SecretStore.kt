package com.tuntech.supertvstreamcast.platform

/**
 * Pairing tokens and pinned TV certificates. Platform stores encrypt at rest (Android Keystore,
 * iOS Keychain) and never sync or back up. A value that cannot be read counts as not stored.
 */
interface SecretStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun clear()
}
/** Keeps secrets for the lifetime of the object only. */
class MemorySecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) { values[key] = value }
    override fun clear() = values.clear()
}
