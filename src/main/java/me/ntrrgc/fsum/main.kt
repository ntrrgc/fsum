package me.ntrrgc.fsum

import io.reactivex.Flowable
import io.reactivex.functions.BiConsumer
import io.reactivex.functions.Consumer
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.processors.UnicastProcessor
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.Callable

data class Warning(
        val path: String,
        val text: String
)

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

private interface CompletedFolderChecksumTask {
    val folder: Folder
    val existingInventory: FolderInventory?
    val filesWithChecksum: List<FileWithChecksum>
}

private data class FolderChecksumTask(
        override val folder: Folder,
        override val existingInventory: FolderInventory?,
        override val filesWithChecksum: MutableList<FileWithChecksum> = mutableListOf()
): CompletedFolderChecksumTask {
    var countPendingFileTasks: Int = -1

    fun setUp(countPendingFileTasks: Int) {
        this.countPendingFileTasks = countPendingFileTasks
    }
}

private data class FileChecksumTask(
        val fileName: String,
        val folderChecksumTask: FolderChecksumTask
)

private sealed class CompletedFileChecksumTask(
        open val fileChecksumTask: FileChecksumTask
) {
    data class Successful(
            override val fileChecksumTask: FileChecksumTask,
            val fileWithChecksum: FileWithChecksum
    ): CompletedFileChecksumTask(fileChecksumTask)

    data class Failed(
            override val fileChecksumTask: FileChecksumTask,
            val cause: Throwable
    ): CompletedFileChecksumTask(fileChecksumTask)
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

fun createChecksums(rootPath: String, skipFilesThatAlreadyHaveChecksums: Boolean, warnings: UnicastProcessor<Warning>) {
    DirectoryLister.scanDirectory(rootPath, warnings)
            .flatMap { (folder, existingMaybeInventory) ->
                val skipFiles = (if (skipFilesThatAlreadyHaveChecksums) existingMaybeInventory?.entries else null)
                        .orEmpty()

                val folderTask = FolderChecksumTask(folder, existingMaybeInventory)
                val fileTasks = folder.files.map { it.name }.toSet()
                        .subtract(skipFiles.map { it.key })
                        .map { FileChecksumTask(it, folderTask) }
                folderTask.setUp(fileTasks.size)
                Flowable.fromIterable(fileTasks)
            }
            .flatMap({ fileChecksumTask ->
                val file = File(fileChecksumTask.folderChecksumTask.folder.path, fileChecksumTask.fileName)
                flowableFromFileChunks(file)
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.computation())
                        .reduce(MessageDigest.getInstance("MD5")) { md, byteChunk ->
                            md.update(byteChunk)
                            md
                        }
                        .map { md -> md.digest() }
                        .map { digest ->
                            CompletedFileChecksumTask.Successful(
                                    fileChecksumTask,
                                    FileWithChecksum(fileChecksumTask.fileName, Checksum(digest))
                            ) as CompletedFileChecksumTask
                        }
                        .onErrorReturn { ex -> CompletedFileChecksumTask.Failed(fileChecksumTask, ex) }
                        .toFlowable()
            }, 10)
            .observeOn(Schedulers.single())
            .flatMap { completedFileChecksumTask ->
                val folderTask = completedFileChecksumTask.fileChecksumTask.folderChecksumTask
                if (completedFileChecksumTask is CompletedFileChecksumTask.Successful) {
                    folderTask.filesWithChecksum.add(completedFileChecksumTask.fileWithChecksum)
                }
                if (--folderTask.countPendingFileTasks == 0) {
                    Flowable.just(folderTask as CompletedFolderChecksumTask)
                } else {
                    Flowable.empty()
                }
            }
            .observeOn(Schedulers.io())
            .doAfterNext { folderTask ->
                val newEntries = folderTask.existingInventory?.entries.orEmpty()
                        .plus(folderTask.filesWithChecksum.map { Pair(it.fileName, it.checksum) })
                val newInventory = FolderInventory(folderTask.folder, newEntries)
                newInventory.save()
            }
            .blockingSubscribe { folderTask ->
                println("${Thread.currentThread().name}: ${folderTask.folder.path}")
                folderTask.filesWithChecksum.forEach {
                    println(" - ${it.checksum.bytes.toHexString()} ${it.fileName}")
                }
            }
}

data class ProgressReport(
        val sizeDone: Long = 0L,
        val sizeTotal: Long?
)

fun main(args: Array<String>) {
    val progress = UnicastProcessor.create<ProgressReport>()
    val warnings = UnicastProcessor.create<Warning>()

    createChecksums("/home/ntrrgc/tmp/test-archive", true, warnings)

    warnings.subscribe { warning ->
        System.err.println("WARNING: ${warning.path}: ${warning.text}")
    }
}
