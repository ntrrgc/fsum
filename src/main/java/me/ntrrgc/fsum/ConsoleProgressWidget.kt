package me.ntrrgc.fsum

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import java.io.Closeable
import java.io.OutputStream
import java.io.PrintStream

sealed class ConsoleProgressWidget : Closeable {
    var latestLine: String = ""
        protected set(value) {
            field = value
            onLineUpdated?.invoke(this)
        }

    var onLineUpdated: ((c: ConsoleProgressWidget) -> Unit)? = null

    class StatusPrinter(val statusFn: () -> String) : ConsoleProgressWidget() {
        var stopped = false
        val thread = Thread {
            while (!stopped) {
                latestLine = statusFn()
                Thread.sleep(200)
            }
        }

        override fun close() {
            stopped = true
        }
    }

    class BoundedBar(taskName: String, maxValue: Long, unitName: String = "", unitSize: Long = 1) : ConsoleProgressWidget() {
        private val progressBar = ProgressBar(taskName, maxValue, 200, PrintStream(object : OutputStream() {
            override fun write(p0: Int) = throw NotImplementedError()
            override fun write(b: ByteArray, off: Int, len: Int) {
                // Rather hacky way to get the progress bar, but it works.
                val line = String(b, off, len)
                if (line != "\r") {
                    latestLine = line.removeSuffix("\n")
                }
            }
        }), ProgressBarStyle.COLORFUL_UNICODE_BLOCK, unitName, unitSize)

        var value: Long
            get() = progressBar.current
            set(value) {
                progressBar.stepTo(value)
            }

        override fun close() {
            progressBar.close()
        }
    }

}