package xin.ctkqiang.deauthctrl.manager

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * TLS 证书检查 —— 直连目标端口,读取服务端证书链并逐张体检
 *
 * 输出每张证书的颁发者/持有者/SAN/有效期/序列号/指纹/签名算法,
 * 并给出机器判读:未生效、已过期、剩余天数、主机名是否匹配。
 * 全部数据来自服务端真实返回的证书,读不到就是没有。
 */
class TlsInspector {

    enum class Status { VALID, NOT_YET_VALID, EXPIRED }

    data class CertInfo(
        val subject: String,
        val issuer: String,
        val san: List<String>,
        val notBefore: String,
        val notAfter: String,
        val serial: String,
        val sigAlg: String,
        val keyAlg: String,
        val fingerprint: String,
        val status: Status,
        val daysLeft: Long,
        val hostnameMatch: Boolean,
    )

    data class ChainResult(
        val host: String,
        val port: Int,
        val protocol: String,
        val cipher: String,
        val chain: List<CertInfo>,
        val elapsedMs: Long,
    )

    /** 同步检查;调用方需在 IO 线程执行 */
    fun inspect(host: String, port: Int, timeoutMs: Int = 8000): ChainResult {
        val started = System.currentTimeMillis()
        val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
        val socket = factory.createSocket() as SSLSocket
        try {
            socket.soTimeout = timeoutMs
            // SNI:多证书站点必须携带服务器名,否则拿到默认证书
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                val params: SSLParameters = socket.sslParameters
                params.serverNames = listOf(javax.net.ssl.SNIHostName(host))
                socket.sslParameters = params
            }
            socket.connect(java.net.InetSocketAddress(host, port), timeoutMs)
            socket.startHandshake()
            val session = socket.session
            val now = System.currentTimeMillis()
            val verifier = HttpsURLConnection.getDefaultHostnameVerifier()
            val chain = session.peerCertificates.mapNotNull { it as? X509Certificate }.map { cert ->
                val status = try {
                    cert.checkValidity(Date(now)); Status.VALID
                } catch (_: java.security.cert.CertificateNotYetValidException) {
                    Status.NOT_YET_VALID
                } catch (_: java.security.cert.CertificateExpiredException) {
                    Status.EXPIRED
                }
                val daysLeft = (cert.notAfter.time - now) / 86_400_000L
                val match = runCatching { verifier.verify(host, session) }.getOrDefault(false)
                CertInfo(
                    subject = cert.subjectX500Principal.name,
                    issuer = cert.issuerX500Principal.name,
                    san = cert.subjectAlternativeNames?.mapNotNull { (_, v) -> v?.toString() } ?: emptyList(),
                    notBefore = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(cert.notBefore),
                    notAfter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(cert.notAfter),
                    serial = cert.serialNumber.toString(16).uppercase(Locale.US),
                    sigAlg = cert.sigAlgName,
                    keyAlg = cert.publicKey.algorithm,
                    fingerprint = sha256Fingerprint(cert),
                    status = status,
                    daysLeft = daysLeft,
                    hostnameMatch = match,
                )
            }
            return ChainResult(host, port, session.protocol, session.cipherSuite, chain, System.currentTimeMillis() - started)
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun sha256Fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
