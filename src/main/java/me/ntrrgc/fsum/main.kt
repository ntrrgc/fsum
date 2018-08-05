package me.ntrrgc.fsum

import io.reactivex.Flowable
import io.reactivex.functions.BiConsumer
import io.reactivex.functions.Consumer
import io.reactivex.plugins.RxJavaPlugins
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Callable

data class ArchiveFile(
        val name: String,
        val size: Long
)

data class Folder(
        val path: String,
        val files: List<ArchiveFile>
)

data class FolderWithOptionalInventory(
        val folder: Folder,
        val inventory: FolderInventory?
)

data class FileWithChecksum(
        val fileName: String,
        val checksum: Checksum
)

/** A MD5 checksum. */
data class Checksum(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Checksum

        if (!Arrays.equals(bytes, other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(bytes)
    }

    override fun toString(): String {
        return bytes.toHexString()
    }
}

fun flowableFromFileChunks(file: File): Flowable<ByteBuffer> {
    return Flowable.generate<ByteBuffer, FileInputStream>(
            Callable { file.inputStream() },
            BiConsumer { status, output ->
                try {
                    val bytes = ByteArray(8096)
                    val numBytesRead = status.read(bytes)
                    if (numBytesRead >= 0) {
                        output.onNext(ByteBuffer.wrap(bytes, 0, numBytesRead))
                    } else {
                        output.onComplete()
                    }
                } catch (ex: IOException) {
                    output.onError(ex)
                }
            },
            Consumer { inputStream ->
                try {
                    inputStream.close()
                } catch (ex: IOException) {
                    RxJavaPlugins.onError(ex)
                }
            }
    )
}
data class ProgressReport(
        val sizeDone: Long = 0L,
        val sizeTotal: Long?
)

fun main(args: Array<String>) {
    val task = WriteChecksumsTask(rootPath = args[0], overwriteExistingChecksums = true)
    ConsoleUI(task).run()
}
