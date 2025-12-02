package postscript

/**
 * Core function:
 *  - Defines the `postscript.Value` sealed class which represents PostScript runtime values (integers, reals, booleans,
 *    strings, names, arrays, dicts, procedures, and null)
 *  - Provides `toString` representations used by `=` and printing operations
 *
 * Notes:
 *  - `PSName` wraps a name and stores whether it's executable (true by default)
 *  - `PSProc` stores a list of token-lines as strings and optionally holds a lexical environment snapshot
 */

sealed class Value {
    data class PSInt(val value: Long) : Value()
    data class PSReal(val value: Double) : Value()
    data class PSBool(val value: Boolean) : Value()
    // `chars` is var so we make an operator that can mutate string contents (when mutable==true)
    data class PSString(var chars: String, var mutable: Boolean = true) : Value()
    data class PSName(val name: String, val executable: Boolean = true) : Value()
    data class PSArray(val elements: MutableList<Value>) : Value()
    data class PSDict(val map: MutableMap<String, Value>) : Value()
    data class PSProc(
        val tokens: List<String>,                       // procedure token lines
        var isExecutable: Boolean = true,
        var lexicalEnv: List<PostScriptDict>? = null   // if set then procedure has captured lexical env
    ) : Value()

    object PSNull : Value()

    override fun toString(): String = when (this) {
        is PSInt -> value.toString()
        is PSReal -> {
            // Avoids unnecessary decimal part when number is integral
            if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        }
        is PSBool -> value.toString()
        is PSString -> "(${chars})"
        is PSName -> "/$name"
        is PSArray -> elements.toString()
        is PSDict -> "<<${map.entries.joinToString(", ") { it.key + " -> " + it.value }}>>"
        is PSProc -> "{ ${tokens.joinToString(" ")} }"
        PSNull -> "null"
    }
}
