package org.fossify.filemanager.activities

import android.graphics.Paint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.models.RadioItem
import org.fossify.filemanager.R
import org.fossify.filemanager.adapters.NetworkFoldersAdapter
import org.fossify.filemanager.databinding.ActivityNetworkFoldersBinding
import org.fossify.filemanager.databinding.DialogAddNetworkFolderBinding
import org.fossify.filemanager.databinding.DialogSmbFinalizeBinding
import org.fossify.filemanager.extensions.buildSmbRootPath
import org.fossify.filemanager.helpers.AppLog
import org.fossify.filemanager.helpers.NetworkFoldersRepository
import org.fossify.filemanager.models.NetworkFolder
import org.fossify.filemanager.models.NetworkFolderType
import org.fossify.filemanager.smb.SmbFileSystem
import java.util.UUID

class NetworkFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {
    companion object {
        const val EXTRA_OPEN_PATH = "extra_open_path"
        private const val SMB_SCAN_RC = 9101
        private const val LOG_TAG = "NetworkFolders"
    }

    private val binding by viewBinding(ActivityNetworkFoldersBinding::inflate)
    private val repo by lazy { NetworkFoldersRepository(this) }
    private var adapter: NetworkFoldersAdapter? = null

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
                R.id.scan_smb -> scanSmb()
                R.id.copy_debug_log -> copyDebugLog()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun copyDebugLog() {
        val text = AppLog.dump().joinToString("\n\n")
        copyToClipboard(text)
        toast(R.string.debug_log_copied)
    }

    private fun updateFolders() {
        binding.apply {
            val folders = repo.getAll()
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

            if (adapter == null) {
                adapter = NetworkFoldersAdapter(
                    activity = this@NetworkFoldersActivity,
                    folders = ArrayList(folders),
                    listener = this@NetworkFoldersActivity,
                    recyclerView = manageNetworkFoldersList,
                    onEdit = { editFolder(it) }
                ) { clicked ->
                    val folder = clicked as NetworkFolder
                    val result = Intent().putExtra(EXTRA_OPEN_PATH, buildSmbRootPath(folder))
                    setResult(Activity.RESULT_OK, result)
                    finish()
                }.also {
                    manageNetworkFoldersList.adapter = it
                }
            } else {
                adapter?.updateItems(folders)
            }
        }
    }

