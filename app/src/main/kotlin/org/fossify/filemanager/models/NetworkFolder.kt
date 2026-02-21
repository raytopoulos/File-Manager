package org.fossify.filemanager.models

enum class NetworkFolderType {
    SMB,
    FTP
}

data class NetworkFolder(
    val id: String,
    val type: NetworkFolderType,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val domain: String?,
    val share: String,
    val basePath: String,
    val createdAt: Long
)

