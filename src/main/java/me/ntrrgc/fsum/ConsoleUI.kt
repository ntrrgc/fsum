package me.ntrrgc.fsum

class ConsoleUI(val task: FSumTask) {
    private val multiplexer = ConsoleProgressMultiplexer(System.err)

    private enum class Unit(val unitSize: Long) {
        MiB(1024 * 1024),
        GiB(1024 * 1024 * 1024)
    }

    fun run() {
        task.incidents.subscribe { incident ->
            multiplexer.printTextLine("${incident.severity.consolePrefix()}${incident.path}: ${incident.text}")
        }
        task.onProgressTypeChanged = {
            val progress = task.progress
            when (progress) {
                is TaskProgress.ScanTree -> multiplexer.replaceProgressWidgets(listOf(
                        ConsoleProgressWidget.StatusPrinter {
                            "Scanning files... ${progress.totalFiles} found so far"
                        }
                ))
                is TaskProgress.ChecksumClient -> {
                    val unit = if (progress.totalBytes >= 10 * Unit.GiB.unitSize) Unit.GiB else Unit.MiB

                    val filesReadBar = ConsoleProgressWidget.BoundedBar(
                            "Files read", progress.totalFiles.toLong())
                    val bytesReadBar = ConsoleProgressWidget.BoundedBar(
                            "Bytes read", progress.totalBytes, unitName = unit.name, unitSize = unit.unitSize)
                    multiplexer.replaceProgressWidgets(listOf(filesReadBar, bytesReadBar))
                    progress.onProgressChanged = {
                        filesReadBar.value = progress.doneFiles.toLong()
                        bytesReadBar.value = progress.doneBytes
                    }
                }
            }
        }
        val finishMessage = task.start().blockingGet()
        multiplexer.replaceProgressWidgets(listOf())
        multiplexer.printTextLine(finishMessage.message)
    }

}