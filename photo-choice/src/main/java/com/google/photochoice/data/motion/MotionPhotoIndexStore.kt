package com.google.photochoice.data.motion

import android.content.Context
import android.util.Log
import com.google.photochoice.data.model.MediaFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** L2 查询结果：确认是 / 确认否 / 未知（未记录或校验失效）。 */
enum class IndexResult { MOTION, NOT_MOTION, UNKNOWN }

/**
 * 索引记录。size + dateAdded 用于 id 复用校验：
 * MediaStore._ID 在媒体删除后可能被新文件复用，比对不一致则视为失效。
 */
data class IndexRecord(
    val id: Long,
    val isMotion: Boolean,
    val size: Long,
    val dateAdded: Long
)

/** 单行 CSV 编解码。纯函数，便于单测。 */
object IndexCodec {

    /** 编码为 `id,flag,size,dateAdded` 一行。 */
    fun encode(record: IndexRecord): String {
        val flag = if (record.isMotion) 1 else 0
        return "${record.id},$flag,${record.size},${record.dateAdded}"
    }

    /** 解码一行；格式非法返回 null（坏行跳过，不影响整体加载）。 */
    fun decode(line: String): IndexRecord? {
        val parts = line.split(",")
        if (parts.size != 4) return null
        val id = parts[0].toLongOrNull() ?: return null
        val flag = parts[1].toIntOrNull() ?: return null
        val size = parts[2].toLongOrNull() ?: return null
        val dateAdded = parts[3].toLongOrNull() ?: return null
        return IndexRecord(id, flag == 1, size, dateAdded)
    }
}

/**
 * 实况图判定结果的持久化索引（L2）。
 *
 * - 存储：App 私有 filesDir/photochoice/motion_index.csv（append-only + 阈值 compact）
 * - 加载：冷启动异步读全量构建内存 Map（O(1) 查询）
 * - 落盘：内存先写(查询立即可见) + 单写协程 + 批量防抖(攒够/超时/onStop 时 flush)
 * - 防 id 复用：size + dateAdded 校验
 * - 容错：所有 I/O runCatching 包裹，任何异常降级为"未命中"，绝不 Crash
 *
 * 为什么自建文件而非 Room：本模块是 library，Room 会带来编译期依赖与宿主版本冲突；
 * 本场景数据结构极简，单表 append 文本足矣。
 */
object MotionPhotoIndexStore {

    private const val TAG = "MotionPhotoIndexStore"
    private const val DIR_NAME = "photochoice"
    private const val FILE_NAME = "motion_index.csv"

    /** 批量落盘阈值：攒够这么多条即 flush 一次。 */
    private const val FLUSH_BATCH = 64
    /** 触发 compact 的总写入行数阈值（含重复行）。 */
    private const val COMPACT_LINE_THRESHOLD = 20000

    private val map = ConcurrentHashMap<Long, IndexRecord>()
    // 语义为"已发起加载"（异步读盘可能尚未完成），非"已加载完成"
    @Volatile private var loadStarted = false
    private val loadComplete = CompletableDeferred<Unit>()
    @Volatile private var indexFile: File? = null

    /** 累计写入文件的行数（含重复），用于判断 compact 时机。 */
    private val writtenLines = AtomicInteger(0)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // 待落盘队列：null 为 flush 信号（onStop 主动触发）
    private val writeChannel = Channel<IndexRecord?>(Channel.UNLIMITED)
    private val pendingBuffer = ArrayList<IndexRecord>(FLUSH_BATCH)

    init {
        scope.launch {
            for (item in writeChannel) {
                if (item == null) {
                    flushBuffer()
                } else {
                    pendingBuffer.add(item)
                    if (pendingBuffer.size >= FLUSH_BATCH) flushBuffer()
                }
            }
        }
    }

    /** 纯逻辑：把多行合并为内存 Map（后写覆盖先写，坏行跳过）。供单测。 */
    fun mergeLines(lines: List<String>): Map<Long, IndexRecord> {
        val result = HashMap<Long, IndexRecord>()
        for (line in lines) {
            val record = IndexCodec.decode(line) ?: continue
            result[record.id] = record
        }
        return result
    }

