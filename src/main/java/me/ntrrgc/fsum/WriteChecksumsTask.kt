package me.ntrrgc.fsum

import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.io.File
import java.security.MessageDigest

class WriteChecksumsTask(val rootPath: String, val overwriteExistingChecksums: Boolean): FSumTask() {
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
    ) {
        val fullPath: String get() = "${folderChecksumTask.folder.path}${File.separator}$fileName"
    }

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

    override fun start(): Single<FSumTask.FinishMessage> {
        val scanTreeProgress = TaskProgress.ScanTree.create()
        lateinit var checksumProgress: TaskProgress.ChecksumClient

        this.progress = scanTreeProgress
        return DirectoryLister.scanDirectory(rootPath, incidentLogger, scanTreeProgress)
                .toList()
                .doOnSuccess {
                    checksumProgress = TaskProgress.Checksum.create(
                            totalBytes = scanTreeProgress.totalBytes,
                            totalFiles = scanTreeProgress.totalFiles
                    )
                    this.progress = checksumProgress
                }
                .flattenAsFlowable { it }
                .flatMap { (folder, existingMaybeInventory) ->
                    val skipFiles = (if (!overwriteExistingChecksums) existingMaybeInventory?.entries else null)
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
                                val sizeAdded = byteChunk.remaining()
                                md.update(byteChunk)
                                checksumProgress.update {
                                    doneBytesAtomic.addAndGet(sizeAdded.toLong())
                                }
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
                    checksumProgress.update { doneFilesAtomic.incrementAndGet() }
                    if (--folderTask.countPendingFileTasks == 0) {
                        Flowable.just(folderTask as CompletedFolderChecksumTask)
                    } else {
                        Flowable.empty()
                    }
                }
                .observeOn(Schedulers.io())
                .doOnNext { folderTask ->
                    val newEntries = folderTask.existingInventory?.entries.orEmpty()
                            .plus(folderTask.filesWithChecksum.map { Pair(it.fileName, it.checksum) })
                    val newInventory = FolderInventory(folderTask.folder, newEntries)
                    newInventory.save()
                }
                .reduce(1) { _, _ -> 1}
                .map { FSumTask.FinishMessage(
                        message = "Finished writing checksums."
                ) }
    }
}
