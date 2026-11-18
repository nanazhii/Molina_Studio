package com.molina.core.security

import com.molina.log.MolinaEvents
import com.molina.log.MolinaLog
import com.molina.log.MolinaLogger
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal fun dialDirect(ip: InetAddress, port: Int): Socket {
    val s = Socket()
    try {
        s.connect(InetSocketAddress(ip, port), 8_000)
        s.tcpNoDelay = true
        return s
    } catch (e: IOException) {
        try { s.close() } catch (_: IOException) { }
        throw e
    }
}

/**
 * Molina Shield: proxy HTTP lokal di 127.0.0.1 (port acak, wajib token). Semua lalu lintas
 * http/https klien (mis. terminal/PRoot) lewat sini, termasuk redirect, segmen HLS/DASH, dan isi playlist, jadi
 * tiap lompatan diperiksa ulang. Alur per permintaan:
 * 1. token (klien membalas tantangan 407 dengan Proxy-Authorization),
 * 2. [UrlPolicy] pada URL, port sensitif ([ShieldPolicy]), daftar pelacak ([TrackerBlocklist]),
 * 3. DNS di-resolve DI SINI (dengan batas waktu), tiap IP diperiksa [ShieldPolicy.checkAddress],
 *    lalu menyambung ke IP yang sama (anti DNS rebinding),
 * 4. http: diteruskan tanpa Cookie/Proxy-*, hanya GET/HEAD tanpa badan; https: CONNECT, TLS tetap
 *    ujung ke ujung (Shield tidak melihat isinya).
 * Tidak mencakup rtmp/rtsp dan proses lain (mis. yt-dlp di Debian). Murni JVM, tanpa Android.
 * [resolver] dan [dialer] dapat diganti untuk uji; bawaannya DNS dan soket sistem.
 */
