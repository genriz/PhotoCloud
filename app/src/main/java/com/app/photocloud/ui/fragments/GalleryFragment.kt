package com.app.photocloud.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.SharedElementCallback
import androidx.core.view.doOnPreDraw
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.FragmentNavigatorExtras
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.app.photocloud.R
import com.app.photocloud.databinding.FragmentGalleryBinding
import com.app.photocloud.ui.adapters.GalleryAdapter
import com.app.photocloud.ui.viewmodels.MainViewModel

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var adapter: GalleryAdapter
    private var lastDetailedPhotoPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setExitSharedElementCallback(object : SharedElementCallback() {
            override fun onMapSharedElements(
                names: MutableList<String>,
                sharedElements: MutableMap<String, View>
            ) {
                val path = lastDetailedPhotoPath ?: return
                val index = viewModel.allPhotos.value?.indexOfFirst { it.filePath == path } ?: -1
                if (index != -1) {
                    val view = binding.rvGallery.layoutManager?.findViewByPosition(index)
                    val imageView = view?.findViewById<View>(R.id.iv_photo)
                    if (imageView != null) {
                        val name = names.getOrNull(0) ?: path
                        sharedElements[name] = imageView
                    }
                }
            }
        })
        
        parentFragmentManager.setFragmentResultListener("photo_details_result", this) { _, bundle ->
            lastDetailedPhotoPath = bundle.getString("returnedPath")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

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

        setupRecyclerView()
        
        viewModel.allPhotos.observe(viewLifecycleOwner) { photos ->
            if (photos.isEmpty()) {
                binding.tvEmptyGallery.visibility = View.VISIBLE
                binding.rvGallery.visibility = View.GONE
                startPostponedEnterTransition()
            } else {
                binding.tvEmptyGallery.visibility = View.GONE
                binding.rvGallery.visibility = View.VISIBLE
                adapter.updateData(photos)
                
                val path = lastDetailedPhotoPath
                if (path != null) {
                    val index = photos.indexOfFirst { it.filePath == path }
                    if (index != -1) {
                        (binding.rvGallery.layoutManager as? GridLayoutManager)?.scrollToPositionWithOffset(index, 0)
                        binding.rvGallery.post {
                            if (isAdded) startPostponedEnterTransition()
                        }
                    } else {
                        startPostponedEnterTransition()
                    }
                } else {
                    binding.rvGallery.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                }
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = GalleryAdapter(emptyList()) { photo, sharedView ->
            lastDetailedPhotoPath = null
            val extras = FragmentNavigatorExtras(sharedView to photo.filePath)
            val bundle = Bundle().apply {
                putString("photoPath", photo.filePath)
            }
            findNavController().navigate(
                R.id.action_galleryFragment_to_photoDetailsFragment,
                bundle,
                null,
                extras
            )
        }
        binding.rvGallery.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvGallery.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
