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
import com.google.photochoice.util.CanvasSafeDownsampleStrategy
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
    /** 裁剪输出尺寸上限（px）；0 = 不限制。透传自 [com.google.photochoice.config.CropConfig]。 */
    private var maxOutputWidth = 0
    private var maxOutputHeight = 0

    companion object {
        const val REQUEST_KEY = "photochoice_crop_result"
        const val EXTRA_CROPPED_URI = "cropped_uri"
        private const val CROP_QUALITY = 95
        /** 裁剪产物落盘子目录（与 SandboxCleaner 清理目录一致）。 */
        private const val CROP_CACHE_DIR = "photo_choice"
        private const val ARG_URI = "uri"
        private const val ARG_INITIAL_RATIO = "initial_ratio"
        private const val ARG_MAX_WIDTH = "max_width"
        private const val ARG_MAX_HEIGHT = "max_height"

        fun newInstance(
            uri: String,
            initialRatio: CropAspectRatio,
            maxWidth: Int = 0,
            maxHeight: Int = 0
        ): CropFragment {
            return CropFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_URI, uri)
                    putString(ARG_INITIAL_RATIO, initialRatio.name)
                    putInt(ARG_MAX_WIDTH, maxWidth)
                    putInt(ARG_MAX_HEIGHT, maxHeight)
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
        maxOutputWidth = arguments?.getInt(ARG_MAX_WIDTH) ?: 0
        maxOutputHeight = arguments?.getInt(ARG_MAX_HEIGHT) ?: 0

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
            // asBitmap：裁剪必须拿到 BitmapDrawable（CropView.crop() 依赖 drawable as BitmapDrawable）；
            // 动图若被解成 AnimatedImageDrawable 会导致裁剪返回 null。取静态首帧即可。
            // CanvasSafeDownsampleStrategy：防全景图/长截图全量解码超 Canvas 100MB 绘制上限（页面空白）
            // DiskCacheStrategy.NONE：本地媒体源文件即"缓存"，仅内存缓存，未命中从源文件解码
            Glide.with(this)
                .asBitmap()
                .load(uri)
                .downsample(CanvasSafeDownsampleStrategy)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
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
            if (selected) R.drawable.ripple_crop_ratio_selected
            else R.drawable.ripple_crop_ratio_item
        )
    }

    private fun performCrop() {
        // View / Matrix 非线程安全：裁剪(读 drawable 与 imageMatrix)必须在主线程完成，
        // 只把 Bitmap 编码/落盘这类纯 IO 丢到后台，杜绝与主线程动画写 Matrix 的数据竞争。
        val cropped: Bitmap? = binding.cropView.crop(maxOutputWidth, maxOutputHeight)
        if (cropped == null) {
            Toast.makeText(
                requireContext(), getString(R.string.photochoice_crop_failed), Toast.LENGTH_SHORT
            ).show()
            return
        }
        // 主线程提前捕获 cacheDir，避免 IO 线程再触碰 Fragment 上下文。
        val cacheDir = requireContext().cacheDir
        viewLifecycleOwner.lifecycleScope.launch {
            val outputFile = withContext(Dispatchers.IO) {
                try {
                    val outputDir = File(cacheDir, CROP_CACHE_DIR).also { it.mkdirs() }
                    val file = File(outputDir, "crop_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { fos ->
                        cropped.compress(Bitmap.CompressFormat.JPEG, CROP_QUALITY, fos)
                    }
                    file
                } catch (e: Exception) {
                    // 落盘失败(磁盘满 / IO 异常)不外抛，回退裁剪失败提示，避免 Crash
                    null
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