    override fun refreshItems() {
        updateFolders()
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

        setupDialogStuff(binding.root, builder, R.string.add_network_folder) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val isSmb = binding.networkFolderType.checkedRadioButtonId == R.id.type_smb
                if (!isSmb) {
                    toast(R.string.coming_soon)
                    return@setOnClickListener
                }

                val name = binding.networkFolderName.value
                val host = binding.networkFolderAddress.value
                val port = binding.networkFolderPort.value.toIntOrNull() ?: 445
                val username = binding.networkFolderUsername.value
                val domain = binding.networkFolderDomain.value.takeIf { it.isNotEmpty() }
                val password = binding.networkFolderPassword.value

                if (name.isEmpty()) {
                    toast(R.string.empty_name)
                    return@setOnClickListener
                }
                if (host.isEmpty()) {
                    toast(R.string.empty_name)
                    return@setOnClickListener
                }

                dialog.dismiss()
                showFinalizeDialog(
                    name,
                    host,
                    port,
                    username,
                    password,
                    domain,
                    existingId = null,
                    existingCreatedAt = null
                )
            }
        }
    }

    private fun editFolder(folder: NetworkFolder) {
        if (folder.type != NetworkFolderType.SMB) {
            toast(R.string.coming_soon)
            return
        }

        val binding = DialogAddNetworkFolderBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)

        binding.typeSmb.isChecked = true
        binding.networkFolderName.setText(folder.name)
        binding.networkFolderAddress.setText(folder.host)
        binding.networkFolderPort.setText(folder.port.toString())
        binding.networkFolderUsername.setText(folder.username)
        binding.networkFolderDomain.setText(folder.domain ?: "")
        binding.networkFolderPassword.setText(repo.getPassword(folder.id) ?: "")

        setupDialogStuff(binding.root, builder, R.string.edit) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                val name = binding.networkFolderName.value
                val host = binding.networkFolderAddress.value
                val port = binding.networkFolderPort.value.toIntOrNull() ?: 445
                val username = binding.networkFolderUsername.value
                val domain = binding.networkFolderDomain.value.takeIf { it.isNotEmpty() }
                val password = binding.networkFolderPassword.value
                if (name.isEmpty() || host.isEmpty()) {
                    toast(R.string.empty_name)
                    return@OnClickListener
                }
                dialog.dismiss()
                showFinalizeDialog(
                    name,
                    host,
                    port,
                    username,
                    password,
                    domain,
                    existingId = folder.id,
                    existingCreatedAt = folder.createdAt,
                    existingShare = folder.share,
                    existingBasePath = folder.basePath
                )
            })
        }
    }

    private fun showFinalizeDialog(
        name: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        domain: String?,
        existingId: String?,
        existingCreatedAt: Long?,
        existingShare: String? = null,
        existingBasePath: String? = null
    ) {
        val binding = DialogSmbFinalizeBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)

        binding.smbName.setText(name)
        binding.smbShare.setText(existingShare ?: "")
        binding.smbBasePath.setText(existingBasePath ?: "")

        setupDialogStuff(binding.root, builder, R.string.add_network_folder) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val finalName = binding.smbName.value
                val share = binding.smbShare.value
                val basePath = binding.smbBasePath.value.trim().trim('/')
                if (finalName.isEmpty()) {
                    toast(R.string.empty_name)
                    return@setOnClickListener
                }
                if (share.isEmpty()) {
                    toast(R.string.empty_name)
                    return@setOnClickListener
                }

                val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                binding.smbError.beGone()
                binding.smbStatus.beVisible()
                binding.smbProgress.beVisible()
                binding.smbStatus.text = getString(R.string.loading)
                positive.isEnabled = false
                negative.isEnabled = false
                binding.smbName.isEnabled = false
                binding.smbShare.isEnabled = false
                binding.smbBasePath.isEnabled = false

                ensureBackgroundThread {
                    try {
                        AppLog.i(LOG_TAG, "Testing SMB connection to $host:$port/$share")
                        SmbFileSystem(this).testConnection(host, port, username, password, domain, share)
                        val id = existingId ?: UUID.randomUUID().toString().replace("-", "")
                        val createdAt = existingCreatedAt ?: System.currentTimeMillis()
                        val folder = NetworkFolder(
                            id = id,
                            type = NetworkFolderType.SMB,
                            name = finalName,
                            host = host,
                            port = port,
                            username = username,
                            domain = domain,
                            share = share,
                            basePath = basePath,
                            createdAt = createdAt
                        )
                        repo.upsert(folder, password)
                        runOnUiThread {
                            updateFolders()
                            dialog.dismiss()
                        }
                    } catch (e: Exception) {
                        AppLog.e(LOG_TAG, "SMB add/edit failed for $host:$port/$share", e)
                        runOnUiThread {
                            binding.smbProgress.beGone()
                            binding.smbStatus.beGone()
                            binding.smbError.beVisible()
                            binding.smbError.text = e.message?.takeIf { it.isNotBlank() }
                                ?: e.javaClass.simpleName
                            positive.isEnabled = true
                            negative.isEnabled = true
                            binding.smbName.isEnabled = true
                            binding.smbShare.isEnabled = true
                            binding.smbBasePath.isEnabled = true
                        }
                    }
                }
            }
        }
    }

    private fun scanSmb() {
        startActivityForResult(Intent(this, SmbScanActivity::class.java), SMB_SCAN_RC)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == SMB_SCAN_RC && resultCode == Activity.RESULT_OK) {
            val path = resultData?.getStringExtra(SmbScanActivity.EXTRA_OPEN_PATH)
            if (!path.isNullOrEmpty()) {
                setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_OPEN_PATH, path))
                finish()
            } else {
                updateFolders()
            }
        }
    }
}
