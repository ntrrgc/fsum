package me.ntrrgc.fsum

object TC {
    val esc = "\u001b"
    val resetFormatting = "$esc[0m"
    val bold = "$esc[1m"
    val fgYellow = "$esc[33m"
    val fgLightRed = "$esc[91m"

    fun cursorUp(count: Int) = "$esc[${count}A"
    fun cursorDown(count: Int) = "$esc[${count}B"

    val saveCursor = "$esc[s"
    val restoreCursor = "$esc[u"

    val eraseLine = "$esc[2K"
}