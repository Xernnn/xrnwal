package com.xern.xrnwallet.domain

/** RFC 4180 reading and writing. */
object Csv {
    fun parse(input: String): List<List<String>> {
        val text = input.removePrefix("\uFEFF")
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val c = text[index]
            when {
                quoted && c == '"' && text.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index++
                }
                quoted && c == '"' -> quoted = false
                quoted -> cell.append(c)
                c == '"' -> quoted = true
                c == ',' -> {
                    row += cell.toString()
                    cell.clear()
                }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && text.getOrNull(index + 1) == '\n') index++
                    row += cell.toString()
                    cell.clear()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(c)
            }
            index++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            if (row.any { it.isNotBlank() }) rows += row
        }
        return rows
    }

    fun write(rows: List<List<String>>): String = buildString {
        append('\uFEFF')
        rows.forEach { row ->
            append(row.joinToString(",", transform = ::escape))
            append("\r\n")
        }
    }

    private fun escape(cell: String): String =
        if (cell.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + cell.replace("\"", "\"\"") + "\""
        } else {
            cell
        }
}
