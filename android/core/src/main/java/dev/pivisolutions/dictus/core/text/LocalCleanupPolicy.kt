package dev.pivisolutions.dictus.core.text

/** Conservative factual checks. Rejection preserves the original transcription. */
object LocalCleanupPolicy {
    fun accept(input: String, output: String): String? {
        if (!output.contains("[[END]]")) return null
        val text = output.substringBefore("[[END]]").trim()
        if (text.isBlank() || text.length > input.length * 2 + 40 || text.length * 2 < input.length) return null
        val digits = Regex("[0-9]+")
        if (digits.findAll(input).map { it.value }.toList() != digits.findAll(text).map { it.value }.toList()) return null
        fun words(value: String) = value.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotEmpty() }.toSet()
        val before = words(input)
        val after = words(text)
        for (word in listOf("not", "never", "no", "don't", "can't", "won't", "isn't", "didn't")) {
            if ((word in before) != (word in after)) return null
        }
        if (before.isNotEmpty() && before.intersect(after).size * 100 < before.size * 65) return null
        return text
    }
}
