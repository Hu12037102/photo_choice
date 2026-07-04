package com.google.photochoice.data.motion

import com.google.photochoice.data.model.MediaFile

/**
 * 文件名启发式：bind 时零 I/O，仅凭 displayName 特征快速猜"疑似实况图"。
 *
 * 设计原则：保守优先（宁漏不错）——命中即立即显示角标，最终以 L4 XMP 嗅探结果校正。
 * 误报会导致角标淡出回撤，故规则只覆盖强特征命名，降低回撤闪烁概率。
 */
object MotionPhotoHeuristics {

    /**
     * 判断是否"疑似实况图"。
     * @return true=疑似实况图（可立即显示角标，待异步校正）
     */
    fun guess(item: MediaFile): Boolean {
        if (item.type != MediaFile.MediaType.IMAGE) return false
        val name = item.displayName.uppercase()
        if (name.isEmpty()) return false
        return name.startsWith("MVIMG_") ||      // Google 相机 Motion Photo
            name.startsWith("MV_") ||             // 部分机型 Motion 前缀
            name.contains("MOTIONPHOTO") ||       // 通用命名
            name.contains("LIVEPHOTO")            // 部分相机 Live 命名
    }
}
