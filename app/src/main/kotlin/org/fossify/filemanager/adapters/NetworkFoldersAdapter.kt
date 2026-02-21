package org.fossify.filemanager.adapters

import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.adapters.MyRecyclerViewAdapter
import org.fossify.commons.extensions.getPopupMenuTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.commons.views.MyRecyclerView
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ItemNetworkFolderBinding
import org.fossify.filemanager.helpers.NetworkFoldersRepository
import org.fossify.filemanager.models.NetworkFolder

class NetworkFoldersAdapter(
    activity: BaseSimpleActivity,
    private var folders: ArrayList<NetworkFolder>,
    private val listener: RefreshRecyclerViewListener?,
    recyclerView: MyRecyclerView,
    private val onEdit: (NetworkFolder) -> Unit,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {
    private val repo = NetworkFoldersRepository(activity)

    override fun getActionMenuId() = R.menu.cab_network_folder

    override fun actionItemPressed(id: Int) {
        when (id) {
            R.id.cab_edit -> editSelection()
            R.id.cab_remove -> removeSelection()
        }
    }

    override fun getSelectableItemCount() = folders.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = folders.getOrNull(position)?.id?.hashCode()

    override fun getItemKeyPosition(key: Int) = folders.indexOfFirst { it.id.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    override fun prepareActionMode(menu: Menu) {}

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return createViewHolder(ItemNetworkFolderBinding.inflate(layoutInflater, parent, false).root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val folder = folders[position]
        holder.bindView(folder, true, true) { itemView, layoutPosition ->
            setupView(itemView, folder, selectedKeys.contains(folder.id.hashCode()))
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = folders.size

    fun updateItems(newFolders: List<NetworkFolder>) {
        folders = ArrayList(newFolders)
        notifyDataSetChanged()
    }

    private fun setupView(view: View, folder: NetworkFolder, isSelected: Boolean) {
        ItemNetworkFolderBinding.bind(view).apply {
            root.setupViewBackground(activity)
            networkFolderTitle.apply {
                text = folder.name
                setTextColor(activity.getProperTextColor())
            }

            networkFolderSubtitle.apply {
                text = "${folder.host} / ${folder.share}"
                setTextColor(activity.getProperTextColor())
            }

            networkFolderHolder.isSelected = isSelected

            overflowMenuIcon.drawable.apply {
                mutate()
                setTint(activity.getProperTextColor())
            }
            overflowMenuIcon.setOnClickListener {
                showPopupMenu(overflowMenuAnchor, folder)
            }
        }
    }

    private fun showPopupMenu(view: View, folder: NetworkFolder) {
        finishActMode()
        val theme = activity.getPopupMenuTheme()
        val contextTheme = ContextThemeWrapper(activity, theme)

        PopupMenu(contextTheme, view, Gravity.END).apply {
            inflate(getActionMenuId())
            setOnMenuItemClickListener { item ->
                val key = folder.id.hashCode()
                when (item.itemId) {
                    R.id.cab_edit -> executeItemMenuOperation(key) { editSelection() }
                    R.id.cab_remove -> executeItemMenuOperation(key) { removeSelection() }
                }
                true
            }
            show()
        }
    }

    private fun executeItemMenuOperation(key: Int, callback: () -> Unit) {
        selectedKeys.clear()
        selectedKeys.add(key)
        callback()
    }

    private fun getSelectedFolder(): NetworkFolder? {
        val key = selectedKeys.firstOrNull() ?: return null
        return folders.firstOrNull { it.id.hashCode() == key }
    }

    private fun editSelection() {
        val folder = getSelectedFolder() ?: return
        selectedKeys.clear()
        onEdit(folder)
    }

    private fun removeSelection() {
        val folder = getSelectedFolder() ?: return
        repo.delete(folder.id)
        folders.removeAll { it.id == folder.id }
        notifyDataSetChanged()
        selectedKeys.clear()
        if (folders.isEmpty()) {
            listener?.refreshItems()
        }
    }
}

