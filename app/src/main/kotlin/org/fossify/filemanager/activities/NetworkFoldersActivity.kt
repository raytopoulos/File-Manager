package org.fossify.filemanager.activities

import android.os.Bundle
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.databinding.ActivityNetworkFoldersBinding

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
        // TODO: Add menu item for adding a new network folder
    }

    private fun updateFolders() {
        // TODO: display a placeholder when no network folders are added yet
    }
}
