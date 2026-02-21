package org.fossify.filemanager.helpers

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.fossify.filemanager.extensions.config
import org.fossify.filemanager.models.NetworkFolder
import org.fossify.filemanager.models.NetworkFolderType
import org.json.JSONArray
import org.json.JSONObject

class NetworkFoldersRepository(private val context: Context) {
    companion object {
        private const val SECRETS_PREFS = "network_folders_secrets"
        private const val PWD_PREFIX = "network_folder_pwd_"
    }

    private val secrets by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            SECRETS_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getAll(): List<NetworkFolder> {
        val raw = context.config.networkFoldersV1Json
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val folders = ArrayList<NetworkFolder>(arr.length())
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            folders.add(obj.toNetworkFolder() ?: continue)
        }
        return folders.sortedByDescending { it.createdAt }
    }

    fun getById(id: String): NetworkFolder? = getAll().firstOrNull { it.id == id }

    fun upsert(folder: NetworkFolder, password: String?) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == folder.id }
        current.add(folder)
        saveAll(current)
        if (password != null) {
            secrets.edit().putString(PWD_PREFIX + folder.id, password).apply()
        }
    }

    fun delete(id: String) {
        val current = getAll().toMutableList()
        current.removeAll { it.id == id }
        saveAll(current)
        secrets.edit().remove(PWD_PREFIX + id).apply()
    }

    fun getPassword(id: String): String? = secrets.getString(PWD_PREFIX + id, null)

    private fun saveAll(folders: List<NetworkFolder>) {
        val arr = JSONArray()
        folders.forEach {
            arr.put(it.toJson())
        }
        context.config.networkFoldersV1Json = arr.toString()
    }

    private fun JSONObject.toNetworkFolder(): NetworkFolder? {
        val id = optString("id")
        if (id.isNullOrEmpty()) return null

        val type = runCatching { NetworkFolderType.valueOf(optString("type")) }.getOrNull() ?: return null
        val name = optString("name")
        val host = optString("host")
        val port = optInt("port", 445)
        val username = optString("username")
        val domain = optString("domain").takeIf { it.isNotEmpty() }
        val share = optString("share")
        val basePath = optString("basePath")
        val createdAt = optLong("createdAt", 0L)

        if (name.isEmpty() || host.isEmpty() || share.isEmpty()) return null

        return NetworkFolder(
            id = id,
            type = type,
            name = name,
            host = host,
            port = port,
            username = username,
            domain = domain,
            share = share,
            basePath = basePath,
            createdAt = createdAt
        )
    }

    private fun NetworkFolder.toJson(): JSONObject {
        return JSONObject()
            .put("id", id)
            .put("type", type.name)
            .put("name", name)
            .put("host", host)
            .put("port", port)
            .put("username", username)
            .put("domain", domain ?: "")
            .put("share", share)
            .put("basePath", basePath)
            .put("createdAt", createdAt)
    }
}

