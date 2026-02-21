package org.fossify.filemanager.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.beVisible
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.R
import org.fossify.filemanager.adapters.SmbHostsAdapter
import org.fossify.filemanager.databinding.ActivitySmbScanBinding
import org.fossify.filemanager.databinding.DialogSmbCredentialsBinding
import org.fossify.filemanager.databinding.DialogSmbFinalizeBinding
import org.fossify.filemanager.helpers.NetworkFoldersRepository
import org.fossify.filemanager.models.NetworkFolder
import org.fossify.filemanager.models.NetworkFolderType
import org.fossify.filemanager.helpers.AppLog
import org.fossify.filemanager.smb.SmbNetworkScanner
import org.fossify.filemanager.smb.SmbFileSystem
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class SmbScanActivity : SimpleActivity() {
    companion object {
        const val EXTRA_OPEN_PATH = "extra_open_path"
        private const val LOG_TAG = "SmbScan"
    }

    private val binding by viewBinding(ActivitySmbScanBinding::inflate)
    private val cancelled = AtomicBoolean(false)
    private val hosts = ArrayList<org.fossify.filemanager.smb.SmbDiscoveredHost>()

    private lateinit var adapter: SmbHostsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        adapter = SmbHostsAdapter(this) { host ->
            if (!cancelled.get()) {
                showCredentialsDialog(host.ip)
            }
        }
        binding.smbScanList.adapter = adapter

        binding.smbScanCancel.setOnClickListener {
            cancelled.set(true)
            finish()
        }

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(smbScanList, smbScanCancel))
            setupMaterialScrollListener(smbScanList, smbScanAppbar)
        }

        startScan()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.smbScanAppbar, NavigationIcon.Arrow)
    }

    private fun startScan() {
        binding.smbScanProgress.isIndeterminate = true
        updateStatus(found = 0, scanned = 0, total = 0)

        ensureBackgroundThread {
            try {
                SmbNetworkScanner().scan(
                    cancelled = cancelled,
                    onProgress = { progress ->
                        runOnUiThread {
                            updateStatus(progress.found, progress.scanned, progress.total)
                            if (progress.scanned >= progress.total) {
                                binding.smbScanProgress.isIndeterminate = false
                                binding.smbScanProgress.progress = 100
                            }
                        }
                    },
                    onHostFound = { host ->
                        synchronized(hosts) {
                            if (hosts.none { it.ip == host.ip }) {
                                hosts.add(host)
                            }
                        }
                        runOnUiThread {
                            adapter.setItems(ArrayList(hosts))
                        }
                    }
                )
            } catch (e: Exception) {
                AppLog.e(LOG_TAG, "SMB scan failed", e)
            }
        }
    }

    private fun updateStatus(found: Int, scanned: Int, total: Int) {
        val status = getString(R.string.smb_scan_status, found, scanned, total)
        binding.smbScanStatus.text = status
        binding.smbScanStatus.setTextColor(getProperTextColor())
        binding.smbScanProgress.setIndicatorColor(getProperPrimaryColor())
    }

    private fun showCredentialsDialog(host: String) {
        val credsBinding = DialogSmbCredentialsBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)

        credsBinding.smbHost.text = host

        setupDialogStuff(credsBinding.root, builder, R.string.smb_credentials) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = credsBinding.smbUsername.value
                val password = credsBinding.smbPassword.value
                val domain = credsBinding.smbDomain.value.takeIf { it.isNotEmpty() }
                dialog.dismiss()
                showFinalizeDialog(host, username, password, domain)
            }
        }
    }

    private fun showFinalizeDialog(host: String, username: String, password: String, domain: String?) {
        val finalBinding = DialogSmbFinalizeBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(this)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)

        finalBinding.smbName.setText(host)
        finalBinding.smbShare.setText("")
        finalBinding.smbBasePath.setText("")

        setupDialogStuff(finalBinding.root, builder, R.string.add_network_folder) { dialog ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(View.OnClickListener {
                val name = finalBinding.smbName.value
                val share = finalBinding.smbShare.value
                val basePath = finalBinding.smbBasePath.value.trim().trim('/')
                if (name.isEmpty()) {
                    toast(R.string.empty_name)
                    return@OnClickListener
                }
                if (share.isEmpty()) {
                    toast(R.string.empty_name)
                    return@OnClickListener
                }

                val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                val negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                finalBinding.smbError.beGone()
                finalBinding.smbStatus.beVisible()
                finalBinding.smbProgress.beVisible()
                finalBinding.smbStatus.text = getString(R.string.loading)
                positive.isEnabled = false
                negative.isEnabled = false
                finalBinding.smbName.isEnabled = false
                finalBinding.smbShare.isEnabled = false
                finalBinding.smbBasePath.isEnabled = false

                ensureBackgroundThread {
                    try {
                        AppLog.i(LOG_TAG, "Testing SMB connection to $host:445/$share")
                        SmbFileSystem(this).testConnection(host, 445, username, password, domain, share)
                        val folder = NetworkFolder(
                            id = UUID.randomUUID().toString().replace("-", ""),
                            type = NetworkFolderType.SMB,
                            name = name,
                            host = host,
                            port = 445,
                            username = username,
                            domain = domain,
                            share = share,
                            basePath = basePath,
                            createdAt = System.currentTimeMillis()
                        )
                        NetworkFoldersRepository(this).upsert(folder, password)
                        val result = Intent().putExtra(EXTRA_OPEN_PATH, org.fossify.filemanager.extensions.buildSmbRootPath(folder))
                        runOnUiThread {
                            setResult(Activity.RESULT_OK, result)
                            finish()
                        }
                    } catch (e: Exception) {
                        AppLog.e(LOG_TAG, "SMB save failed for $host", e)
                        runOnUiThread {
                            finalBinding.smbProgress.beGone()
                            finalBinding.smbStatus.beGone()
                            finalBinding.smbError.beVisible()
                            finalBinding.smbError.text = e.message?.takeIf { it.isNotBlank() }
                                ?: e.javaClass.simpleName
                            positive.isEnabled = true
                            negative.isEnabled = true
                            finalBinding.smbName.isEnabled = true
                            finalBinding.smbShare.isEnabled = true
                            finalBinding.smbBasePath.isEnabled = true
                        }
                    }
                }
            })
        }
    }
}
