package me.ntrrgc.fsum

data class Incident(
        val severity: Severity,
        val path: String,
        val text: String
) {
    enum class Severity(val vtFormatPrefix: String) {
        Warning("${TC.bold}${TC.fgYellow}"), // missing files
        Error("${TC.bold}${TC.fgLightRed}"); // I/O errors, mismatched checksums, parsing errors (access denied at best, corrupted data at worst)

        fun textPrefix(): String {
            return "$name: "
        }

        fun consolePrefix(): String {
            return if (System.console() != null) {
                "$vtFormatPrefix${textPrefix()}${TC.resetFormatting}"
            } else {
                textPrefix()
            }
        }
    }
}