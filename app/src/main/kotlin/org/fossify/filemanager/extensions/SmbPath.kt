package org.fossify.filemanager.extensions

import android.net.Uri
import org.fossify.filemanager.models.NetworkFolder

private const val SMB_SCHEME_PREFIX = "smb://"

fun String.isSmbPath(): Boolean = startsWith(SMB_SCHEME_PREFIX, ignoreCase = true)

fun String.smbFolderId(): String {
    val authority = Uri.parse(this).authority ?: return ""
    return authority.substringAfterLast("~", authority)
}

fun String.smbRelativePath(): String {
    val path = Uri.parse(this).path ?: return ""
    return path.trimStart('/')
}

fun buildSmbRootPath(folder: NetworkFolder): String {
    val safeName = folder.name
        .trim()
        .replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
        .replace("\\s+".toRegex(), "_")
        .takeIf { it.isNotEmpty() }
        ?: "SMB"

    return "smb://${safeName}~${folder.id}"
}
