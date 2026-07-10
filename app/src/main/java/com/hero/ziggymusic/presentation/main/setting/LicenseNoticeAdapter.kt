package com.hero.ziggymusic.presentation.main.setting

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.hero.ziggymusic.databinding.ItemLicenseNoticeBinding
import com.hero.ziggymusic.presentation.main.setting.model.LicenseNotice

class LicenseNoticeAdapter(
    private val onNoticeClick: (LicenseNotice) -> Unit,
) : ListAdapter<LicenseNotice, LicenseNoticeAdapter.LicenseNoticeViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): LicenseNoticeViewHolder {
        val binding = ItemLicenseNoticeBinding
            .inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )

        return LicenseNoticeViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: LicenseNoticeViewHolder,
        position: Int,
    ) {
        val item = getItem(position)

        holder.bind(
            notice = item,
            onNoticeClick = onNoticeClick,
        )
    }

    class LicenseNoticeViewHolder(
        private val binding: ItemLicenseNoticeBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            notice: LicenseNotice,
            onNoticeClick: (LicenseNotice) -> Unit,
        ) {
            binding.tvLicenseName.text = notice.name

            binding.tvLicenseSummary.text = notice.licenseSummary
            binding.tvLicenseSummary.isVisible = notice.licenseSummary.isNotBlank()

            binding.root.setOnClickListener {
                onNoticeClick(notice)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<LicenseNotice>() {
            override fun areItemsTheSame(oldItem: LicenseNotice, newItem: LicenseNotice): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(
                oldItem: LicenseNotice,
                newItem: LicenseNotice,
            ): Boolean {
                return oldItem == newItem
            }
        }
    }
}
