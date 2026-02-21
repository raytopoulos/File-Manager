package org.fossify.filemanager.dialogs

import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.R
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.filemanager.extensions.smbRelativePath
import org.fossify.filemanager.helpers.AppLog
import org.fossify.filemanager.models.NetworkFolder
import org.fossify.filemanager.smb.SmbFileSystem

class SmbDirectoryPickerDialog(
    private val activity: BaseSimpleActivity,
    private val folder: NetworkFolder,
    private val initialRelPath: String,
    private val callback: (selectedRelPath: String) -> Unit
) {
    private val smb = SmbFileSystem(activity)

    init {
        showFor(initialRelPath.trim('/'))
    }

    private fun showFor(currentRel: String) {
        ensureBackgroundThread {
            try {
                val dirs = smb.list(folder.id, currentRel)
                    .filter { it.isDirectory }
                    .sortedBy { it.name.lowercase() }

                activity.runOnUiThread {
                    val labels = ArrayList<String>()
                    val rels = ArrayList<String>()
                    if (currentRel.isNotEmpty()) {
                        labels.add("..")
                        rels.add("__UP__")
                    }
                    dirs.forEach {
                        labels.add(it.name)
                        rels.add(it.path.smbRelativePath())
                    }

                    val builder = MaterialAlertDialogBuilder(activity)
                        .setPositiveButton(R.string.select_here, null)
                        .setNegativeButton(R.string.cancel, null)
                        .setTitle(activity.getString(R.string.select_folder))

                    if (labels.isNotEmpty()) {
                        builder.setItems(labels.toTypedArray()) { _, which ->
                            val sel = rels[which]
                            if (sel == "__UP__") {
                                showFor(currentRel.substringBeforeLast("/", ""))
                            } else {
                                showFor(sel)
                            }
                        }
                    }

                    val dialog = builder.create()
                    dialog.show()
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        dialog.dismiss()
                        callback(currentRel)
                    }
                }
            } catch (e: Exception) {
                AppLog.e("SmbDirectoryPicker", "SMB directory list failed", e)
            }
        }
    }
}
