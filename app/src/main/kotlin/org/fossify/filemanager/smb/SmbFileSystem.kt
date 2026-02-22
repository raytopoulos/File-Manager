package org.fossify.filemanager.smb

import android.content.Context
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import org.fossify.commons.models.FileDirItem
import org.fossify.filemanager.extensions.buildSmbRootPath
import org.fossify.filemanager.helpers.AppLog
import org.fossify.filemanager.helpers.NetworkFoldersRepository
import org.fossify.filemanager.models.NetworkFolder
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.EnumSet
import java.util.concurrent.TimeUnit

class SmbFileSystem(private val context: Context) {
    private val repository = NetworkFoldersRepository(context)
    private val clientManager = SmbClientManager(context)

    private fun buildClient(): SMBClient {
        val config = SmbConfig.builder()
            .withTimeout(10, TimeUnit.SECONDS)
            .build()
        return SMBClient(config)
    }

    fun stat(folderId: String, relPath: String): FileDirItem {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        return clientManager.withDiskShare(folder) { share ->
            val smbPath = toSmbSharePath(folder.basePath, relPath)
            val isDirectory = if (smbPath.isEmpty()) true else runCatching { share.folderExists(smbPath) }.getOrDefault(false)
            val name = relPath.trim('/').substringAfterLast('/', folder.name)
            val fullPath = if (relPath.isBlank()) buildSmbRootPath(folder) else "${buildSmbRootPath(folder)}/${relPath.trimStart('/')}"
            FileDirItem(fullPath.trimEnd('/'), name, isDirectory, 0, 0, 0)
        }
    }

    fun isDirectory(folderId: String, relPath: String): Boolean {
        return stat(folderId, relPath).isDirectory
    }

    fun list(folderId: String, relPath: String): List<FileDirItem> {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        return clientManager.withDiskShare(folder) { share ->
            val smbPath = toSmbSharePath(folder.basePath, relPath)
            val infos = share.list(if (smbPath.isEmpty()) "" else smbPath)
            infos.asSequence()
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .map { info -> toFileDirItem(folder, relPath, info) }
                .toList()
        }
    }

    fun mkdir(folderId: String, relDirPath: String) {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        clientManager.withDiskShare(folder) { share ->
            val smbPath = toSmbSharePath(folder.basePath, relDirPath)
            if (smbPath.isEmpty()) {
                return@withDiskShare
            }
            if (smbPath.isNotEmpty() && share.folderExists(smbPath)) {
                return@withDiskShare
            }
            share.mkdir(smbPath)
        }
    }

    fun createEmptyFile(folderId: String, relFilePath: String) {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        clientManager.withDiskShare(folder) { share ->
            val smbPath = toSmbSharePath(folder.basePath, relFilePath)
            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                SMB2CreateDisposition.FILE_CREATE,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            runCatching { file.close() }
        }
    }

    fun rename(folderId: String, fromRelPath: String, toRelPath: String) {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        clientManager.withDiskShare(folder) { share ->
            val from = toSmbSharePath(folder.basePath, fromRelPath)
            val to = toSmbSharePath(folder.basePath, toRelPath)
            invokeRename(share, from, to)
        }
    }

    fun delete(folderId: String, relPath: String, recursive: Boolean) {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        clientManager.withDiskShare(folder) { share ->
            val smbPath = toSmbSharePath(folder.basePath, relPath)
            deleteInternal(share, smbPath, recursive)
        }
    }

    fun openInputStream(folderId: String, relFilePath: String): InputStream {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        val password = repository.getPassword(folder.id) ?: ""
        val streamOp = SmbStreamingOperation(folder, password)
        return streamOp.openInputStream(toSmbSharePath(folder.basePath, relFilePath))
    }

