package com.tuntech.supertvstreamcast.domain

/** Minimal protobuf wire writer: varint and length-delimited fields are all the TV protocols need. */
class ProtoWriter {
    private var bytes = ByteArray(32)
    private var size = 0
    private fun put(value: Int) {
        if (size == bytes.size) bytes = bytes.copyOf(size * 2)
        bytes[size++] = value.toByte()
    }
    private fun raw(value: Long) {
        var rest = value
        while (rest and 0x7FL.inv() != 0L) { put((rest and 0x7F).toInt() or 0x80); rest = rest ushr 7 }
        put(rest.toInt())
    }
    fun varint(field: Int, value: Long) { raw((field.toLong() shl 3)); raw(value) }
    fun bytes(field: Int, value: ByteArray) { raw((field.toLong() shl 3) or 2); raw(value.size.toLong()); value.forEach { put(it.toInt()) } }
    fun string(field: Int, value: String) = bytes(field, value.encodeToByteArray())
    fun message(field: Int, body: ProtoWriter.() -> Unit) = bytes(field, proto(body))
    fun toByteArray(): ByteArray = bytes.copyOf(size)
}
fun proto(body: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(body).toByteArray()
/** A varint on its own, as used for message length prefixes. */
fun protoVarint(value: Int): ByteArray {
    require(value >= 0)
    val out = ArrayList<Byte>()
    var rest = value
    while (rest and 0x7F.inv() != 0) { out += ((rest and 0x7F) or 0x80).toByte(); rest = rest ushr 7 }
    out += rest.toByte()
    return out.toByteArray()
}

/** Top-level fields of one message; a repeated field keeps its last value. */
class ProtoFields(val varints: Map<Int, Long>, val bytes: Map<Int, ByteArray>) {
    fun message(field: Int): ProtoFields? = bytes[field]?.let(::parseProto)
    fun string(field: Int): String = bytes[field]?.decodeToString().orEmpty()
}
/** @throws IllegalArgumentException on truncated or unsupported input. */
fun parseProto(bytes: ByteArray): ProtoFields {
    val varints = mutableMapOf<Int, Long>()
    val blobs = mutableMapOf<Int, ByteArray>()
    var position = 0
    fun varint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            require(position < bytes.size && shift < 64)
            val byte = bytes[position++].toInt()
            result = result or ((byte and 0x7F).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
    }
    while (position < bytes.size) {
        val tag = varint()
        val field = (tag ushr 3).toInt()
        when ((tag and 7).toInt()) {
            0 -> varints[field] = varint()
            1 -> position += 8
            2 -> {
                val length = varint()
                require(length >= 0 && length <= bytes.size - position)
                blobs[field] = bytes.copyOfRange(position, position + length.toInt())
                position += length.toInt()
            }
            5 -> position += 4
            else -> throw IllegalArgumentException()
        }
        require(position <= bytes.size)
    }
    return ProtoFields(varints, blobs)
}

fun sha256(input: ByteArray): ByteArray {
    val h = SHA256_INIT.copyOf()
    val bitLength = input.size.toLong() * 8
    val padded = (input + 0x80.toByte()).let { it.copyOf((it.size + 8 + 63) / 64 * 64) }
    for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
    val w = IntArray(64)
    for (block in padded.indices step 64) {
        for (i in 0 until 16) w[i] = (0 until 4).fold(0) { acc, j -> (acc shl 8) or (padded[block + i * 4 + j].toInt() and 0xFF) }
        for (i in 16 until 64) {
            val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
            val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
            w[i] = w[i - 16] + s0 + w[i - 7] + s1
        }
        val v = h.copyOf()
        for (i in 0 until 64) {
            val s1 = v[4].rotateRight(6) xor v[4].rotateRight(11) xor v[4].rotateRight(25)
            val t1 = v[7] + s1 + ((v[4] and v[5]) xor (v[4].inv() and v[6])) + SHA256_K[i] + w[i]
            val s0 = v[0].rotateRight(2) xor v[0].rotateRight(13) xor v[0].rotateRight(22)
            val t2 = s0 + ((v[0] and v[1]) xor (v[0] and v[2]) xor (v[1] and v[2]))
            for (j in 7 downTo 1) v[j] = v[j - 1]
            v[4] += t1
            v[0] = t1 + t2
        }
        for (i in 0 until 8) h[i] += v[i]
    }
    return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
}
private val SHA256_INIT = intArrayOf(
    0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt(), 0x510e527f, 0x9b05688c.toInt(), 0x1f83d9ab, 0x5be0cd19,
)
private val SHA256_K = longArrayOf(
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
).map { it.toInt() }.toIntArray()

fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
/** Null unless [value] is an even number of hex digits. */
fun hexBytes(value: String): ByteArray? {
    if (value.length % 2 != 0 || value.any { it.digitToIntOrNull(16) == null }) return null
    return ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

/** Unsigned big-endian magnitudes, without leading zero bytes. */
class RsaPublicKey(val modulus: ByteArray, val exponent: ByteArray)

/** The few DER structures needed to read a TV's certificate and to issue this phone's own. */
object Der {
    private const val SEQUENCE = 0x30
    private const val INTEGER = 0x02
    private const val BIT_STRING = 0x03
    private val SHA256_WITH_RSA = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x0B, 0x05, 0x00)
    private val RSA_ENCRYPTION = byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00)
    private val COMMON_NAME = byteArrayOf(0x06, 0x03, 0x55, 0x04, 0x03)

    private class Item(val tag: Int, val start: Int, val end: Int)
    private fun items(bytes: ByteArray, from: Int, to: Int): List<Item> {
        val result = ArrayList<Item>()
        var position = from
        while (position < to) {
            require(position + 2 <= to)
            val tag = bytes[position].toInt() and 0xFF
            var length = bytes[position + 1].toInt() and 0xFF
            position += 2
            if (length and 0x80 != 0) {
                val count = length and 0x7F
                require(count in 1..3 && position + count <= to)
                length = 0
                repeat(count) { length = (length shl 8) or (bytes[position++].toInt() and 0xFF) }
            }
            require(length <= to - position)
            result += Item(tag, position, position + length)
            position += length
        }
        return result
    }
    private fun ByteArray.magnitude(item: Item): ByteArray {
        var start = item.start
        while (start < item.end - 1 && this[start] == 0.toByte()) start++
        return copyOfRange(start, item.end)
    }
    /** PKCS#1 `RSAPublicKey`. */
    fun rsaPublicKey(pkcs1: ByteArray, from: Int = 0, to: Int = pkcs1.size): RsaPublicKey {
        val sequence = items(pkcs1, from, to).single().also { require(it.tag == SEQUENCE) }
        val (modulus, exponent) = items(pkcs1, sequence.start, sequence.end).also { require(it.size == 2 && it.all { i -> i.tag == INTEGER }) }
        return RsaPublicKey(pkcs1.magnitude(modulus), pkcs1.magnitude(exponent))
    }
    /** RSA key of an X.509 certificate; fails for other key types or malformed input. */
    fun certificateKey(certificate: ByteArray): RsaPublicKey {
        val outer = items(certificate, 0, certificate.size).first().also { require(it.tag == SEQUENCE) }
        val tbs = items(certificate, outer.start, outer.end).first().also { require(it.tag == SEQUENCE) }
        // version [0] is optional; subjectPublicKeyInfo is the sixth field after it.
        val fields = items(certificate, tbs.start, tbs.end).dropWhile { it.tag == 0xA0 }
        val info = fields[5].also { require(it.tag == SEQUENCE) }
        val (algorithm, key) = items(certificate, info.start, info.end)
        require(certificate.copyOfRange(algorithm.start, algorithm.end).contentEquals(RSA_ENCRYPTION))
        require(key.tag == BIT_STRING && certificate[key.start] == 0.toByte())
        return rsaPublicKey(certificate, key.start + 1, key.end)
    }

    private fun tlv(tag: Int, vararg parts: ByteArray): ByteArray {
        val length = parts.sumOf { it.size }
        val header = when {
            length < 0x80 -> byteArrayOf(tag.toByte(), length.toByte())
            length < 0x100 -> byteArrayOf(tag.toByte(), 0x81.toByte(), length.toByte())
            else -> byteArrayOf(tag.toByte(), 0x82.toByte(), (length shr 8).toByte(), length.toByte())
        }
        return parts.fold(header) { acc, part -> acc + part }
    }
    private fun integer(magnitude: ByteArray): ByteArray {
        val trimmed = magnitude.dropWhile { it == 0.toByte() }.toByteArray()
        return tlv(INTEGER, if (trimmed.isEmpty() || trimmed[0].toInt() < 0) byteArrayOf(0) + trimmed else trimmed)
    }
    fun rsaPublicKeyDer(key: RsaPublicKey): ByteArray = tlv(SEQUENCE, integer(key.modulus), integer(key.exponent))
    /**
     * Self-signed X.509 v3 certificate (SHA-256 with RSA, valid 2024–2099) for [pkcs1PublicKey].
     * [sign] returns the PKCS#1 v1.5 SHA-256 signature of the bytes it is given.
     */
    fun selfSignedCertificate(pkcs1PublicKey: ByteArray, commonName: String, serial: ByteArray, sign: (ByteArray) -> ByteArray): ByteArray {
        val algorithm = tlv(SEQUENCE, SHA256_WITH_RSA)
        val name = tlv(SEQUENCE, tlv(0x31, tlv(SEQUENCE, COMMON_NAME, tlv(0x0C, commonName.encodeToByteArray()))))
        val validity = tlv(SEQUENCE, tlv(0x17, "240101000000Z".encodeToByteArray()), tlv(0x18, "20991231235959Z".encodeToByteArray()))
        val keyInfo = tlv(SEQUENCE, tlv(SEQUENCE, RSA_ENCRYPTION), tlv(BIT_STRING, byteArrayOf(0), pkcs1PublicKey))
        val tbs = tlv(SEQUENCE, tlv(0xA0, byteArrayOf(INTEGER.toByte(), 1, 2)), integer(serial), algorithm, name, validity, name, keyInfo)
        return tlv(SEQUENCE, tbs, algorithm, tlv(BIT_STRING, byteArrayOf(0), sign(tbs)))
    }
}
