package com.tuntech.supertvstreamcast

import com.tuntech.supertvstreamcast.domain.Der
import com.tuntech.supertvstreamcast.domain.RsaPublicKey
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import kotlin.test.Test
import kotlin.test.assertEquals

/** The hand-built certificate (used for the iOS identity) must be a certificate the JDK accepts and verifies. */
class DerCertificateHostTest {
    @Test fun selfSignedCertificateIsValidX509() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val public = pair.public as RSAPublicKey
        val pkcs1 = Der.rsaPublicKeyDer(RsaPublicKey(public.modulus.toByteArray(), public.publicExponent.toByteArray()))
        val der = Der.selfSignedCertificate(pkcs1, "atvremote", byteArrayOf(0x7A, 0x01, 0x02)) { tbs ->
            Signature.getInstance("SHA256withRSA").run { initSign(pair.private); update(tbs); sign() }
        }
        val certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        certificate.verify(pair.public)
        certificate.checkValidity()
        assertEquals("CN=atvremote", certificate.subjectX500Principal.name)
        assertEquals(certificate.subjectX500Principal, certificate.issuerX500Principal)
        assertEquals(BigInteger("7A0102", 16), certificate.serialNumber)
        assertEquals(3, certificate.version)
        assertEquals(public, certificate.publicKey)
        val parsed = Der.certificateKey(der)
        assertEquals(public.modulus, BigInteger(1, parsed.modulus))
        assertEquals(public.publicExponent, BigInteger(1, parsed.exponent))
        // Certificates issued by other software parse the same way.
        assertEquals(public.modulus, BigInteger(1, Der.certificateKey(certificate.encoded).modulus))
    }
}
