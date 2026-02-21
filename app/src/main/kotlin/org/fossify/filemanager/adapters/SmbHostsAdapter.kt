package org.fossify.filemanager.adapters

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.filemanager.databinding.ItemSmbHostBinding
import org.fossify.filemanager.smb.SmbDiscoveredHost
import org.fossify.filemanager.activities.SimpleActivity

class SmbHostsAdapter(
    private val activity: SimpleActivity,
    private val onClick: (SmbDiscoveredHost) -> Unit
) : RecyclerView.Adapter<SmbHostsAdapter.VH>() {

    private val hosts = ArrayList<SmbDiscoveredHost>()

    fun setItems(newItems: List<SmbDiscoveredHost>) {
        hosts.clear()
        hosts.addAll(newItems.sortedBy { it.ip })
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemSmbHostBinding.inflate(activity.layoutInflater, parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(hosts[position])
    }

    override fun getItemCount() = hosts.size

    inner class VH(private val binding: ItemSmbHostBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(host: SmbDiscoveredHost) {
            val textColor = activity.getProperTextColor()
            binding.smbHostIp.apply {
                text = host.ip
                setTextColor(textColor)
            }
            binding.smbHostName.apply {
                text = host.hostname ?: ""
                setTextColor(textColor)
            }
            binding.root.setOnClickListener { onClick(host) }
        }
    }
}
