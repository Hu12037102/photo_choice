package com.google.photochoice.data.motion

/**
 * bind 时角标状态。
 * - CONFIRMED_*：确定态，同帧显示/隐藏，无动画
 * - HEURISTIC_MOTION：启发式疑似，立即显示但待 L4 校正
 * - UNKNOWN：待异步嗅探，先隐藏并触发嗅探
 */
enum class BadgeState {
    CONFIRMED_MOTION,
    CONFIRMED_NOT,
    HEURISTIC_MOTION,
    UNKNOWN
}

/**
 * 五级判定级联（纯函数）：L0 系统标记 > L1 内存 > L2 索引 > L3 启发式 > L4 未知。
 * 命中即返回，成本从低到高。调用方(Adapter)从各单例取值后传入。
 */
object MotionPhotoDecision {

    /**
     * 依据五级级联判定角标状态（纯函数，无副作用）。
     * 优先级：L0 系统标记 > L1 内存缓存 > L2 持久化索引 > L3 文件名启发式 > L4 未知。
     * 任一级命中即返回，成本从低到高逐级下探。
     *
     * @param isMotionFlag  L0：MediaFile.isMotionPhoto，系统直接标记，最高优先级
     * @param memoryResult  L1：LruCache 命中结果（null=未缓存，true=动图，false=非动图）
     * @param indexResult   L2：持久化索引结果（MOTION/NOT_MOTION/UNKNOWN）
     * @param heuristicGuess L3：文件名启发式猜测（true=疑似动图）
     * @return 角标状态；L1 的 false 结果（CONFIRMED_NOT）优先级高于 L3 启发式
     */
    fun resolve(
        isMotionFlag: Boolean,        // L0: MediaFile.isMotionPhoto
        memoryResult: Boolean?,       // L1: LruCache（null=未缓存）
        indexResult: IndexResult,     // L2: 持久化索引
        heuristicGuess: Boolean       // L3: 文件名启发式
    ): BadgeState {
        // L0：系统标记为真，直接确认为动图
        if (isMotionFlag) return BadgeState.CONFIRMED_MOTION
        // L1：内存缓存命中（含 false），确定态优先于启发式
        if (memoryResult != null) {
            return if (memoryResult) BadgeState.CONFIRMED_MOTION else BadgeState.CONFIRMED_NOT
        }
        // L2：持久化索引命中（含 NOT_MOTION），UNKNOWN 继续下探
        when (indexResult) {
            IndexResult.MOTION -> return BadgeState.CONFIRMED_MOTION
            IndexResult.NOT_MOTION -> return BadgeState.CONFIRMED_NOT
            IndexResult.UNKNOWN -> Unit
        }
        // L3：启发式命中，返回疑似态（待后续异步校正）
        if (heuristicGuess) return BadgeState.HEURISTIC_MOTION
        // L4：全部未知且启发式不命中，返回未知态（触发异步嗅探）
        return BadgeState.UNKNOWN
    }
}