    /** 冷启动异步载入。幂等：仅首次真正读盘。 */
    fun ensureLoaded(context: Context) {
        if (loadStarted) return
        synchronized(this) {
            if (loadStarted) return
            loadStarted = true
        }
        scope.launch {
            runCatching {
                val file = resolveFile(context)
                indexFile = file
                if (file.exists()) {
                    val lines = file.readLines()
                    writtenLines.set(lines.size)
                    // 磁盘记录只填空：map 中已存在的必是本会话 put 的新值，不能被旧盘覆盖
                    for ((id, record) in mergeLines(lines)) {
                        map.putIfAbsent(id, record)
                    }
                    Log.d(TAG, "index loaded: ${map.size} entries from ${lines.size} lines")
                }
            }.onFailure { Log.w(TAG, "ensureLoaded failed", it) }
            loadComplete.complete(Unit)
        }
    }

    /** 挂起直到首次加载完成（供预建在过滤前等待，避免整册重嗅）。 */
    suspend fun awaitLoaded(context: Context) {
        ensureLoaded(context)
        loadComplete.await()
    }

    /** O(1) 查询，含 id 复用校验。 */
    fun query(item: MediaFile): IndexResult {
        val record = map[item.id] ?: return IndexResult.UNKNOWN
        if (record.size != item.size || record.dateAdded != item.dateAdded) {
            // id 被复用为不同文件，旧记录失效
            return IndexResult.UNKNOWN
        }
        return if (record.isMotion) IndexResult.MOTION else IndexResult.NOT_MOTION
    }

    /** 写入一条：内存立即生效 + 入队异步落盘。 */
    fun put(item: MediaFile, isMotion: Boolean) {
        val record = IndexRecord(item.id, isMotion, item.size, item.dateAdded)
        map[item.id] = record
        writeChannel.trySend(record)
    }

    /** 生命周期 onStop 时主动 flush，避免丢失未落盘的判定。 */
    fun requestFlush() {
        writeChannel.trySend(null)
    }

    private fun flushBuffer() {
        if (pendingBuffer.isEmpty()) return
        val file = indexFile
        if (file == null) {
            // 加载窗口内文件句柄未就绪：不清空缓冲，等下一次 flush 信号重试，避免静默丢盘
            Log.w(TAG, "flush deferred: index file not ready, ${pendingBuffer.size} pending")
            return
        }
        runCatching {
            file.parentFile?.mkdirs()
            val text = buildString {
                for (r in pendingBuffer) {
                    append(IndexCodec.encode(r)); append('\n')
                }
            }
            file.appendText(text)
            val total = writtenLines.addAndGet(pendingBuffer.size)
            if (total > COMPACT_LINE_THRESHOLD) compact(file)
        }.onFailure { Log.w(TAG, "flush failed", it) }
        // 落盘失败即放弃该批（内存 map 仍有效），避免磁盘满时重试风暴
        pendingBuffer.clear()
    }

    /** 用内存 Map 全量重写文件，消除重复行，控制体积。 */
    private fun compact(file: File) {
        runCatching {
            val snapshot = map.values.toList()
            val tmp = File(file.parentFile, "$FILE_NAME.tmp")
            // 先写 tmp 并 fsync，再原子 rename，避免掉电把整个索引截断归零
            FileOutputStream(tmp).use { out ->
                out.write(buildString {
                    for (r in snapshot) { append(IndexCodec.encode(r)); append('\n') }
                }.toByteArray())
                out.fd.sync()
            }
            if (tmp.renameTo(file)) {
                writtenLines.set(snapshot.size)
                Log.d(TAG, "compacted to ${snapshot.size} entries")
            } else {
                // rename 失败：补日志并清理 tmp，writtenLines 不重置以便下次 flush 重试
                Log.w(TAG, "compact rename failed")
                tmp.delete()
            }
        }.onFailure { Log.w(TAG, "compact failed", it) }
    }

    private fun resolveFile(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        return File(dir, FILE_NAME)
    }
}
