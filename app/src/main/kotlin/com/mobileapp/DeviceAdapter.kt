package com.mobileapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mobileapp.data.Device

class DeviceAdapter(
    private val onDeviceClick: (Device) -> Unit,
    private val onDeviceDelete: (Device) -> Unit
) : ListAdapter<Device, DeviceAdapter.ViewHolder>(DeviceDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvDeviceName)
        private val tvMac: TextView = itemView.findViewById(R.id.tvMacAddress)
        private val tvCount: TextView = itemView.findViewById(R.id.tvFileCount)
        private val btnOpen: Button = itemView.findViewById(R.id.btnOpen)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(device: Device) {
            tvName.text = device.name
            tvMac.text = device.macAddress
            tvCount.text = itemView.context.getString(R.string.storage_files_count, device.fileCount)

            // Show/hide buttons based on device type
            if (device.id == 0L) {
                // Imported - only show Open
                btnDelete.visibility = View.GONE
                btnOpen.text = "Открыть"
            } else {
                btnDelete.visibility = View.VISIBLE
                btnOpen.text = "Открыть"
            }

            // Hide card click, use buttons instead
            itemView.setOnClickListener(null)
            
            btnOpen.setOnClickListener { onDeviceClick(device) }
            btnDelete.setOnClickListener { onDeviceDelete(device) }
        }
    }

    class DeviceDiffCallback : DiffUtil.ItemCallback<Device>() {
        override fun areItemsTheSame(oldItem: Device, newItem: Device) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Device, newItem: Device) = oldItem == newItem
    }
}