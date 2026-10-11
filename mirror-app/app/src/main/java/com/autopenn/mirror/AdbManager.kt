package com.autopenn.mirror

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import android.sun.security.x509.AlgorithmId
import android.sun.security.x509.CertificateAlgorithmId
import android.sun.security.x509.CertificateExtensions
import android.sun.security.x509.CertificateIssuerName
import android.sun.security.x509.CertificateSerialNumber
import android.sun.security.x509.CertificateSubjectName
import android.sun.security.x509.CertificateValidity
import android.sun.security.x509.CertificateVersion
import android.sun.security.x509.CertificateX509Key
import android.sun.security.x509.KeyIdentifier
import android.sun.security.x509.PrivateKeyUsageExtension
import android.sun.security.x509.SubjectKeyIdentifierExtension
import android.sun.security.x509.X500Name
import android.sun.security.x509.X509CertImpl
import android.sun.security.x509.X509CertInfo
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random

/** ADB client identity (RSA key + self-signed cert) kept in the app's private files dir; adbd remembers it after pairing. */
class AdbManager private constructor(dir: File) : AbsAdbConnectionManager() {
    private val privateKey: PrivateKey
    private val certificate: Certificate

    init {
        setApi(Build.VERSION.SDK_INT)
        val keyFile = File(dir, "adb_key")
        val certFile = File(dir, "adb_cert")
        if (keyFile.exists() && certFile.exists()) {
            privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
            certificate = CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(certFile.readBytes()))
        } else {
            val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom.getInstance("SHA1PRNG")) }.generateKeyPair()
            privateKey = pair.private
            val alg = "SHA512withRSA"
            val notBefore = Date()
            val notAfter = Date(System.currentTimeMillis() + 10L * 365 * 86_400_000)
            val ext = CertificateExtensions().apply {
                set("SubjectKeyIdentifier", SubjectKeyIdentifierExtension(KeyIdentifier(pair.public).identifier))
                set("PrivateKeyUsage", PrivateKeyUsageExtension(notBefore, notAfter))
            }
            val name = X500Name("CN=Auto Penn")
            val info = X509CertInfo().apply {
                set("version", CertificateVersion(2))
                set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
                set("algorithmID", CertificateAlgorithmId(AlgorithmId.get(alg)))
                set("subject", CertificateSubjectName(name))
                set("key", CertificateX509Key(pair.public))
                set("validity", CertificateValidity(notBefore, notAfter))
                set("issuer", CertificateIssuerName(name))
                set("extensions", ext)
            }
            certificate = X509CertImpl(info).also { it.sign(privateKey, alg) }
            keyFile.writeBytes(privateKey.encoded)
            certFile.writeBytes(certificate.encoded)
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey
    override fun getCertificate(): Certificate = certificate
    override fun getDeviceName(): String = "Auto Penn"

    companion object {
        @Volatile private var instance: AdbManager? = null
        fun get(ctx: Context): AdbManager = instance ?: synchronized(this) {
            instance ?: AdbManager(ctx.applicationContext.filesDir).also { instance = it }
        }
    }
}
