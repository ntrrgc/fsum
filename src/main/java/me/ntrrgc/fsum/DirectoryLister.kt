package me.ntrrgc.fsum

import io.reactivex.Flowable
import io.reactivex.functions.BiConsumer
import io.reactivex.processors.UnicastProcessor
import me.ntrrgc.fsum.FolderInventory.Companion.inventoryFileName
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Callable

object DirectoryLister {
    private val ignoreFilePatterns = setOf(
            Regex("""^desktop\.ini$"""),
            Regex("""^Thumbs\.db$"""),
            Regex("""^\.DS_Store$"""),
            Regex("""^\.directory$""")
    )

    private val ignoreDirectoryPatterns = setOf(
            Regex("""^@eaDir$"""),
            Regex("""^\.Trash-[0-9]+$"""),
            Regex("""^#recycle$"""),
            Regex("""^__MACOSX$""")
    )

    private fun shouldIgnoreFile(name: String): Boolean {
        return ignoreFilePatterns.any { it.matches(name) }
    }

    private fun shouldIgnoreDirectory(name: String): Boolean {
        return ignoreDirectoryPatterns.any { it.matches(name) }
    }

    private fun listFiles(directory: File): List<File>? {
        return directory.listFiles()
                ?.filter { (it.isDirectory && !shouldIgnoreDirectory(it.name)) || (it.isFile && !shouldIgnoreFile(it.name)) }
    }

    fun scanDirectory(rootPath: String, warnings: UnicastProcessor<Warning>): Flowable<FolderWithOptionalInventory> {
        data class ScanStatus(
                val pendingFolders: Stack<String> = Stack()
        )

        return Flowable.generate<FolderWithOptionalInventory, ScanStatus>(
                Callable {
                    ScanStatus().apply { pendingFolders.push(rootPath) }
                },
                BiConsumer { scanStatus, output ->
                    if (scanStatus.pendingFolders.empty()) {
                        output.onComplete()
                        return@BiConsumer
                    }

                    val path = scanStatus.pendingFolders.pop()
                    val entries = listFiles(File(path))
                    if (entries == null) {
                        warnings.onNext(Warning(
                                path = path,
                                text = "Could not list directory."
                        ))
                        return@BiConsumer
                    }

                    val files = entries.filter { it.isFile }
                            .filter { it.name != inventoryFileName }
                            .map { ArchiveFile(it.name, it.length()) }

                    val subdirectories = entries.filter { it.isDirectory }
                    subdirectories.asReversed().forEach { subdirectory ->
                        scanStatus.pendingFolders.push(subdirectory.path)
                    }

                    val folder = Folder(path, files)
                    val inventory = if (entries.any { it.isFile && it.name == inventoryFileName }) {
                        try {
                            FolderInventory.load(folder)
                        } catch (ex: IOException) {
                            warnings.onNext(Warning(
                                    path = folder.path,
                                    text = "I/O error when reading checksum inventory: ${ex.toString()}"
                            ))
                            return@BiConsumer
                        } catch (ex: Exception) {
                            warnings.onNext(Warning(
                                    path = folder.path,
                                    text = "Error while parsing inventory: ${ex.toString()}"
                            ))
                            return@BiConsumer
                        }
                    } else null

                    output.onNext(FolderWithOptionalInventory(folder, inventory))
                }
        )
    }
}