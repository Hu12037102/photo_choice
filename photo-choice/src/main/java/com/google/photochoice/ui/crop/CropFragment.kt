package com.google.photochoice.ui.crop

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.R
import com.google.photochoice.config.CropAspectRatio
import com.google.photochoice.databinding.FragmentCropBinding
import com.google.photochoice.viewmodel.PhotoChoiceViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope

class CropFragment : Fragment() {

    private var _binding: FragmentCropBinding? = null
    private val binding get() = _binding!!
    private val viewModel: PhotoChoiceViewModel by viewModels(
        ownerProducer = { requireActivity() }
    )
    private var sourceUri: String? = null
    private var currentRatio = CropAspectRatio.ORIGINAL

    companion object {
        const val REQUEST_KEY = "photochoice_crop_result"
        const val EXTRA_CROPPED_URI = "cropped_uri"
        private const val CROP_QUALITY = 95
        private const val ARG_URI = "uri"

        fun newInstance(uri: String): CropFragment {
            return CropFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, uri)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sourceUri = arguments?.getString(ARG_URI)

        sourceUri?.let { uri ->
            Glide.with(this)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(binding.cropView)
        }

        binding.btnCancel.setOnClickListener { viewModel.dismissCrop() }
        binding.btnConfirm.setOnClickListener { performCrop() }

        binding.btnRatioFree.setOnClickListener { selectRatio(CropAspectRatio.ORIGINAL) }
        binding.btnRatio11.setOnClickListener { selectRatio(CropAspectRatio.SQUARE) }
        binding.btnRatio34.setOnClickListener { selectRatio(CropAspectRatio.RATIO_3_4) }
        binding.btnRatio916.setOnClickListener { selectRatio(CropAspectRatio.RATIO_9_16) }

        // 应用配置中的初始比例
        val initialRatio = viewModel.config.cropConfig.aspectRatio
        selectRatio(initialRatio)
    }

    private fun selectRatio(ratio: CropAspectRatio) {
        currentRatio = ratio
        binding.cropView.aspectRatio = ratio
        updateRatioButtons()
    }

    private fun updateRatioButtons() {
        applyButtonState(binding.btnRatioFree, currentRatio == CropAspectRatio.ORIGINAL)
        applyButtonState(binding.btnRatio11, currentRatio == CropAspectRatio.SQUARE)
        applyButtonState(binding.btnRatio34, currentRatio == CropAspectRatio.RATIO_3_4)
        applyButtonState(binding.btnRatio916, currentRatio == CropAspectRatio.RATIO_9_16)
    }

    private fun applyButtonState(button: AppCompatTextView, selected: Boolean) {
        val ctx = button.context
        button.setTextColor(
            ContextCompat.getColor(
                ctx,
                if (selected) R.color.photochoice_preview_text else R.color.photochoice_icon_secondary
            )
        )
        button.setBackgroundResource(
            if (selected) R.drawable.bg_ratio_selected else android.R.color.transparent
        )
    }

    private fun performCrop() {
        val view = binding.cropView
        viewLifecycleOwner.lifecycleScope.launch {
            val outputFile = withContext(Dispatchers.IO) {
                val cropped: Bitmap = view.crop() ?: return@withContext null
                try {
                    val outputDir = File(requireContext().cacheDir, "photo_choice").also { it.mkdirs() }
                    val file = File(outputDir, "crop_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { fos ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, CROP_QUALITY, fos)
                    }
                    file
                } finally {
                    cropped.recycle()
                }
            }
            if (outputFile == null) {
                Toast.makeText(
                    requireContext(), getString(R.string.photochoice_crop_failed), Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val uri = Uri.fromFile(outputFile).toString()
            parentFragmentManager.setFragmentResult(
                REQUEST_KEY,
                Bundle().apply { putString(EXTRA_CROPPED_URI, uri) }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
