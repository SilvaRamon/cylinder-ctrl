package com.silvaramon.cylinders_ctrl.data

/** Dados de identificação extraídos via OCR da etiqueta do cilindro. */
data class CylinderOcrData(
    val capacityM3: String,
    val serialNumber: String,
)

sealed interface OcrParseResult {
    data class Success(val data: CylinderOcrData) : OcrParseResult
    data class Error(val message: String) : OcrParseResult
}

/**
 * Extrai os campos `CAP_M3` (capacidade) e `N/S` (número de série) do texto do OCR.
 *
 * O valor pode estar na mesma linha do rótulo (ex.: `CAP_M3: 10,0`) ou na linha
 * seguinte, já que o ML Kit costuma separar rótulo e valor em blocos distintos.
 * O parser tolera variações comuns de OCR (`CAP M3`, `CAP.M3`, `CAP_M³`, `N / S`, `N\S`).
 */
object CylinderOcrParser {

    private val capLabel = Regex("""CAP[\s._\-]*M\s*[3³]""", RegexOption.IGNORE_CASE)
    private val serialLabel = Regex("""(?<![A-Z0-9])N\s*[/\\|]\s*S(?![A-Z0-9])""", RegexOption.IGNORE_CASE)

    private val capValue = Regex("""^[\s:=.\-]*(\d+(?:[.,]\d+)?)""")
    private val serialValue = Regex("""^[\s:=.\-]*([A-Z0-9][A-Z0-9\-/.]*)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): OcrParseResult {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val capacity = findValue(lines, capLabel, capValue)?.replace(',', '.')
        val serial = findValue(lines, serialLabel, serialValue)?.uppercase()

        val missing = buildList {
            if (capacity == null) add("CAP_M3")
            if (serial == null) add("N/S")
        }
        return if (missing.isEmpty()) {
            OcrParseResult.Success(CylinderOcrData(capacity!!, serial!!))
        } else {
            OcrParseResult.Error(
                "Não foi possível identificar ${missing.joinToString(" e ")} na captura. " +
                    "Aproxime a câmera da etiqueta e tente novamente."
            )
        }
    }

    private fun findValue(lines: List<String>, label: Regex, value: Regex): String? {
        lines.forEachIndexed { index, line ->
            val match = label.find(line) ?: return@forEachIndexed
            val sameLine = line.substring(match.range.last + 1)
            value.find(sameLine)?.let { return it.groupValues[1] }
            lines.getOrNull(index + 1)
                ?.let { next -> value.find(next) }
                ?.let { return it.groupValues[1] }
        }
        return null
    }
}
