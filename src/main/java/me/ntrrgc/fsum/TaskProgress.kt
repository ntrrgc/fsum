package me.ntrrgc.fsum

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

interface TaskProgressClient {
    var onProgressChanged: ((taskProgress: TaskProgress) -> Unit)?
}

sealed class TaskProgress: TaskProgressClient {
    override var onProgressChanged: ((taskProgress: TaskProgress) -> Unit)? = null
    protected fun progressChanged() {
        onProgressChanged?.invoke(this)
    }

    interface ScanTreeClient: TaskProgressClient {
        val totalFiles: Int
        val totalBytes: Long

        fun update(cb: ScanTree.() -> Unit)
    }
    class ScanTree private constructor(): TaskProgress(), ScanTreeClient {
        override fun update(cb: ScanTree.() -> Unit) {
            this.cb()
            progressChanged()
        }

        val foundFileCountAtomic = AtomicInteger(0)
        val totalBytesAtomic = AtomicLong(0)

        override val totalFiles: Int
            get() = foundFileCountAtomic.get()

        override val totalBytes: Long
            get() = totalBytesAtomic.get()

        companion object {
            fun create(): ScanTreeClient = ScanTree()
        }
    }

    interface ChecksumClient: TaskProgressClient {
        val doneBytes: Long
        val totalBytes: Long
        val doneFiles: Int
        val totalFiles: Int

        fun update(cb: Checksum.() -> Unit)
    }
    class Checksum private constructor(override val totalBytes: Long, override val totalFiles: Int): TaskProgress(), ChecksumClient {
        override fun update(cb: Checksum.() -> Unit) {
            this.cb()
            progressChanged()
        }

        val doneBytesAtomic = AtomicLong(0)
        val doneFilesAtomic = AtomicInteger(0)

        override val doneBytes: Long
            get() = doneBytesAtomic.get()
        override val doneFiles: Int
            get() = doneFilesAtomic.get()

        companion object {
            fun create(totalBytes: Long, totalFiles: Int): ChecksumClient = Checksum(totalBytes, totalFiles)
        }
    }
}