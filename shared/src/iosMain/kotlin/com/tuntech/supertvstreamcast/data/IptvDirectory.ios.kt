@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.tuntech.supertvstreamcast.data

import coil3.PlatformContext
import kotlinx.cinterop.*
import platform.CoreCrypto.*
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/** Application Support, flagged so iCloud and device backups skip it. */
actual fun iptvDirectory(context: PlatformContext): String {
    val files = NSFileManager.defaultManager
    val base = files.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    val url = requireNotNull(base?.URLByAppendingPathComponent("iptv", isDirectory = true))
    files.createDirectoryAtURL(url, withIntermediateDirectories = true, attributes = null, error = null)
    url.setResourceValue(true, forKey = NSURLIsExcludedFromBackupKey, error = null)
    return requireNotNull(url.path)
}
actual fun iptvSecretStore(context: PlatformContext): com.tuntech.supertvstreamcast.platform.SecretStore =
    com.tuntech.supertvstreamcast.platform.createSecretStore("com.tuntech.supertvstreamcast.iptv")

actual fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray {
    if (data.isEmpty()) return data
    val out = ByteArray(data.size)
    memScoped {
        val cryptor = alloc<CCCryptorRefVar>()
        val created = CCCryptorCreateWithMode(kCCEncrypt, kCCModeCTR, kCCAlgorithmAES, ccNoPadding, iv.refTo(0), key.refTo(0), key.size.convert(),
            null, 0.convert(), 0, kCCModeOptionCTR_BE, cryptor.ptr)
        check(created == kCCSuccess)
        try {
            val moved = alloc<platform.posix.size_tVar>()
            check(CCCryptorUpdate(cryptor.value, data.refTo(0), data.size.convert(), out.refTo(0), out.size.convert(), moved.ptr) == kCCSuccess)
        } finally { CCCryptorRelease(cryptor.value) }
    }
    return out
}
actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { bytes ->
    if (size > 0) check(platform.Security.SecRandomCopyBytes(platform.Security.kSecRandomDefault, size.convert(), bytes.refTo(0)) == 0)
}
