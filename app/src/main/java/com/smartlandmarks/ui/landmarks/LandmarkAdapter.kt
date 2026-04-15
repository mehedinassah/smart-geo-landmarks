package com.smartlandmarks.ui.landmarks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.smartlandmarks.R
import com.smartlandmarks.data.model.Landmark
import com.smartlandmarks.databinding.ItemLandmarkBinding
import com.smartlandmarks.utils.Constants

class LandmarkAdapter(
    private val onVisitClick: (Landmark) -> Unit,
    private val onDeleteClick: (Landmark) -> Unit
) : ListAdapter<Landmark, LandmarkAdapter.LandmarkViewHolder>(DiffCallback()) {

    inner class LandmarkViewHolder(private val binding: ItemLandmarkBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(landmark: Landmark) {
            binding.tvTitle.text = landmark.title
            binding.tvScore.text = "Score: ${"%.1f".format(landmark.score)}"
            binding.tvVisitCount.text = "Visits: ${landmark.visitCount}"
            landmark.avgDistance?.let {
                binding.tvAvgDistance.text = "Avg dist: ${"%.2f".format(it)} km"
            }

            // Score color indicator
            val colorRes = when {
                landmark.score < Constants.SCORE_LOW_THRESHOLD -> R.color.score_low
                landmark.score < Constants.SCORE_MED_THRESHOLD -> R.color.score_medium
                else -> R.color.score_high
            }
            binding.scoreIndicator.setBackgroundColor(
                binding.root.context.getColor(colorRes)
            )

            Glide.with(binding.root.context)
                .load(landmark.image)
                .placeholder(R.drawable.ic_landmark_placeholder)
                .error(R.drawable.ic_landmark_placeholder)
                .centerCrop()
                .into(binding.ivLandmark)

            binding.btnVisit.setOnClickListener { onVisitClick(landmark) }
            binding.btnDelete.setOnClickListener { onDeleteClick(landmark) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LandmarkViewHolder {
        val binding = ItemLandmarkBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LandmarkViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LandmarkViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class DiffCallback : DiffUtil.ItemCallback<Landmark>() {
        override fun areItemsTheSame(oldItem: Landmark, newItem: Landmark) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Landmark, newItem: Landmark) = oldItem == newItem
    }
}
