package org.fossify.filemanager

import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.helpers.AppLog
import java.io.File

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        AppLog.i("App", "onCreate()")
        Reprint.initialize(this)
        cleanupSmbCache()
    }

    private fun cleanupSmbCache() {
        ensureBackgroundThread {
            val smbCache = File(cacheDir, "smb")
            if (!smbCache.exists()) return@ensureBackgroundThread

            val maxAgeMs = 7L * 24 * 60 * 60 * 1000
            val maxBytes = 250L * 1024 * 1024
            val now = System.currentTimeMillis()

            val files = smbCache.walkTopDown().filter { it.isFile }.toList()
            files.filter { now - it.lastModified() > maxAgeMs }.forEach { runCatching { it.delete() } }

            val remaining = smbCache.walkTopDown().filter { it.isFile }.toList()
            var total = remaining.sumOf { it.length() }
            if (total <= maxBytes) return@ensureBackgroundThread

            remaining.sortedBy { it.lastModified() }.forEach {
                if (total <= maxBytes) return@forEach
                val len = it.length()
                if (runCatching { it.delete() }.getOrDefault(false)) {
                    total -= len
                }
            }
        }
    }
}
