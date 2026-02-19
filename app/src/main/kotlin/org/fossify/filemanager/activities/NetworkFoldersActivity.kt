package org.fossify.filemanager.activities

import android.graphics.Paint
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityNetworkFoldersBinding
import org.fossify.filemanager.databinding.DialogAddNetworkFolderBinding

class NetworkFoldersActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityNetworkFoldersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        updateFolders()
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(manageNetworkFoldersList))
            setupMaterialScrollListener(binding.manageNetworkFoldersList, binding.manageNetworkFoldersAppbar)
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.manageNetworkFoldersAppbar, NavigationIcon.Arrow)
    }

    private fun setupOptionsMenu() {
        binding.manageNetworkFoldersToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.add_network_folder -> addNetworkFolder()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateFolders() {
        binding.apply {
            val folders = ArrayList<String>()
            // TODO: fetch network folders from config
            manageNetworkFoldersPlaceholder.beVisibleIf(folders.isEmpty())
            manageNetworkFoldersPlaceholder.setTextColor(getProperTextColor())

            manageNetworkFoldersPlaceholder2.apply {
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                beVisibleIf(folders.isEmpty())
                setTextColor(getProperPrimaryColor())
                setOnClickListener {
                    addNetworkFolder()
                }
            }

            // TODO: set adapter for manageNetworkFoldersList
        }
    }

    private fun addNetworkFolder() {
        val binding = DialogAddNetworkFolderBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)

        binding.networkFolderType.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.type_smb) {
                binding.networkFolderPort.hint = "445"
            } else {
                binding.networkFolderPort.hint = "21"
            }
        }

        // Initialize default port hint
        binding.networkFolderPort.hint = "445"

        setupDialogStuff(binding.root, builder, R.string.add_network_folder) {
            // TODO: add network folder
        }
    }
}
