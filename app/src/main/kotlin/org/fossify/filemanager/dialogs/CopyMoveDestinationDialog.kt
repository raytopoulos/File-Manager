package org.fossify.filemanager.dialogs

import android.view.View
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.adapters.FilepickerFavoritesAdapter
import org.fossify.commons.adapters.FilepickerItemsAdapter
import org.fossify.commons.databinding.DialogFilepickerBinding
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.internalStoragePath
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.Breadcrumbs
import org.fossify.filemanager.R
import org.fossify.filemanager.activities.SimpleActivity
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.extensions.isSmbPath
import org.fossify.filemanager.extensions.smbFolderId
import org.fossify.filemanager.extensions.smbRelativePath
import org.fossify.filemanager.helpers.AppLog
import org.fossify.filemanager.smb.SmbFileSystem
import java.io.File

class CopyMoveDestinationDialog(
    private val activity: SimpleActivity,
    startPath: String,
    private var showHidden: Boolean,
    private val callback: (destinationDir: String) -> Unit
) : Breadcrumbs.BreadcrumbsListener {
    private val binding = DialogFilepickerBinding.inflate(activity.layoutInflater)
    private val smb = SmbFileSystem(activity)

    private val fileDirItems = ArrayList<FileDirItem>()
    private val itemsAdapter = FilepickerItemsAdapter(activity, fileDirItems, binding.filepickerList) {
        val item = it as? FileDirItem ?: return@FilepickerItemsAdapter
        if (item.isDirectory) {
            openPath(item.path)
        }
    }

    private val favoritesAdapter = FilepickerFavoritesAdapter(activity, ArrayList(activity.config.favorites.toList()), binding.filepickerFavoritesList) {
        val path = it.toString()
        openPath(path)
    }

    private var currentPath = startPath.trimEnd('/').ifBlank { activity.internalStoragePath.trimEnd('/') }

    init {
        activity.updateTextColors(binding.root)
        binding.filepickerFastscroller.updateColors(activity.getProperPrimaryColor())
        binding.filepickerPlaceholder.setTextColor(activity.getProperTextColor())

        binding.filepickerBreadcrumbs.apply {
            listener = this@CopyMoveDestinationDialog
        }

        binding.filepickerList.adapter = itemsAdapter
        binding.filepickerFavoritesList.adapter = favoritesAdapter
        binding.filepickerFab.beVisible()

        binding.filepickerFab.setOnClickListener {
            CreateNewItemDialog(activity, currentPath) { success ->
                if (success) {
                    loadItems()
                }
            }
        }

        binding.filepickerFabShowFavorites.setOnClickListener {
            if (binding.filepickerFavoritesHolder.visibility == View.VISIBLE) {
                binding.filepickerFavoritesHolder.beGone()
                binding.filepickerFilesHolder.beVisible()
            } else {
                binding.filepickerFilesHolder.beGone()
                binding.filepickerFavoritesHolder.beVisible()
            }
        }

        binding.filepickerFabShowHidden.setOnClickListener {
            showHidden = !showHidden
            loadItems()
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.select_folder) { dialog ->
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        callback(currentPath)
                        dialog.dismiss()
                    }
                }
            }

        if (!currentPath.isSmbPath() && !File(currentPath).isDirectory) {
            currentPath = activity.internalStoragePath.trimEnd('/')
        }

        openPath(currentPath)
    }

    override fun breadcrumbClicked(id: Int) {
        if (id == 0) {
            StoragePickerWithNetworkFoldersDialog(activity, currentPath) {
                openPath(it)
            }
            return
        }

        val item = binding.filepickerBreadcrumbs.getItem(id)
        openPath(item.path)
    }

    private fun openPath(path: String) {
        val trimmed = path.trimEnd('/')
        currentPath = if (trimmed.isBlank()) {
            activity.internalStoragePath.trimEnd('/')
        } else if (!trimmed.isSmbPath() && !File(trimmed).isDirectory) {
            activity.internalStoragePath.trimEnd('/')
        } else {
            trimmed
        }
        binding.filepickerBreadcrumbs.setBreadcrumb(currentPath)
        loadItems()
    }

    private fun loadItems() {
        binding.filepickerPlaceholder.setText(org.fossify.commons.R.string.loading)
        binding.filepickerPlaceholder.beVisible()
        ensureBackgroundThread {
            val items = try {
                if (currentPath.isSmbPath()) {
                    smb.list(currentPath.smbFolderId(), currentPath.smbRelativePath())
                        .asSequence()
                        .filter { showHidden || !it.name.startsWith('.') }
                        .sortedBy { it.name.lowercase() }
                        .toList()
                } else {
                    val dir = File(currentPath)
                    dir.listFiles()
                        ?.asSequence()
                        ?.filterNotNull()
                        ?.filter { showHidden || !it.name.startsWith('.') }
                        ?.map { file ->
                            val children = if (file.isDirectory) {
                                file.listFiles()?.count { showHidden || !it.name.startsWith('.') } ?: 0
                            } else {
                                0
                            }
                            FileDirItem(
                                path = file.absolutePath,
                                name = file.name,
                                isDirectory = file.isDirectory,
                                children = children,
                                size = if (file.isDirectory) 0 else file.length(),
                                modified = file.lastModified()
                            )
                        }
                        ?.sortedBy { it.name.lowercase() }
                        ?.toList()
                        ?: emptyList()
                }
            } catch (e: Exception) {
                AppLog.e("CopyMoveDestinationDialog", "Failed loading destination picker items for $currentPath", e)
                emptyList()
            }

            activity.runOnUiThread {
                fileDirItems.clear()
                fileDirItems.addAll(items)
                itemsAdapter.notifyDataSetChanged()
                if (fileDirItems.isEmpty()) {
                    binding.filepickerPlaceholder.setText(org.fossify.commons.R.string.no_items_found)
                    binding.filepickerPlaceholder.beVisible()
                } else {
                    binding.filepickerPlaceholder.beGone()
                }
            }
        }
    }
}
