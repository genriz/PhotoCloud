package com.app.photocloud.ui.fragments

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.transition.TransitionInflater
import coil.load
import com.app.photocloud.R
import com.app.photocloud.data.model.ItemPhoto
import com.app.photocloud.databinding.FragmentPhotoDetailsBinding
import com.app.photocloud.ui.viewmodels.MainViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class PhotoDetailsFragment : Fragment() {

    private var _binding: FragmentPhotoDetailsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private var photoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedElementEnterTransition = TransitionInflater.from(requireContext())
            .inflateTransition(android.R.transition.move)
            ?.setDuration(220)
    }

    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            finalizeDeletion()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPhotoDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val photoPath = arguments?.getString("photoPath") ?: return
        photoFile = File(photoPath)
        
        binding.ivDetailPhoto.transitionName = photoPath
        postponeEnterTransition()

        photoFile?.let { file ->
            if (file.exists()) {
                binding.ivDetailPhoto.load(file) {
                    listener(
                        onSuccess = { _, _ ->
                            binding.ivDetailPhoto.doOnPreDraw {
                                startPostponedEnterTransition()
                            }
                        },
                        onError = { _, _ ->
                            startPostponedEnterTransition()
                        }
                    )
                }
                displayMetadata(file)
            } else {
                startPostponedEnterTransition()
            }
        }

        viewModel.getPhotoByPath(photoPath).observe(viewLifecycleOwner) { photo ->
            photo?.let {
                val (statusRes, colorRes) = when (it.uploadStatus) {
                    ItemPhoto.STATUS_SAVED_LOCALLY -> R.string.status_saved_locally to R.color.primary
                    ItemPhoto.STATUS_UPLOADING -> R.string.status_uploading to R.color.secondary
                    ItemPhoto.STATUS_UPLOADED -> R.string.status_uploaded to android.R.color.holo_green_dark
                    ItemPhoto.STATUS_UPLOAD_ERROR -> R.string.status_upload_error to R.color.error
                    else -> R.string.status_unknown to R.color.outline
                }
                binding.tvDetailStatus.text = getString(R.string.format_status, getString(statusRes))
                binding.tvDetailStatus.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

                val isUploading = it.uploadStatus == ItemPhoto.STATUS_UPLOADING
                binding.btnUploadDrive.isEnabled = !isUploading
                binding.btnUploadYandex.isEnabled = !isUploading
                binding.progressUpload.visibility = if (isUploading) View.VISIBLE else View.GONE
            }
        }

        binding.btnBack.setOnClickListener {
            binding.btnBack.animate()
                .scaleX(1.3f)
                .scaleY(1.3f)
                .setDuration(100)
                .withEndAction {
                    binding.btnBack.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .withEndAction {
                            if (isAdded) findNavController().popBackStack()
                        }
                }
        }

        binding.btnUploadDrive.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("google_prefs", Context.MODE_PRIVATE)
            val email = prefs.getString("google_email", null)
            if (email != null) {
                photoFile?.let { uploadToDrive(it, email) }
            } else {
                Toast.makeText(requireContext(), R.string.msg_select_account, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnUploadYandex.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("yandex_prefs", Context.MODE_PRIVATE)
            val token = prefs.getString("yandex_token", null)
            if (token != null) {
                photoFile?.let { uploadToYandexDisk(it, token) }
            } else {
                Toast.makeText(requireContext(),
                    getString(R.string.please_sign_in_to_yandex_on_dashboard), Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnDeletePhoto.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun showDeleteConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(R.string.delete_confirm_msg)
            .setPositiveButton(R.string.btn_delete_photo) { _, _ ->
                performDeletion()
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun performDeletion() {
        val file = photoFile ?: return
        lifecycleScope.launch {
            val deletedFromMediaStore = withContext(Dispatchers.IO) {
                deleteFromMediaStore(file.name)
            }

            if (deletedFromMediaStore) {
                finalizeDeletion()
            }
        }
    }

    private fun deleteFromMediaStore(fileName: String): Boolean {
        val resolver = requireContext().contentResolver
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)

        val cursor = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), selection, selectionArgs, null)
        val id = cursor?.use {
            if (it.moveToFirst()) it.getLong(it.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)) else null
        }

        if (id != null) {
            val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
            return try {
                resolver.delete(contentUri, null, null) > 0
            } catch (securityException: SecurityException) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && securityException is RecoverableSecurityException) {
                    val intentSender = securityException.userAction.actionIntent.intentSender
                    deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                    false
                } else {
                    throw securityException
                }
            }
        }
        return true
    }

    private fun finalizeDeletion() {
        val file = photoFile ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (file.exists()) {
                    file.delete()
                }
            }
            viewModel.deletePhoto(file.absolutePath)
            Toast.makeText(requireContext(), R.string.delete_success, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    private fun uploadToDrive(file: File, accountEmail: String) {
        viewModel.uploadPhoto(file, accountEmail)
        Toast.makeText(requireContext(), R.string.msg_upload_started, Toast.LENGTH_SHORT).show()
    }

    private fun uploadToYandexDisk(file: File, token: String) {
        viewModel.uploadPhotoToYandex(file, token)
        Toast.makeText(requireContext(), R.string.msg_upload_started, Toast.LENGTH_SHORT).show()
    }

    private fun displayMetadata(file: File) {
        val sizeInKb = file.length() / 1024.0
        val df = DecimalFormat("#.##")
        binding.tvDetailSize.text = getString(R.string.format_size, df.format(sizeInKb))

        try {
            val exif = ExifInterface(file.absolutePath)
            val width = exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)
            val height = exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)
            binding.tvDetailResolution.text = getString(R.string.format_resolution, "${width}x${height}")

            val exifDate = exif.getAttribute(ExifInterface.TAG_DATETIME)
            val displayDate = if (exifDate != null) {
                try {
                    val parser = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                    val formatter = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US)
                    parser.parse(exifDate)?.let { formatter.format(it) } ?: exifDate
                } catch (e: Exception) {
                    Log.v("DASD", e.localizedMessage?:"exception null")
                    exifDate
                }
            } else {
                "Unknown"
            }
            binding.tvDetailDatetime.text = getString(R.string.format_datetime, displayDate)

            val latLong = exif.latLong
            if (latLong != null) {
                binding.tvDetailCoordinates.text = getString(R.string.format_coordinates, "${latLong[0]}, ${latLong[1]}")
            } else {
                binding.tvDetailCoordinates.text = getString(R.string.format_coordinates, "Not available")
            }
        } catch (e: Exception) {
            Log.v("DASD", e.localizedMessage?:"exception null")
            binding.tvDetailResolution.text = "Metadata error"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
