package com.app.photocloud.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.app.photocloud.data.model.ItemPhoto
import com.app.photocloud.databinding.ItemPhotoPagerBinding
import java.io.File

class PhotoPagerAdapter(
    private val onImageLoaded: (String) -> Unit
) : ListAdapter<ItemPhoto, PhotoPagerAdapter.PhotoViewHolder>(PhotoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoPagerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PhotoViewHolder(binding, onImageLoaded)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PhotoViewHolder(
        private val binding: ItemPhotoPagerBinding,
        private val onImageLoaded: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: ItemPhoto) {
            binding.ivPagerPhoto.transitionName = photo.filePath
            binding.ivPagerPhoto.load(File(photo.filePath)) {
                listener(
                    onSuccess = { _, _ -> onImageLoaded(photo.filePath) },
                    onError = { _, _ -> onImageLoaded(photo.filePath) }
                )
            }
        }
    }

    class PhotoDiffCallback : DiffUtil.ItemCallback<ItemPhoto>() {
        override fun areItemsTheSame(oldItem: ItemPhoto, newItem: ItemPhoto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ItemPhoto, newItem: ItemPhoto): Boolean {
            return oldItem == newItem
        }
    }
}
