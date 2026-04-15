package com.smartlandmarks.ui.activity

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.smartlandmarks.data.local.VisitHistoryEntity
import com.smartlandmarks.databinding.ItemVisitHistoryBinding
import java.text.SimpleDateFormat
import java.util.*

class VisitHistoryAdapter :
    ListAdapter<VisitHistoryEntity, VisitHistoryAdapter.VisitViewHolder>(DiffCallback()) {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())

    inner class VisitViewHolder(private val binding: ItemVisitHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(visit: VisitHistoryEntity) {
            binding.tvLandmarkName.text = visit.landmarkName
            binding.tvVisitTime.text = dateFormat.format(Date(visit.visitTime))
            binding.tvDistance.text = if (visit.distance != null)
                "Distance: ${"%.2f".format(visit.distance)} km"
            else
                "Distance: N/A"
            binding.tvSyncStatus.text = if (visit.synced) "✓ Synced" else "⏳ Pending sync"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VisitViewHolder {
        val binding = ItemVisitHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VisitViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VisitViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<VisitHistoryEntity>() {
        override fun areItemsTheSame(a: VisitHistoryEntity, b: VisitHistoryEntity) = a.id == b.id
        override fun areContentsTheSame(a: VisitHistoryEntity, b: VisitHistoryEntity) = a == b
    }
}
