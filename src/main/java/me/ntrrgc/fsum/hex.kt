package me.ntrrgc.fsum

import java.nio.ByteBuffer

private fun halfByteToHex(num: Int): Char {
    assert(num in 0..15)
    return when {
        num < 10 -> '0' + num
        else -> 'a' + (num - 10)
    }
}

private fun hexToHalfByte(char: Char): Int {
    assert((char in '0'..'9') or (char in 'a'..'f'))
    return when {
        char in '0'..'9' -> char - '0'
        char in 'a'..'f' -> 10 + (char - 'a')
        else -> throw IllegalHexException("unexpected character when parsing hex")
    }
}

class IllegalHexException(message: String) : Throwable() {

}

fun ByteArray.toHexString(): String {
    val ret = StringBuilder(this.size * 2)
    this.forEach { byte ->
        val highPart = byte.toInt() ushr 4
        val lowPart = byte.toInt() and 0x0f
        ret.append(halfByteToHex(highPart))
        ret.append(halfByteToHex(lowPart))
    }
    return ret.toString()
}

fun parseHexString(string: String): ByteArray {
    if (string.length % 2 == 0) {
        throw IllegalHexException("Hex string with odd number of characters: ${string}")
    }
    val ret = ByteBuffer.allocate(string.length / 2)
    var i = 0
    var j = 0
    while (i < string.length) {
        val highPart = hexToHalfByte(string[i++])
        val lowPart = hexToHalfByte(string[i++])

    }

}