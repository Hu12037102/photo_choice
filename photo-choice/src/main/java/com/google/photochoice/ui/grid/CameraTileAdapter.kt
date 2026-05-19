package com.google.photochoice.ui.grid

import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.google.photochoice.R

/**
 * 网格首格相机入口。与 [MediaGridAdapter] 通过 [androidx.recyclerview.widget.ConcatAdapter] 拼接，
 * 避免在 PagingDataAdapter 上重写 itemCount 导致分页数据无法展示。
 */
class CameraTileAdapter(
    private val onCameraClick: () -> Unit
) : RecyclerView.Adapter<CameraTileAdapter.CameraVH>() {

    override fun getItemCount(): Int = 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CameraVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_camera, parent, false)
        return CameraVH(view)
    }

    override fun onBindViewHolder(holder: CameraVH, position: Int) {
        holder.bind(onCameraClick)
    }

    class CameraVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cameraAnim: AppCompatImageView = itemView.findViewById(R.id.ivCameraAnim)

        fun bind(onClick: () -> Unit) {
            itemView.setOnClickListener { onClick() }
            (cameraAnim.drawable as? Animatable)?.start()
        }
    }
}