class LocalShieldProxy(
    initial: ShieldConfig = ShieldConfig(),
    initialBlocklist: TrackerBlocklist = TrackerBlocklist(),
    private val logger: MolinaLogger = MolinaLog.logger,
    private val resolver: (String) -> List<InetAddress> = { InetAddress.getAllByName(it).toList() },
    private val dialer: (InetAddress, Int) -> Socket = ::dialDirect,
) : MolinaShield {

    @Volatile private var config: ShieldConfig = initial
    @Volatile private var blocklist: TrackerBlocklist = initialBlocklist
    @Volatile private var allowed: Set<String> = normAllow(initial)
    @Volatile private var server: ServerSocket? = null
    @Volatile private var token: String = ""

    private val slots = Semaphore(MAX_CONNECTIONS)
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "molina-shield").apply { isDaemon = true } }
    private val live: MutableSet<Socket> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val events = ConcurrentLinkedDeque<ShieldEvent>()
    private val passed = AtomicLong(0)
    private val lastLogged = HashMap<String, Long>()
    private val state = MutableStateFlow(ShieldSnapshot(enabled = initial.enabled))

    override val snapshot: StateFlow<ShieldSnapshot> = state.asStateFlow()

    private fun running(): Boolean = server?.isClosed == false

    override fun failClosed(): Boolean = config.enabled && !running()

    override fun recentBlocks(): List<ShieldEvent> = events.toList()

    override fun config(): ShieldConfig = config

    /** Ganti daftar pelacak saat berjalan (mis. setelah daftar tambahan pengguna dimuat). */
    fun replaceBlocklist(b: TrackerBlocklist) { blocklist = b }

    override fun updateConfig(config: ShieldConfig) {
        this.config = config
        allowed = normAllow(config)
        state.update { it.copy(enabled = config.enabled) }
    }

    override fun endpoint(): ProxyEndpoint? {
        if (!config.enabled) return null
        val s = server ?: return null
        if (s.isClosed) return null
        return ProxyEndpoint("127.0.0.1", s.localPort, USER, token)
    }

    @Synchronized
    override fun start(): Boolean {
        if (running()) return true
        return try {
            val raw = ByteArray(24)
            SecureRandom().nextBytes(raw)
            token = raw.joinToString("") { "%02x".format(it) }
            val s = ServerSocket(0, 50, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
            server = s
            Thread({ acceptLoop(s) }, "molina-shield-accept").apply { isDaemon = true; start() }
            state.update { it.copy(running = true) }
            MolinaEvents.behavior("net", "shield.start", "Molina Shield aktif", mapOf("port" to s.localPort.toString()), logger = logger)
            true
        } catch (e: IOException) {
            MolinaEvents.bug("net", "shield.start-failed", "Shield gagal start", e, logger = logger)
            false
        }
    }

    @Synchronized
    override fun stop() {
        try { server?.close() } catch (_: IOException) { }
        server = null
        for (s in live.toList()) closeQuietly(s)
        state.update { it.copy(running = false) }
        MolinaEvents.behavior("net", "shield.stop", "Molina Shield berhenti", logger = logger)
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val c = try { s.accept() } catch (e: IOException) { break }
            if (!slots.tryAcquire()) { closeQuietly(c); continue }
            try {
                pool.execute {
                    live.add(c)
                    try { handle(c) } catch (_: Exception) { /* satu koneksi gagal tidak boleh menjatuhkan proxy */ }
                    finally { live.remove(c); slots.release(); closeQuietly(c) }
                }
            } catch (_: Exception) { slots.release(); closeQuietly(c) }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = HEAD_TIMEOUT_MS
        client.tcpNoDelay = true
        val out = client.getOutputStream()
        val head = readHead(client.getInputStream()) ?: return reply(out, 400, "Bad Request")
        val lines = head.split("\r\n")
        val first = lines[0].split(' ')
        if (first.size != 3) return reply(out, 400, "Bad Request")
        val method = first[0].uppercase()
        val target = first[1]
        val headers = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val c = lines[i].indexOf(':')
            if (c > 0) headers[lines[i].substring(0, c).trim().lowercase()] = lines[i].substring(c + 1).trim()
        }
        if (!authorized(headers["proxy-authorization"])) return deny407(out)
        if ((headers["content-length"]?.toLongOrNull() ?: 0L) > 0L || "transfer-encoding" in headers) return reply(out, 400, "Bad Request")
        when (method) {
            "CONNECT" -> handleConnect(client, out, target)
            "GET", "HEAD" -> handleForward(client, out, method, target, lines)
            else -> reply(out, 405, "Method Not Allowed")
        }
    }

    private fun handleConnect(client: Socket, out: OutputStream, target: String) {
        val hp = splitHostPort(target, 443) ?: return reply(out, 400, "Bad Request")
        val origin = openOrigin(out, hp.first, hp.second, "https" + "://" + target) ?: return
        live.add(origin)
        try {
            out.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
            out.flush()
            state.update { it.copy(passed = passed.incrementAndGet()) }
            tunnel(client, origin, clientToOrigin = true)
        } finally { live.remove(origin); closeQuietly(origin) }
    }

    private fun handleForward(client: Socket, out: OutputStream, method: String, target: String, lines: List<String>) {
        if (!target.startsWith("http" + "://", ignoreCase = true)) return reply(out, 400, "Bad Request")
        val afterScheme = target.substring(7)
        val authEnd = afterScheme.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (authEnd < 0) afterScheme else afterScheme.substring(0, authEnd)
        var path = if (authEnd < 0) "/" else afterScheme.substring(authEnd)
        if (!path.startsWith("/")) path = "/$path"
        path = path.substringBefore('#')
        val hp = splitHostPort(authority, 80) ?: return reply(out, 400, "Bad Request")
        val origin = openOrigin(out, hp.first, hp.second, target) ?: return
        live.add(origin)
        try {
            val sb = StringBuilder().append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
            var hasHost = false
            for (i in 1 until lines.size) {
                val l = lines[i]
                val c = l.indexOf(':')
                if (c <= 0) continue
                val name = l.substring(0, c).trim().lowercase()
                if (name in DROP_HEADERS || (config.stripReferer && name == "referer")) continue
                if (name == "host") hasHost = true
                sb.append(l).append("\r\n")
            }
            if (!hasHost) sb.append("Host: ").append(authority).append("\r\n")
            sb.append("Connection: close\r\n\r\n")
            val o = origin.getOutputStream()
            o.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            o.flush()
            state.update { it.copy(passed = passed.incrementAndGet()) }
            tunnel(client, origin, clientToOrigin = false)
        } finally { live.remove(origin); closeQuietly(origin) }
    }

    /** Periksa tujuan lalu sambung ke salah satu IP yang lolos; null bila ditolak (balasan sudah dikirim). */
    private fun openOrigin(out: OutputStream, host: String, port: Int, policyUrl: String): Socket? {
        val cfg = config
        val v = UrlPolicy.check(policyUrl, cfg.allowLan)
        if (v is UrlPolicy.Verdict.Blocked) { block(out, host, "url." + v.reason.name.lowercase(), v.reason.message); return null }
        if (!ShieldPolicy.portAllowed(port)) { block(out, host, "port.sensitive", "port tidak diizinkan"); return null }
        if (cfg.blockTrackers && blocklist.isTracker(host, allowed)) { block(out, host, "tracker", "pelacak atau iklan"); return null }
        val all = resolve(host)
        if (all.isNullOrEmpty()) { reply(out, 502, "Bad Gateway"); return null }
        val ok = all.filter { ShieldPolicy.checkAddress(it, cfg.allowLan) is UrlPolicy.Verdict.Allowed }
            .sortedBy { if (it is Inet4Address) 0 else 1 }
        if (ok.isEmpty()) { block(out, host, "dns.rebinding", "DNS mengarah ke alamat terlarang"); return null }
        for (ip in ok) {
            try {
                val s = dialer(ip, port)
                s.soTimeout = IDLE_TIMEOUT_MS
                return s
            } catch (_: IOException) { /* coba IP berikutnya */ }
        }
        reply(out, 502, "Bad Gateway")
        return null
    }

    /** DNS dengan batas waktu: getAllByName sistem bisa menggantung tanpa batas. */
    private fun resolve(host: String): List<InetAddress>? {
        val f = pool.submit<List<InetAddress>> { resolver(host) }
        return try {
            f.get(DNS_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) { f.cancel(true); null
        } catch (_: ExecutionException) { null
        } catch (_: InterruptedException) { Thread.currentThread().interrupt(); null }
    }

    private fun tunnel(client: Socket, origin: Socket, clientToOrigin: Boolean) {
        client.soTimeout = IDLE_TIMEOUT_MS
        if (clientToOrigin) {
            Thread({
                pump(client.getInputStream(), origin.getOutputStream(), ignoreTimeout = true)
                try { origin.shutdownOutput() } catch (_: IOException) { }
            }, "molina-shield-up").apply { isDaemon = true; start() }
        }
        pump(origin.getInputStream(), client.getOutputStream(), ignoreTimeout = false)
        closeQuietly(origin)
        closeQuietly(client)
    }

    private fun pump(src: InputStream, dst: OutputStream, ignoreTimeout: Boolean) {
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = try { src.read(buf) }
            catch (_: SocketTimeoutException) { if (ignoreTimeout) continue else return }
            catch (_: IOException) { return }
            if (n < 0) return
            try { dst.write(buf, 0, n); dst.flush() } catch (_: IOException) { return }
        }
    }

    private fun authorized(given: String?): Boolean {
        if (given == null) return false
        val expected = "Basic " + Base64.getEncoder().encodeToString((USER + ":" + token).toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(given.toByteArray(Charsets.UTF_8), expected.toByteArray(Charsets.UTF_8))
    }

    /** Baca kepala permintaan byte demi byte dengan batas waktu total; null bila tidak sah. */
    private fun readHead(input: InputStream): String? {
        val buf = ByteArray(MAX_HEAD_BYTES)
        var n = 0
        val deadline = System.nanoTime() + HEAD_TIMEOUT_MS * 1_000_000L
        try {
            while (n < buf.size) {
                if (System.nanoTime() > deadline) return null
                val b = input.read()
                if (b < 0) return null
                buf[n++] = b.toByte()
                if (n >= 4 && buf[n - 4] == CR && buf[n - 3] == LF && buf[n - 2] == CR && buf[n - 1] == LF) {
                    return String(buf, 0, n - 4, Charsets.ISO_8859_1)
                }
            }
        } catch (_: IOException) { return null }
        return null
    }

    private fun block(out: OutputStream, host: String, code: String, message: String) {
        val h = host.filter { it > ' ' && it != '\u007f' }.take(100)
        events.addLast(ShieldEvent(System.currentTimeMillis(), h, code, message))
        while (events.size > MAX_EVENTS) events.pollFirst()
        state.update { it.copy(blocked = it.blocked + 1) }
        if (shouldLog(h)) logger.w(MolinaLogger.tag("net", "shield"), "blokir $h: $message")
        reply(out, 403, "Forbidden")
    }

    /** Satu baris log per host per menit; iklan bisa memanggil host yang sama ratusan kali. */
    private fun shouldLog(host: String): Boolean = synchronized(lastLogged) {
        val now = System.currentTimeMillis()
        if (lastLogged.size > 256) lastLogged.clear()
        val prev = lastLogged[host]
        if (prev != null && now - prev < 60_000) false else { lastLogged[host] = now; true }
    }

    private fun deny407(out: OutputStream) = write(
        out, "HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"molina\"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n",
    )

    private fun reply(out: OutputStream, code: Int, msg: String) =
        write(out, "HTTP/1.1 $code $msg\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")

    private fun write(out: OutputStream, text: String) {
        try { out.write(text.toByteArray(Charsets.ISO_8859_1)); out.flush() } catch (_: IOException) { /* klien sudah pergi */ }
    }

    private fun closeQuietly(s: Socket) { try { s.close() } catch (_: IOException) { } }

    private companion object {
        const val USER = "molina"
        const val MAX_HEAD_BYTES = 16 * 1024
        const val MAX_CONNECTIONS = 64
        const val MAX_EVENTS = 50
        const val HEAD_TIMEOUT_MS = 15_000
        const val IDLE_TIMEOUT_MS = 30_000
        const val DNS_TIMEOUT_MS = 5_000L
        const val CR: Byte = 13
        const val LF: Byte = 10

        /** Header yang tidak diteruskan ke server asal (hop-by-hop, kredensial proxy, cookie). */
        val DROP_HEADERS = setOf(
            "proxy-authorization", "proxy-connection", "connection", "keep-alive",
            "te", "upgrade", "cookie", "trailer", "transfer-encoding",
        )

        fun normAllow(c: ShieldConfig): Set<String> = c.trackerAllowlist.mapTo(HashSet()) { TrackerBlocklist.normalize(it) }

        fun splitHostPort(s: String, defaultPort: Int): Pair<String, Int>? = parseHostPort(s, defaultPort)
    }
}
