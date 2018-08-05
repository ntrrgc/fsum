package me.ntrrgc.fsum.progressBarScratchpad

import me.tongfei.progressbar.ProgressBar
import me.tongfei.progressbar.ProgressBarStyle
import java.io.OutputStream
import java.io.PrintStream

fun main(args: Array<String>) {
    val progressOutputStream = ProgressOutputStream(System.err)
    val progressPrintStream = PrintStream(progressOutputStream)

    val pb = ProgressBar("Creating checksums", 100, 300, progressPrintStream,
            ProgressBarStyle.COLORFUL_UNICODE_BLOCK, "", 1)
    pb.use {
        for (i in 1..100) {
            pb.stepBy(1)
            if (i % 10 == 0) {
                progressOutputStream.println("\u001b[2K\rHi!")
            }

            Thread.sleep(100)
        }
    }
}

interface LinePrinter {
    fun println(line: String)
}

class ProgressOutputStream(private val sinkStream: OutputStream) : OutputStream(), LinePrinter {
    private var lastProgressBarText: String = ""

    override fun write(byte: Int) {
        throw NotImplementedError()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (!(len == 1 && b[off] == '\r'.toByte())) {
            synchronized(this) {
                lastProgressBarText = String(b, off, len)
                sinkStream.write('\r'.toInt())
                sinkStream.write(b, off, len)
                sinkStream.flush()
            }
        }
    }

    override fun println(line: String) {
        synchronized(this) {
            sinkStream.write("\u001b[2K\r$line\n$lastProgressBarText".toByteArray())
            sinkStream.flush()
        }
    }
}