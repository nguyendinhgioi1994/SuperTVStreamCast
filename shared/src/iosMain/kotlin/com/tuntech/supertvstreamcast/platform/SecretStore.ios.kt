@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.tuntech.supertvstreamcast.platform

import kotlinx.cinterop.*
import platform.CoreFoundation.*
import platform.Security.*

/**
 * Values are generic-password Keychain items of this app, kept on this device only (no iCloud sync
 * or backup restore elsewhere). Each [service] is a separate store, so clearing one leaves the others alone.
 */
fun createSecretStore(service: String = SERVICE): SecretStore = object : SecretStore {
    private fun CfScope.item(key: String?): List<Pair<CFTypeRef?, CFTypeRef?>> = listOfNotNull(
        kSecClass to kSecClassGenericPassword, kSecAttrService to string(service), key?.let { kSecAttrAccount to string(it) },
    )
    override fun get(key: String): String? = cfScope {
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(dictionary(item(key) + listOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne)), result.ptr)
            val data: CFDataRef? = if (status == errSecSuccess) own(result.value)?.reinterpret() else null
            data?.bytes()?.decodeToString()
        }
    }
    override fun put(key: String, value: String) {
        cfScope {
            SecItemDelete(dictionary(item(key)))
            SecItemAdd(dictionary(item(key) + listOf(
                kSecValueData to data(value.encodeToByteArray()), kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
            )), null)
        }
    }
    override fun clear() { cfScope { SecItemDelete(dictionary(item(null))) } }
}
private const val SERVICE = "com.tuntech.supertvstreamcast.pairing"

/** Core Foundation objects created inside [cfScope] are released when it ends. */
internal class CfScope {
    private val owned = ArrayList<CFTypeRef?>()
    fun <T : CFTypeRef?> own(ref: T): T { owned += ref; return ref }
    fun release() = owned.forEach { if (it != null) CFRelease(it) }
}
internal inline fun <T> cfScope(block: CfScope.() -> T): T {
    val scope = CfScope()
    try { return scope.block() } finally { scope.release() }
}
internal fun CfScope.string(value: String): CFStringRef? = own(CFStringCreateWithCString(null, value, kCFStringEncodingUTF8))
internal fun CfScope.data(bytes: ByteArray): CFDataRef? =
    own(if (bytes.isEmpty()) CFDataCreate(null, null, 0) else bytes.usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), bytes.size.convert()) })
internal fun CfScope.number(value: Int): CFNumberRef? = memScoped {
    val holder = alloc<IntVar>()
    holder.value = value
    own(CFNumberCreate(null, kCFNumberIntType, holder.ptr))
}
internal fun CfScope.dictionary(entries: List<Pair<CFTypeRef?, CFTypeRef?>>): CFDictionaryRef? {
    val dictionary = CFDictionaryCreateMutable(null, entries.size.convert(), kCFTypeDictionaryKeyCallBacks.ptr, kCFTypeDictionaryValueCallBacks.ptr)
    entries.forEach { CFDictionarySetValue(dictionary, it.first, it.second) }
    return own(dictionary)
}
internal fun CFDataRef.bytes(): ByteArray {
    val length = CFDataGetLength(this).toInt()
    return if (length == 0) ByteArray(0) else CFDataGetBytePtr(this)!!.readBytes(length)
}
