package postscript

/**
 * Core function:
 *  - Small helper extension methods for postscript.Value conversions used across operators.
 */

fun Value.asIntOrThrow(): Long = when (this) {
    is Value.PSInt -> this.value
    is Value.PSReal -> this.value.toLong()
    else -> throw PostScriptException("typecheck: expected integer but got $this")
}

fun Value.asStringOrThrow(): String = when (this) {
    is Value.PSString -> this.chars
    else -> throw PostScriptException("typecheck: expected string but got $this")
}