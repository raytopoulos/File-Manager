package org.fossify.filemanager.smb

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

data class SmbDiscoveredHost(
    val ip: String,
    val hostname: String?
)

class SmbNetworkScanner {
    data class ScanProgress(
        val scanned: Int,
        val total: Int,
        val found: Int
    )

    fun scan(
        timeoutMs: Int = 250,
        maxHostsPerSubnet: Int = 1024,
        concurrency: Int = 64,
        cancelled: AtomicBoolean = AtomicBoolean(false),
        onProgress: (ScanProgress) -> Unit,
        onHostFound: (SmbDiscoveredHost) -> Unit
    ) {
        val candidates = buildCandidates(maxHostsPerSubnet)
        val total = candidates.size
        if (total == 0) {
            onProgress(ScanProgress(scanned = 0, total = 0, found = 0))
            return
        }
        val scanned = AtomicInteger(0)
        val found = AtomicInteger(0)

        val pool = Executors.newFixedThreadPool(concurrency)
        try {
            val tasks = candidates.map { ip ->
                Callable {
                    if (cancelled.get()) return@Callable
                    val ok = isPortOpen(ip, 445, timeoutMs)
                    val done = scanned.incrementAndGet()
                    if (ok) {
                        val host = SmbDiscoveredHost(ip = ip, hostname = reverseLookup(ip))
                        found.incrementAndGet()
                        onHostFound(host)
                    }
                    onProgress(ScanProgress(scanned = done, total = total, found = found.get()))
                }
            }
            pool.invokeAll(tasks)
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    private fun buildCandidates(maxHostsPerSubnet: Int): List<String> {
        val result = LinkedHashSet<String>()
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filterNot { it.isLoopback || !it.isUp }

        interfaces.forEach { netIf ->
            netIf.interfaceAddresses.forEach { addr ->
                val inet = addr.address
                if (inet !is Inet4Address) return@forEach
                val prefix = addr.networkPrefixLength.toInt()
                if (prefix <= 0 || prefix > 32) return@forEach

                val hosts = 1 shl (32 - prefix)
                val cappedPrefix = if (hosts > maxHostsPerSubnet) 24 else prefix
                result.addAll(expandSubnet(inet, cappedPrefix))
            }
        }

        return result.toList()
    }

    private fun expandSubnet(ip: Inet4Address, prefix: Int): List<String> {
        val ipInt = ipToInt(ip)
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val network = ipInt and mask
        val broadcast = network or mask.inv()

        val out = ArrayList<String>()
        var cur = network + 1
        while (cur < broadcast) {
            out.add(intToIp(cur))
            cur++
        }
        return out
    }

    private fun isPortOpen(ip: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun reverseLookup(ip: String): String? {
        return runCatching {
            val addr = InetAddress.getByName(ip)
            addr.hostName?.takeIf { it != ip }
        }.getOrNull()
    }

    private fun ipToInt(ip: Inet4Address): Int {
        val b = ip.address
        return (b[0].toInt() and 0xFF shl 24) or
            (b[1].toInt() and 0xFF shl 16) or
            (b[2].toInt() and 0xFF shl 8) or
            (b[3].toInt() and 0xFF)
    }

    private fun intToIp(value: Int): String {
        return listOf(
            value ushr 24 and 0xFF,
            value ushr 16 and 0xFF,
            value ushr 8 and 0xFF,
            value and 0xFF
        ).joinToString(".")
    }
}
