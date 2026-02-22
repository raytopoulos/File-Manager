package org.fossify.filemanager.models

data class ClipboardItem(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
)

data class ClipboardOperation(
    val items: ArrayList<ClipboardItem>,
    val sourceParentPath: String,
    val isCopyOperation: Boolean,
)

