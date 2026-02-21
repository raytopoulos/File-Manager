package org.fossify.filemanager.helpers

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.filemanager.extensions.isSmbPath
import org.fossify.filemanager.extensions.smbFolderId
import org.fossify.filemanager.extensions.smbRelativePath
import org.fossify.filemanager.smb.SmbFileSystem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class TransferEngine(private val activity: BaseSimpleActivity) {
    private val smb = SmbFileSystem(activity)

    fun copyMoveSmbToLocal(sourcePaths: List<String>, destinationDir: String, isCopy: Boolean) {
        val destRoot = File(destinationDir)
        require(destRoot.isDirectory) { "Destination must be a directory." }

        val folderId = sourcePaths.first().smbFolderId()
        sourcePaths.forEach { src ->
            val rel = src.smbRelativePath()
            val name = src.getFilenameFromPath()
            val dest = File(destRoot, name)
            copySmbEntryToLocal(folderId, rel, dest)
            if (!isCopy) {
                smb.delete(folderId, rel, recursive = true)
            }
        }
    }

    fun copyMoveLocalToSmb(sourcePaths: List<String>, destFolderId: String, destRelDir: String, isCopy: Boolean) {
        sourcePaths.forEach { src ->
            val file = File(src)
            val name = file.name
            val targetRel = joinRel(destRelDir, name)
            if (file.isDirectory) {
                smb.mkdir(destFolderId, targetRel)
                file.listFiles()?.forEach { child ->
                    copyMoveLocalToSmb(listOf(child.absolutePath), destFolderId, targetRel, isCopy = true)
                }
                if (!isCopy) {
                    file.deleteRecursively()
                }
            } else {
                FileInputStream(file).use { input ->
                    smb.openOutputStream(destFolderId, targetRel, overwrite = true).use { output ->
                        input.copyTo(output)
                    }
                }
                if (!isCopy) {
                    file.delete()
                }
            }
        }
    }

    fun copyMoveSmbToSmb(sourcePaths: List<String>, destFolderId: String, destRelDir: String, isCopy: Boolean) {
        val sourceFolderId = sourcePaths.first().smbFolderId()
        sourcePaths.forEach { src ->
            val srcRel = src.smbRelativePath()
            val name = src.getFilenameFromPath()
            val destRel = joinRel(destRelDir, name)
            copySmbEntryToSmb(sourceFolderId, srcRel, destFolderId, destRel)
            if (!isCopy) {
                smb.delete(sourceFolderId, srcRel, recursive = true)
            }
        }
    }

    private fun copySmbEntryToLocal(folderId: String, relPath: String, dest: File) {
        if (smb.isDirectory(folderId, relPath)) {
            dest.mkdirs()
            val children = smb.list(folderId, relPath)
            children.forEach { child ->
                val childRel = child.path.smbRelativePath()
                val childDest = File(dest, child.name)
                copySmbEntryToLocal(folderId, childRel, childDest)
            }
        } else {
            dest.parentFile?.mkdirs()
            smb.openInputStream(folderId, relPath).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun copySmbEntryToSmb(
        srcFolderId: String,
        srcRelPath: String,
        dstFolderId: String,
        dstRelPath: String
    ) {
        if (smb.isDirectory(srcFolderId, srcRelPath)) {
            smb.mkdir(dstFolderId, dstRelPath)
            val children = smb.list(srcFolderId, srcRelPath)
            children.forEach { child ->
                val childSrcRel = child.path.smbRelativePath()
                val childDstRel = joinRel(dstRelPath, child.name)
                copySmbEntryToSmb(srcFolderId, childSrcRel, dstFolderId, childDstRel)
            }
        } else {
            smb.openInputStream(srcFolderId, srcRelPath).use { input ->
                smb.openOutputStream(dstFolderId, dstRelPath, overwrite = true).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun joinRel(parent: String, child: String): String {
        return listOf(parent.trim('/'), child.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
    }
}
