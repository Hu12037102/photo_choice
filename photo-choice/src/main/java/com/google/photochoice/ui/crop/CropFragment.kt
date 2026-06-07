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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.photochoice.R
import com.google.photochoice.config.CropAspectRatio
import com.google.photochoice.databinding.FragmentCropBinding
import com.google.photochoice.util.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.lifecycle.lifecycleScope

class CropFragment : Fragment() {

    private var _binding: FragmentCropBinding? = null
    private val binding get() = _binding!!
    private var sourceUri: String? = null
    private var currentRatio = CropAspectRatio.ORIGINAL

    companion object {
        const val REQUEST_KEY = "photochoice_crop_result"
        const val EXTRA_CROPPED_URI = "cropped_uri"
        private const val CROP_QUALITY = 95
        private const val ARG_URI = "uri"
        private const val ARG_INITIAL_RATIO = "initial_ratio"

        fun newInstance(uri: String, initialRatio: CropAspectRatio): CropFragment {
            return CropFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, uri)
                    putString(ARG_INITIAL_RATIO, initialRatio.name)
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

        // 顶栏/底栏各自叠加系统栏 inset，与 PreviewActivity 一致策略
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { root, insets ->
            val statusBarInset = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            binding.toolbar.updatePadding(top = statusBarInset)
            val navBarInset = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars()).bottom
            binding.bottomBar.updatePadding(bottom = navBarInset + requireContext().dp(10))
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        sourceUri?.let { uri ->
            Glide.with(this)
                .load(uri)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(binding.cropView)
        }

        binding.btnCancel.setOnClickListener { requireActivity().finish() }
        binding.btnConfirm.setOnClickListener { performCrop() }

        binding.btnRatioFree.setOnClickListener { selectRatio(CropAspectRatio.ORIGINAL) }
        binding.btnRatio11.setOnClickListener { selectRatio(CropAspectRatio.SQUARE) }
        binding.btnRatio34.setOnClickListener { selectRatio(CropAspectRatio.RATIO_3_4) }
        binding.btnRatio916.setOnClickListener { selectRatio(CropAspectRatio.RATIO_9_16) }

        val initialRatioName = arguments?.getString(ARG_INITIAL_RATIO)
        val initialRatio = if (initialRatioName != null) {
            runCatching { CropAspectRatio.valueOf(initialRatioName) }
                .getOrDefault(CropAspectRatio.ORIGINAL)
        } else {
            CropAspectRatio.ORIGINAL
        }
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
