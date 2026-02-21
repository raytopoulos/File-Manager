package org.fossify.filemanager.smb

import android.content.Context
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.Share
import org.fossify.filemanager.helpers.NetworkFoldersRepository
import org.fossify.filemanager.models.NetworkFolder
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SmbClientManager(private val context: Context) {
    private data class SessionHolder(
        val smbClient: SMBClient,
        val connection: Connection,
        val session: Session
    ) : Closeable {
        override fun close() {
            runCatching { session.close() }
            runCatching { connection.close() }
            runCatching { smbClient.close() }
        }
    }

    private val repository = NetworkFoldersRepository(context)
    private val cache = ConcurrentHashMap<String, SessionHolder>()

    private fun buildClient(): SMBClient {
        val config = SmbConfig.builder()
            .withTimeout(10, TimeUnit.SECONDS)
            .build()
        return SMBClient(config)
    }

    private fun getOrCreateSession(folder: NetworkFolder): SessionHolder {
        return cache[folder.id] ?: synchronized(cache) {
            cache[folder.id] ?: run {
                val password = repository.getPassword(folder.id) ?: ""
                val smbClient = buildClient()
                val connection = smbClient.connect(folder.host, folder.port)
                val auth = AuthenticationContext(folder.username, password.toCharArray(), folder.domain)
                val session = connection.authenticate(auth)
                SessionHolder(smbClient, connection, session).also { cache[folder.id] = it }
            }
        }
    }

    fun invalidate(folderId: String) {
        cache.remove(folderId)?.close()
    }

    fun <T> withDiskShare(folder: NetworkFolder, action: (DiskShare) -> T): T {
        val holder = try {
            getOrCreateSession(folder)
        } catch (t: Throwable) {
            invalidate(folder.id)
            throw t
        }

        try {
            val share: Share = holder.session.connectShare(folder.share)
            if (share !is DiskShare) {
                runCatching { share.close() }
                throw SmbException("Selected share is not a disk share.")
            }
            try {
                return action(share)
            } finally {
                runCatching { share.close() }
            }
        } catch (t: Throwable) {
            if (t is TransportException) {
                invalidate(folder.id)
            }
            throw t
        }
    }
}
