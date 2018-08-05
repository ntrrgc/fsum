package me.ntrrgc.fsum

import java.io.OutputStream

class ConsoleProgressMultiplexer(private val sinkStream: OutputStream) {
    private var progressWidgets = listOf<ConsoleProgressWidget>()

    fun replaceProgressWidgets(progressWidgets: List<ConsoleProgressWidget>) {
        lateinit var oldWidgets: List<ConsoleProgressWidget>
        synchronized(this) {
            // Unbind events for old widgets
            oldWidgets = this.progressWidgets
            oldWidgets.forEach { widget ->
                widget.onLineUpdated = null
            }

            val differenceLines = progressWidgets.size - this.progressWidgets.size
            if (differenceLines > 0) {
                // Add new lines
                repeat(differenceLines) {
                    sinkStream.write('\n'.toInt())
                }
            } else {
                // Erase lines
                repeat(-differenceLines) {
                    sinkStream.write("${TC.cursorUp(1)}${TC.eraseLine}".toByteArray())
                }
            }

            this.progressWidgets = progressWidgets
            progressWidgets.forEach { widget ->
                widget.onLineUpdated = { printProgressBarAndFlush(it) }
            }
            progressWidgets.forEach(::printProgressBar)
            sinkStream.flush()
        }

        oldWidgets.forEach { it.close() }
    }

    private fun printProgressBar(progressWidget: ConsoleProgressWidget) {
        val widgetIndex = progressWidgets.indexOf(progressWidget)
        if (widgetIndex == -1) {
            // Notification from a widget that was removed before it acquired the mutex for this method.
            return
        }
        // For the last progress widget we don't need to move the cursor up. For the before last we need to move one
        // line up, and so on.
        val linesUp = progressWidgets.size - widgetIndex
        // Save the cursor position, move to the line of the selected progress bar, replace its contents and restore
        // the previous position
        sinkStream.write(("${TC.saveCursor}\r${TC.cursorUp(linesUp)}${TC.eraseLine}" +
                "${progressWidget.latestLine}${TC.restoreCursor}").toByteArray())
    }

    @Synchronized
    private fun printProgressBarAndFlush(progressWidget: ConsoleProgressWidget) {
        printProgressBar(progressWidget)
        sinkStream.flush()
    }

    @Synchronized
    fun printTextLine(line: String) {
        // Erase progress bars
        sinkStream.write('\r'.toInt())
        var remainingLines = progressWidgets.size
        while (remainingLines > 0) {
            sinkStream.write("${TC.cursorUp(1)}${TC.eraseLine}".toByteArray())
            remainingLines--
        }

        // Print text line
        sinkStream.write("$line\n".toByteArray())

        // Print progress bars following the new line.
        progressWidgets.forEach {
            sinkStream.write("${it.latestLine}\n".toByteArray())
        }
        sinkStream.flush()
    }
}