package com.app.photocloud.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.app.photocloud.R
import com.app.photocloud.data.model.ItemPhoto
import com.app.photocloud.databinding.ItemGalleryPhotoBinding
import java.io.File

class GalleryAdapter(
    private var photos: List<ItemPhoto>,
    private val onPhotoClick: (ItemPhoto, View) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {

    fun updateData(newPhotos: List<ItemPhoto>) {
        android.util.Log.d("GalleryAdapter", "Updating data with ${newPhotos.size} photos")
        photos = newPhotos
        notifyDataSetChanged()
    }

    inner class GalleryViewHolder(private val binding: ItemGalleryPhotoBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(photo: ItemPhoto) {
            val file = File(photo.filePath)
            binding.ivPhoto.transitionName = photo.filePath
            binding.ivPhoto.load(file)
            binding.tvPhotoDate.text = photo.captureDate
            
            val (statusRes, colorRes) = when (photo.uploadStatus) {
                ItemPhoto.STATUS_SAVED_LOCALLY -> R.string.status_saved_locally to R.color.primary
                ItemPhoto.STATUS_UPLOADING -> R.string.status_uploading to R.color.secondary
                ItemPhoto.STATUS_UPLOADED -> R.string.status_uploaded to android.R.color.holo_green_dark
                ItemPhoto.STATUS_UPLOAD_ERROR -> R.string.status_upload_error to R.color.error
                else -> R.string.status_unknown to R.color.outline
            }
            
            binding.tvUploadStatus.text = binding.root.context.getString(statusRes)
            binding.tvUploadStatus.setTextColor(ContextCompat.getColor(binding.root.context, colorRes))
            
            binding.root.setOnClickListener { onPhotoClick(photo, binding.ivPhoto) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val binding = ItemGalleryPhotoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GalleryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size
}