    fun openOutputStream(folderId: String, relFilePath: String, overwrite: Boolean): OutputStream {
        val folder = repository.getById(folderId) ?: throw SmbException("Network folder not found.")
        val password = repository.getPassword(folder.id) ?: ""
        val streamOp = SmbStreamingOperation(folder, password)
        return streamOp.openOutputStream(toSmbSharePath(folder.basePath, relFilePath), overwrite)
    }

    fun testConnection(host: String, port: Int, username: String, password: String, domain: String?, share: String) {
        AppLog.i("SmbFileSystem", "testConnection: $host:$port share=$share user=$username domain=${domain ?: ""}")

        // Fast-fail if the host/port is unreachable to avoid long hangs.
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 1500)
        }

        val smbClient = buildClient()
        val connection = smbClient.connect(host, port)
        try {
            val auth = AuthenticationContext(username, password.toCharArray(), domain)
            val session = connection.authenticate(auth)
            try {
                val connectedShare = session.connectShare(share)
                if (connectedShare !is DiskShare) {
                    runCatching { connectedShare.close() }
                    throw SmbException("Selected share is not a disk share.")
                }
                try {
                    connectedShare.list("")
                } finally {
                    runCatching { connectedShare.close() }
                }
            } finally {
                runCatching { session.close() }
            }
        } finally {
            runCatching { connection.close() }
            runCatching { smbClient.close() }
        }
    }

    private fun deleteInternal(share: DiskShare, smbPath: String, recursive: Boolean) {
        val isDir = runCatching { share.folderExists(smbPath) }.getOrDefault(false)
        if (!isDir) {
            invokeRm(share, smbPath)
            return
        }

        if (recursive) {
            val children = share.list(smbPath)
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .map { child ->
                    if (smbPath.isEmpty()) child.fileName else "$smbPath\\${child.fileName}"
                }
            children.forEach { childPath ->
                deleteInternal(share, childPath, recursive = true)
            }
        }

        invokeRmdir(share, smbPath)
    }

    private fun toFileDirItem(folder: NetworkFolder, relParentPath: String, info: FileIdBothDirectoryInformation): FileDirItem {
        val attrs = info.fileAttributes.toLong()
        val dirFlag = FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value.toLong()
        val isDirectory = (attrs and dirFlag) != 0L
        val name = info.fileName
        val childRelPath = joinRelPath(relParentPath, name)
        val fullPath = "${buildSmbRootPath(folder)}/${childRelPath}".trimEnd('/')
        val size = if (isDirectory) 0L else info.endOfFile
        val modified = runCatching { info.lastWriteTime.toEpochMillis() }.getOrDefault(0L)
        return FileDirItem(fullPath, name, isDirectory, 0, size, modified)
    }

    private fun invokeRename(share: DiskShare, from: String, to: String) {
        val methods = share.javaClass.methods.filter { it.name == "rename" }
        val twoArg = methods.firstOrNull { it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java)) }
        if (twoArg != null) {
            twoArg.invoke(share, from, to)
            return
        }

        val threeArg = methods.firstOrNull {
            it.parameterTypes.size == 3
                && it.parameterTypes[0] == String::class.java
                && it.parameterTypes[1] == String::class.java
                && (it.parameterTypes[2] == java.lang.Boolean.TYPE || it.parameterTypes[2] == java.lang.Boolean::class.java)
        }
        if (threeArg != null) {
            threeArg.invoke(share, from, to, false)
            return
        }

        throw SmbException("Rename is not supported by this SMB client.")
    }

    private fun invokeRm(share: DiskShare, path: String) {
        val methods = share.javaClass.methods.filter { it.name == "rm" }
        val oneArg = methods.firstOrNull { it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
        if (oneArg != null) {
            oneArg.invoke(share, path)
            return
        }

        val twoArg = methods.firstOrNull {
            it.parameterTypes.size == 2
                && it.parameterTypes[0] == String::class.java
                && (it.parameterTypes[1] == java.lang.Boolean.TYPE || it.parameterTypes[1] == java.lang.Boolean::class.java)
        }
        if (twoArg != null) {
            twoArg.invoke(share, path, false)
            return
        }

        throw SmbException("Delete is not supported by this SMB client.")
    }

    private fun invokeRmdir(share: DiskShare, path: String) {
        val methods = share.javaClass.methods.filter { it.name == "rmdir" }
        val oneArg = methods.firstOrNull { it.parameterTypes.contentEquals(arrayOf(String::class.java)) }
        if (oneArg != null) {
            oneArg.invoke(share, path)
            return
        }

        val twoArg = methods.firstOrNull {
            it.parameterTypes.size == 2
                && it.parameterTypes[0] == String::class.java
                && (it.parameterTypes[1] == java.lang.Boolean.TYPE || it.parameterTypes[1] == java.lang.Boolean::class.java)
        }
        if (twoArg != null) {
            twoArg.invoke(share, path, false)
            return
        }

        throw SmbException("Delete is not supported by this SMB client.")
    }

    private fun joinRelPath(parent: String, child: String): String {
        return listOf(parent.trim('/'), child.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun toSmbSharePath(basePath: String, relPath: String): String {
        val a = basePath.trim('/').trim('\\')
        val b = relPath.trim('/').trim('\\')
        val joined = listOf(a, b).filter { it.isNotEmpty() }.joinToString("\\")
        return joined.replace('/', '\\')
    }

    private class SmbStreamingOperation(
        private val folder: NetworkFolder,
        private val password: String
    ) {
        fun openInputStream(smbPath: String): InputStream {
            val smbClient = SMBClient(
                SmbConfig.builder()
                    .withTimeout(10, TimeUnit.SECONDS)
                    .build()
            )
            val connection = smbClient.connect(folder.host, folder.port)
            val auth = com.hierynomus.smbj.auth.AuthenticationContext(folder.username, password.toCharArray(), folder.domain)
            val session = connection.authenticate(auth)
            val share = session.connectShare(folder.share)
            if (share !is DiskShare) {
                runCatching { share.close() }
                runCatching { session.close() }
                runCatching { connection.close() }
                runCatching { smbClient.close() }
                throw SmbException("Selected share is not a disk share.")
            }

            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                SMB2CreateDisposition.FILE_OPEN,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            val input = file.inputStream
            return object : InputStream() {
                override fun read(): Int = input.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len)
                override fun close() {
                    runCatching { input.close() }
                    runCatching { file.close() }
                    runCatching { share.close() }
                    runCatching { session.close() }
                    runCatching { connection.close() }
                    runCatching { smbClient.close() }
                }
            }
        }

        fun openOutputStream(smbPath: String, overwrite: Boolean): OutputStream {
            val smbClient = SMBClient(
                SmbConfig.builder()
                    .withTimeout(10, TimeUnit.SECONDS)
                    .build()
            )
            val connection = smbClient.connect(folder.host, folder.port)
            val auth = com.hierynomus.smbj.auth.AuthenticationContext(folder.username, password.toCharArray(), folder.domain)
            val session = connection.authenticate(auth)
            val share = session.connectShare(folder.share)
            if (share !is DiskShare) {
                runCatching { share.close() }
                runCatching { session.close() }
                runCatching { connection.close() }
                runCatching { smbClient.close() }
                throw SmbException("Selected share is not a disk share.")
            }

            val disposition = if (overwrite) SMB2CreateDisposition.FILE_OVERWRITE_IF else SMB2CreateDisposition.FILE_CREATE
            val file = share.openFile(
                smbPath,
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE, SMB2ShareAccess.FILE_SHARE_DELETE),
                disposition,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            val output = file.outputStream
            return object : OutputStream() {
                override fun write(b: Int) = output.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = output.write(b, off, len)
                override fun flush() = output.flush()
                override fun close() {
                runCatching { output.close() }
                runCatching { file.close() }
                runCatching { share.close() }
                runCatching { session.close() }
                runCatching { connection.close() }
                runCatching { smbClient.close() }
            }
        }
    }
}
}
