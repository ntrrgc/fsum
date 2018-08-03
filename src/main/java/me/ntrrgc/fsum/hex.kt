package me.ntrrgc.fsum

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

class IllegalHexException(message: String) : Exception(message)

fun ByteArray.toHexString(): String {
    val ret = StringBuilder(this.size * 2)
    this.forEach { byte ->
        val unsignedByte = byte.toInt() and 0xff
        val highPart = unsignedByte ushr 4
        val lowPart = unsignedByte and 0x0f
        ret.append(halfByteToHex(highPart))
        ret.append(halfByteToHex(lowPart))
    }
    return ret.toString()
}

fun parseHexString(string: String): ByteArray {
    if (string.length % 2 != 0) {
        throw IllegalHexException("Hex string with odd number of characters: ${string}")
    }
    val lowerCaseString = string.toLowerCase()
    val ret = ByteArray(lowerCaseString.length / 2)
    var i = 0
    var j = 0
    while (i < string.length) {
        val highPart = hexToHalfByte(lowerCaseString[i++])
        val lowPart = hexToHalfByte(lowerCaseString[i++])
        ret[j++] = ((highPart shl 4) or lowPart).toByte()
    }
    return ret
}