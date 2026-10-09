package com.tuntech.supertvstreamcast.data

import coil3.PlatformContext
import java.io.File

/** Internal storage; the manifest turns backups off for the whole app. */
actual fun iptvDirectory(context: PlatformContext): String = File(context.filesDir, "iptv").absolutePath
actual fun iptvSecretStore(context: PlatformContext): com.tuntech.supertvstreamcast.platform.SecretStore =
    com.tuntech.supertvstreamcast.platform.createSecretStore(context, "tv_space_iptv")

actual fun aesCtr(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray =
    javax.crypto.Cipher.getInstance("AES/CTR/NoPadding").run {
        init(javax.crypto.Cipher.ENCRYPT_MODE, javax.crypto.spec.SecretKeySpec(key, "AES"), javax.crypto.spec.IvParameterSpec(iv))
        doFinal(data)
    }
actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also { java.security.SecureRandom().nextBytes(it) }
