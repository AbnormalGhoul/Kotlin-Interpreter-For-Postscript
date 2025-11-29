package postscript

import java.lang.NumberFormatException

/**
 * Core function:
 *  - Parses a single input line (one token per line mode) into a postscript.Value (or returns an empty list for no output)
 *  - Handles procedure block assembly: `{` and `}` must be on their own lines; everything between becomes tokens of PSProc
 *
 * Simplified lexical rules:
 *  - "(...)" => PSString
 *  - "/name" => PSName(name, executable = false)
 *  - "true"/"false" => PSBool
 *  - integer (no '.') => PSInt
 *  - float (with '.') => PSReal
 *  - "{" starts procedure collection, "}" ends and returns PSProc
 *  - otherwise => PSName(token, executable = true)
 */

class Parser {
    private var procMode = false
    private val procBuffer = mutableListOf<String>()
    private var procDepth = 0

    // Parses a single line, returns a list of postscript.Value(s) (usually single item or empty)
    fun parseLine(line: String): List<Value> {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return emptyList()

        // procedure start
        if (!procMode && trimmed == "{") {
            procMode = true
            procBuffer.clear()
            procDepth = 1
            return emptyList()
        }

        if (procMode) {
            when (trimmed) {
                "{" -> {
                    procDepth++
                    procBuffer.add("{")
                    return emptyList()
                }
                "}" -> {
                    procDepth--
                    if (procDepth == 0) {
                        procMode = false
                        val tokens = procBuffer.toList()
                        return listOf(Value.PSProc(tokens))
                    } else {
                        procBuffer.add("}")
                        return emptyList()
                    }
                }
                else -> {
                    procBuffer.add(trimmed)
                    return emptyList()
                }
            }
        }

        // Strings (single-line)
        if (trimmed.startsWith("(") && trimmed.endsWith(")") && trimmed.length >= 2) {
            val content = trimmed.substring(1, trimmed.length - 1)
            return listOf(Value.PSString(content))
        }

        // literal name
        if (trimmed.startsWith("/")) {
            val name = trimmed.substring(1)
            return listOf(Value.PSName(name, executable = false))
        }

        // booleans
        if (trimmed == "true") return listOf(Value.PSBool(true))
        if (trimmed == "false") return listOf(Value.PSBool(false))

        // numbers
        try {
            if (trimmed.contains(".")) {
                val d = trimmed.toDouble()
                return listOf(Value.PSReal(d))
            } else {
                val i = trimmed.toLong()
                return listOf(Value.PSInt(i))
            }
        } catch (e: NumberFormatException) {
            // not a number
        }

        // name, operator (executable by default)
        return listOf(Value.PSName(trimmed, executable = true))
    }
}