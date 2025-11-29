package postscript

/**
 * Core function:
 *  - Implements the interpreter's operand stack behavior: push, pop, peek, type-safe pop helpers, and utilities
 *  - Provides convenience helpers to pop numbers and preserve integer-vs-real semantics where reasonable
 */

class OperandStack {
    private val stack = ArrayDeque<Value>()

    fun push(v: Value) = stack.addLast(v)
    fun pop(): Value {
        if (stack.isEmpty()) throw PostScriptException("stackunderflow")
        return stack.removeLast()
    }
    fun peek(): Value {
        if (stack.isEmpty()) throw PostScriptException("stackunderflow")
        return stack.last()
    }
    fun clear() = stack.clear()
    fun count(): Int = stack.size
    fun toList(): List<Value> = stack.toList()
    fun isEmpty() = stack.isEmpty()

    // Typed helpers
    fun popNumberAsDouble(): Double {
        val v = pop()
        return when (v) {
            is Value.PSInt -> v.value.toDouble()
            is Value.PSReal -> v.value
            else -> throw PostScriptException("typecheck: expected number but got $v")
        }
    }

    // returns postscript.Value maintaining integer type if both ints and result is integral
    fun popTwoNumbersReturnBest(aFirst: Boolean = true, op: (Double, Double) -> Double): Value {
        // aFirst: when true, pop second then first (so op(first, second))
        val second = popNumberAsDouble()
        val first = popNumberAsDouble()
        val res = if (aFirst) op(first, second) else op(second, first)
        // if res is integer within epsilon, return PSInt
        if (res % 1.0 == 0.0) return Value.PSInt(res.toLong())
        return Value.PSReal(res)
    }

    fun popIntValue(): Long {
        val v = pop()
        return when (v) {
            is Value.PSInt -> v.value
            is Value.PSReal -> v.value.toLong()
            else -> throw PostScriptException("typecheck: expected int but got $v")
        }
    }
}