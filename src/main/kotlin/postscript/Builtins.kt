package postscript

import kotlin.collections.iterator
import kotlin.math.*

/**
 * Core function:
 *  - Registers built-in PostScript operators with the postscript.Interpreter via `registerOperator`
 *  - Implementations manipulate the postscript.Interpreter's operandStack and dictStack
 *  - Basically this contains all the built-in PostScript operators
 *
 */

object Builtins {
    fun registerAll(interp: Interpreter) {
        // Basic stack operations
        interp.registerOperator("pop") { i ->
            i.operandStack.pop()
        }

        interp.registerOperator("dup") { i ->
            val top = i.operandStack.peek()
            i.operandStack.push(top)
        }

        interp.registerOperator("exch") { i ->
            val a = i.operandStack.pop()
            val b = i.operandStack.pop()
            i.operandStack.push(a)
            i.operandStack.push(b)
        }

        interp.registerOperator("clear") { i ->
            i.operandStack.clear()
        }

        interp.registerOperator("count") { i ->
            i.operandStack.push(Value.PSInt(i.operandStack.count().toLong()))
        }

        // mark, cleartomark, counttomark
        val MARK_NAME = "__MARK__"
        interp.registerOperator("mark") { i ->
            i.operandStack.push(Value.PSName(MARK_NAME, executable = false))
        }

        interp.registerOperator("cleartomark") { i ->
            while (true) {
                if (i.operandStack.isEmpty()) throw PostScriptException("unmatchedmark")
                val v = i.operandStack.pop()
                if (v is Value.PSName && v.name == MARK_NAME) break
            }
        }

        interp.registerOperator("counttomark") { i ->
            var count = 0
            // traverse from top to bottom
            val list = i.operandStack.toList().asReversed()
            for (v in list) {
                if (v is Value.PSName && v.name == MARK_NAME) break
                count++
            }
            i.operandStack.push(Value.PSInt(count.toLong()))
        }

        interp.registerOperator("index") { i ->
            val nVal = i.operandStack.pop()
            val n = when (nVal) {
                is Value.PSInt -> nVal.value.toInt()
                is Value.PSReal -> nVal.value.toInt()
                else -> throw PostScriptException("typecheck")
            }
            if (n < 0 || n >= i.operandStack.count()) throw PostScriptException("rangecheck")
            val arr = i.operandStack.toList()
            val target = arr[arr.size - 1 - n]
            i.operandStack.push(target)
        }

        interp.registerOperator("roll") { i ->
            val jVal = i.operandStack.pop()
            val nVal = i.operandStack.pop()
            val j = when (jVal) {
                is Value.PSInt -> jVal.value.toInt()
                is Value.PSReal -> jVal.value.toInt()
                else -> throw PostScriptException("typecheck")
            }
            val n = when (nVal) {
                is Value.PSInt -> nVal.value.toInt()
                is Value.PSReal -> nVal.value.toInt()
                else -> throw PostScriptException("typecheck")
            }
            if (n < 0 || n > i.operandStack.count()) throw PostScriptException("rangecheck")
            if (n == 0 || n == 1) return@registerOperator

            // Collect the top n elements (top is last)
            val tmp = mutableListOf<Value>()
            repeat(n) { tmp.add(0, i.operandStack.pop()) } // tmp[0] = bottom of chunk ... tmp[n-1] = top

            // Normalize shift to [0, n-1] (right-rotation by j)
            val shift = ((j % n) + n) % n
            val rotated = MutableList<Value>(n) { Value.PSNull }
            for (idx in 0 until n) {
                rotated[(idx + shift) % n] = tmp[idx]
            }
            // push rotated back (bottom -> top)
            for (v in rotated) i.operandStack.push(v)
        }

        // copy: stack copy or array to array
        interp.registerOperator("copy") { i ->
            val top = i.operandStack.pop()
            when (top) {
                is Value.PSInt -> {
                    val n = top.value.toInt()
                    if (n < 0 || n > i.operandStack.count()) throw PostScriptException("rangecheck")
                    val arr = i.operandStack.toList()
                    val size = arr.size
                    for (k in (size - n) until size) {
                        i.operandStack.push(arr[k])
                    }
                }
                is Value.PSArray -> {
                    val source = top
                    val destVal = i.operandStack.pop()
                    if (destVal !is Value.PSArray) throw PostScriptException("typecheck")
                    val dest = destVal
                    val min = min(source.elements.size, dest.elements.size)
                    for (idx in 0 until min) dest.elements[idx] = source.elements[idx]
                    i.operandStack.push(dest)
                }
                else -> throw PostScriptException("typecheck")
            }
        }

        // Arithmetic
        interp.registerOperator("add") { i ->
            val res = i.operandStack.popTwoNumbersReturnBest(op = { a, b -> a + b })
            i.operandStack.push(res)
        }

        interp.registerOperator("sub") { i ->
            val res = i.operandStack.popTwoNumbersReturnBest(op = { a, b -> a - b })
            i.operandStack.push(res)
        }

        interp.registerOperator("mul") { i ->
            val res = i.operandStack.popTwoNumbersReturnBest(op = { a, b -> a * b })
            i.operandStack.push(res)
        }

        interp.registerOperator("div") { i ->
            val b = i.operandStack.popNumberAsDouble()
            val a = i.operandStack.popNumberAsDouble()
            if (b == 0.0) throw PostScriptException("rangecheck")
            i.operandStack.push(Value.PSReal(a / b))
        }

        interp.registerOperator("idiv") { i ->
            val b = i.operandStack.popIntValue()
            val a = i.operandStack.popIntValue()
            if (b == 0L) throw PostScriptException("rangecheck")
            i.operandStack.push(Value.PSInt(a / b))
        }

        interp.registerOperator("mod") { i ->
            val b = i.operandStack.popIntValue()
            val a = i.operandStack.popIntValue()
            if (b == 0L) throw PostScriptException("rangecheck")
            i.operandStack.push(Value.PSInt(a % b))
        }

        interp.registerOperator("neg") { i ->
            val v = i.operandStack.pop()
            when (v) {
                is Value.PSInt -> i.operandStack.push(Value.PSInt(-v.value))
                is Value.PSReal -> i.operandStack.push(Value.PSReal(-v.value))
                else -> throw PostScriptException("typecheck")
            }
        }

        interp.registerOperator("abs") { i ->
            val v = i.operandStack.pop()
            when (v) {
                is Value.PSInt -> i.operandStack.push(Value.PSInt(abs(v.value)))
                is Value.PSReal -> i.operandStack.push(Value.PSReal(abs(v.value)))
                else -> throw PostScriptException("typecheck")
            }
        }

        interp.registerOperator("ceiling") { i ->
            val d = i.operandStack.popNumberAsDouble()
            i.operandStack.push(Value.PSReal(ceil(d)))
        }

        interp.registerOperator("floor") { i ->
            val d = i.operandStack.popNumberAsDouble()
            i.operandStack.push(Value.PSReal(floor(d)))
        }

        interp.registerOperator("round") { i ->
            val d = i.operandStack.popNumberAsDouble()
            i.operandStack.push(Value.PSReal(round(d)))
        }

        interp.registerOperator("sqrt") { i ->
            val d = i.operandStack.popNumberAsDouble()
            if (d < 0) throw PostScriptException("rangecheck")
            i.operandStack.push(Value.PSReal(sqrt(d)))
        }

        interp.registerOperator("sin") { i ->
            val deg = i.operandStack.popNumberAsDouble()
            i.operandStack.push(Value.PSReal(sin(Math.toRadians(deg))))
        }

        interp.registerOperator("cos") { i ->
            val deg = i.operandStack.popNumberAsDouble()
            i.operandStack.push(Value.PSReal(cos(Math.toRadians(deg))))
        }

        interp.registerOperator("atan") { i ->
            val den = i.operandStack.popNumberAsDouble()
            val num = i.operandStack.popNumberAsDouble()
            i.operandStack.push(Value.PSReal(Math.toDegrees(atan2(num, den))))
        }

        // simple PRNG wrappers
        var rndSeed: Long = System.currentTimeMillis()
        interp.registerOperator("srand") { i ->
            val seed = i.operandStack.popIntValue()
            rndSeed = seed
        }
        interp.registerOperator("rand") { i ->
            // use 64-bit LCG, ensures positive
            rndSeed = (rndSeed * 6364136223846793005L + 1442695040888963407L) and Long.MAX_VALUE
            val res = (rndSeed ushr 33) and 0x7FFFFFFF
            i.operandStack.push(Value.PSInt(res))
        }
        interp.registerOperator("rrand") { i ->
            i.operandStack.push(Value.PSInt((System.currentTimeMillis() and Long.MAX_VALUE)))
        }

        // Dictionaries
        interp.registerOperator("dict") { i ->
            val size = i.operandStack.popIntValue()
            val dict = Value.PSDict(mutableMapOf())
            i.operandStack.push(dict)
        }

        interp.registerOperator("begin") { i ->
            val d = i.operandStack.pop()
            if (d !is Value.PSDict) throw PostScriptException("typecheck")
            i.dictStack.begin(d)
        }

        interp.registerOperator("end") { i ->
            i.dictStack.end()
        }

        interp.registerOperator("def") { i ->
            val value = i.operandStack.pop()
            val key = i.operandStack.pop()
            if (key !is Value.PSName || key.executable) throw PostScriptException("typecheck")
            val name = key.name
            // If lexical scoping mode and value is a proc, capture a copy of the current dictstack snapshot
            if (i.lexical && value is Value.PSProc) {
                // Create shallow copies of each dict (so later mutations to the live dictstack
                // won't affect this captured lexical environment).
                val snapshot = i.dictStack.snapshot()
                val copied = snapshot.map { orig ->
                    // make a shallow copy of the map
                    Value.PSDict(orig.map.toMutableMap())
                }
                value.lexicalEnv = copied
            }
            i.dictStack.define(name, value)
        }


        interp.registerOperator("store") { i ->
            val value = i.operandStack.pop()
            val key = i.operandStack.pop()
            if (key !is Value.PSName || key.executable) throw PostScriptException("typecheck")
            i.dictStack.store(key.name, value)
        }

        interp.registerOperator("where") { i ->
            val keyval = i.operandStack.pop()
            if (keyval !is Value.PSName || keyval.executable) throw PostScriptException("typecheck")
            val name = keyval.name
            val d = i.dictStack.where(name)
            if (d != null) {
                i.operandStack.push(d)
                i.operandStack.push(Value.PSBool(true))
            } else {
                i.operandStack.push(Value.PSBool(false))
            }
        }

        interp.registerOperator("load") { i ->
            val key = i.operandStack.pop()
            if (key !is Value.PSName) throw PostScriptException("typecheck")
            val v = i.dictStack.lookup(key.name) ?: throw PostScriptException("undefined")
            i.operandStack.push(v)
        }

        interp.registerOperator("known") { i ->
            val key = i.operandStack.pop()
            val d = i.operandStack.pop()
            if (d !is Value.PSDict || key !is Value.PSName) throw PostScriptException("typecheck")
            i.operandStack.push(Value.PSBool(d.map.containsKey(key.name)))
        }

        // get, put for dicts, strings, arrays
        interp.registerOperator("get") { i ->
            val index = i.operandStack.pop()
            val container = i.operandStack.pop()
            when (container) {
                is Value.PSDict -> {
                    if (index !is Value.PSName) throw PostScriptException("typecheck")
                    val valx = container.map[index.name] ?: throw PostScriptException("undefined")
                    i.operandStack.push(valx)
                }
                is Value.PSArray -> {
                    val idx = when (index) {
                        is Value.PSInt -> index.value.toInt()
                        is Value.PSReal -> index.value.toInt()
                        else -> throw PostScriptException("typecheck")
                    }
                    if (idx < 0 || idx >= container.elements.size) throw PostScriptException("rangecheck")
                    i.operandStack.push(container.elements[idx])
                }
                is Value.PSString -> {
                    val idx = when (index) {
                        is Value.PSInt -> index.value.toInt()
                        is Value.PSReal -> index.value.toInt()
                        else -> throw PostScriptException("typecheck")
                    }
                    if (idx < 0 || idx >= container.chars.length) throw PostScriptException("rangecheck")
                    val code = container.chars[idx].code
                    i.operandStack.push(Value.PSInt(code.toLong()))
                }
                else -> throw PostScriptException("typecheck")
            }
        }

        interp.registerOperator("put") { i ->
            val value = i.operandStack.pop()
            val index = i.operandStack.pop()
            val container = i.operandStack.pop()
            when (container) {
                is Value.PSDict -> {
                    if (index !is Value.PSName) throw PostScriptException("typecheck")
                    container.map[index.name] = value
                }
                is Value.PSArray -> {
                    val idx = when (index) {
                        is Value.PSInt -> index.value.toInt()
                        is Value.PSReal -> index.value.toInt()
                        else -> throw PostScriptException("typecheck")
                    }
                    if (idx < 0 || idx >= container.elements.size) throw PostScriptException("rangecheck")
                    container.elements[idx] = value
                }
                is Value.PSString -> {
                    if (!container.mutable) throw PostScriptException("invalidaccess")
                    val idx = when (index) {
                        is Value.PSInt -> index.value.toInt()
                        is Value.PSReal -> index.value.toInt()
                        else -> throw PostScriptException("typecheck")
                    }
                    if (idx < 0 || idx >= container.chars.length) throw PostScriptException("rangecheck")
                    // value should be integer char code
                    val code = when (value) {
                        is Value.PSInt -> value.value.toInt()
                        else -> throw PostScriptException("typecheck")
                    }
                    val sb = StringBuilder(container.chars)
                    sb.setCharAt(idx, code.toChar())
                    container.chars = sb.toString()
                }
                else -> throw PostScriptException("typecheck")
            }
        }

        interp.registerOperator("length") { i ->
            val top = i.operandStack.pop()
            when (top) {
                is Value.PSArray -> i.operandStack.push(Value.PSInt(top.elements.size.toLong()))
                is Value.PSString -> i.operandStack.push(Value.PSInt(top.chars.length.toLong()))
                is Value.PSDict -> i.operandStack.push(Value.PSInt(top.map.size.toLong()))
                else -> throw PostScriptException("typecheck")
            }
        }

        // array and string creation
        interp.registerOperator("array") { i ->
            val size = i.operandStack.popIntValue().toInt()
            if (size < 0) throw PostScriptException("rangecheck")
            val arr = Value.PSArray(MutableList(size) { Value.PSNull })
            i.operandStack.push(arr)
        }

        interp.registerOperator("string") { i ->
            val size = i.operandStack.popIntValue().toInt()
            if (size < 0) throw PostScriptException("rangecheck")
            val s = Value.PSString(" ".repeat(size), mutable = true)
            i.operandStack.push(s)
        }

        interp.registerOperator("aload") { i ->
            val arr = i.operandStack.pop()
            if (arr !is Value.PSArray) throw PostScriptException("typecheck")
            for (elem in arr.elements) i.operandStack.push(elem)
            i.operandStack.push(arr)
        }

        interp.registerOperator("astore") { i ->
            val arr = i.operandStack.pop()
            if (arr !is Value.PSArray) throw PostScriptException("typecheck")
            for (idx in arr.elements.size - 1 downTo 0) {
                arr.elements[idx] = i.operandStack.pop()
            }
            i.operandStack.push(arr)
        }

        // forall on arrays, dicts
        interp.registerOperator("forall") { i ->
            val procv = i.operandStack.pop()
            val arr = i.operandStack.pop()
            if (procv !is Value.PSProc) throw PostScriptException("typecheck")
            if (arr is Value.PSArray) {
                for (elem in arr.elements) {
                    i.operandStack.push(elem)
                    i.executeProcedure(procv)
                }
            } else if (arr is Value.PSDict) {
                for ((k, v) in arr.map) {
                    i.operandStack.push(Value.PSName(k, executable = false))
                    i.operandStack.push(v)
                    i.executeProcedure(procv)
                }
            } else throw PostScriptException("typecheck")
        }

        // Flow control: exec, if, ifelse, repeat, for, quit
        interp.registerOperator("exec") { i ->
            val obj = i.operandStack.pop()
            when (obj) {
                is Value.PSProc -> i.executeProcedure(obj)
                is Value.PSName -> i.evalToken(obj)
                else -> throw PostScriptException("typecheck")
            }
        }

        interp.registerOperator("if") { i ->
            val proc = i.operandStack.pop()
            val cond = i.operandStack.pop()
            if (proc !is Value.PSProc) throw PostScriptException("typecheck")
            if (cond !is Value.PSBool) throw PostScriptException("typecheck")
            if (cond.value) i.executeProcedure(proc)
        }

        interp.registerOperator("ifelse") { i ->
            val proc2 = i.operandStack.pop()
            val proc1 = i.operandStack.pop()
            val cond = i.operandStack.pop()
            if (proc1 !is Value.PSProc || proc2 !is Value.PSProc || cond !is Value.PSBool) throw PostScriptException("typecheck")
            if (cond.value) i.executeProcedure(proc1) else i.executeProcedure(proc2)
        }

        interp.registerOperator("repeat") { i ->
            val proc = i.operandStack.pop()
            val nVal = i.operandStack.pop()
            val n = when (nVal) {
                is Value.PSInt -> nVal.value.toInt()
                is Value.PSReal -> nVal.value.toInt()
                else -> throw PostScriptException("typecheck")
            }
            if (proc !is Value.PSProc) throw PostScriptException("typecheck")
            repeat(n) { i.executeProcedure(proc) }
        }

        // for: init increment limit proc for
        interp.registerOperator("for") { i ->
            val proc = i.operandStack.pop()
            val limitVal = i.operandStack.pop()
            val incVal = i.operandStack.pop()
            val initVal = i.operandStack.pop()

            if (proc !is Value.PSProc) throw PostScriptException("typecheck")
            val init = when (initVal) {
                is Value.PSInt -> initVal.value
                is Value.PSReal -> initVal.value.toLong()
                else -> throw PostScriptException("typecheck")
            }
            val inc = when (incVal) {
                is Value.PSInt -> incVal.value
                is Value.PSReal -> incVal.value.toLong()
                else -> throw PostScriptException("typecheck")
            }
            val limit = when (limitVal) {
                is Value.PSInt -> limitVal.value
                is Value.PSReal -> limitVal.value.toLong()
                else -> throw PostScriptException("typecheck")
            }
            var x = init
            if (inc == 0L) throw PostScriptException("rangecheck")
            if (inc > 0) {
                while (x <= limit) {
                    i.operandStack.push(Value.PSInt(x))
                    i.executeProcedure(proc)
                    x += inc
                }
            } else {
                while (x >= limit) {
                    i.operandStack.push(Value.PSInt(x))
                    i.executeProcedure(proc)
                    x += inc
                }
            }
        }

        interp.registerOperator("quit") { i ->
            i.requestQuit = true
        }

        // I/O, Debugging
        interp.registerOperator("print") { i ->
            val v = i.operandStack.pop()
            if (v is Value.PSString) print(v.chars) else print(v.toString())
        }

        interp.registerOperator("=") { i ->
            val v = i.operandStack.pop()
            println(v.toString())
        }

        interp.registerOperator("==") { i ->
            val v = i.operandStack.pop()
            println("== ${v::class.simpleName}: ${v.toString()}")
        }

        interp.registerOperator("stack") { i ->
            val list = i.operandStack.toList()
            for (v in list) println(v.toString())
        }

        interp.registerOperator("pstack") { i ->
            val list = i.operandStack.toList()
            for (v in list) println(v.toString())
        }
    }
}