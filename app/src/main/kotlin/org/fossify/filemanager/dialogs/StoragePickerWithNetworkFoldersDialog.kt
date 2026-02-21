package org.fossify.filemanager.dialogs

import org.fossify.commons.dialogs.RadioGroupDialog
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.sdCardPath
import org.fossify.commons.models.RadioItem
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.extensions.buildSmbRootPath
import org.fossify.filemanager.extensions.isSmbPath
import org.fossify.filemanager.extensions.smbFolderId
import org.fossify.filemanager.helpers.NetworkFoldersRepository
import org.fossify.filemanager.models.NetworkFolderType

class StoragePickerWithNetworkFoldersDialog(
    private val activity: SimpleActivity,
    private val currentPath: String,
    private val callback: (path: String) -> Unit
) {
    init {
        val items = ArrayList<RadioItem>()

        val internalPath = activity.internalStoragePath.trimEnd('/')
        items.add(RadioItem(0, activity.getString(R.string.internal), internalPath))

        val sdPath = activity.sdCardPath.trimEnd('/')
        if (sdPath.isNotEmpty() && sdPath != internalPath) {
            items.add(RadioItem(1, activity.getString(R.string.sd_card), sdPath))
        }

        val smbFolders = NetworkFoldersRepository(activity).getAll().filter { it.type == NetworkFolderType.SMB }
        smbFolders.forEachIndexed { index, folder ->
            val root = buildSmbRootPath(folder)
            val label = "${folder.name} (${folder.host}/${folder.share})"
            items.add(RadioItem(100 + index, label, root))
        }

        val checkedIndex = getCheckedIndex(items)
        RadioGroupDialog(activity, items, checkedIndex, R.string.select_storage) {
            callback(it.toString())
        }
    }

    private fun getCheckedIndex(items: List<RadioItem>): Int {
        val curr = currentPath.trimEnd('/')
        if (curr.isSmbPath()) {
            val folderId = curr.smbFolderId()
            return items.indexOfFirst { it.value.toString().isSmbPath() && it.value.toString().smbFolderId() == folderId }
        }

        return items.indexOfFirst { curr.startsWith(it.value.toString().trimEnd('/'), ignoreCase = true) }
    }
}

