package postscript

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The tests include:
 *  - basic arithmetic and stack operators
 *  - def / load lookup behavior
 *  - array creation + length
 *  - error handling (stackunderflow / typecheck)
 *  - dynamic vs lexical scoping behavior
 *
 * The tests use Interpreter.evalToken(...) directly to push tokens (Value objects)
 */

class InterpreterTests {

    // small helper: feed a sequence of Value tokens into the interpreter
    private fun runTokens(interp: Interpreter, tokens: List<Value>) {
        for (t in tokens) interp.evalToken(t)
    }

    @Test
    fun testAddSubMul() {
        val interp = Interpreter()
        // 2 3 add = 5
        runTokens(interp, listOf(Value.PSInt(2), Value.PSInt(3), Value.PSName("add")))
        val top1 = interp.operandStack.pop()
        assertEquals(Value.PSInt(5), top1)

        // 10 4 sub = 6
        runTokens(interp, listOf(Value.PSInt(10), Value.PSInt(4), Value.PSName("sub")))
        val top2 = interp.operandStack.pop()
        assertEquals(Value.PSInt(6), top2)

        // 6 7 mul = 42
        runTokens(interp, listOf(Value.PSInt(6), Value.PSInt(7), Value.PSName("mul")))
        val top3 = interp.operandStack.pop()
        assertEquals(Value.PSInt(42), top3)
    }

    @Test
    fun testDupExchPopCount() {
        val interp = Interpreter()
        runTokens(interp, listOf(Value.PSInt(4), Value.PSName("dup")))
        assertEquals(2, interp.operandStack.count())
        val a = interp.operandStack.pop()
        val b = interp.operandStack.pop()
        assertEquals(a, b)
        // exch
        runTokens(interp, listOf(Value.PSInt(1), Value.PSInt(2), Value.PSName("exch")))
        val top = interp.operandStack.pop()
        val below = interp.operandStack.pop()
        // after exch, top should be 1 then 2
        assertEquals(Value.PSInt(1), top)
        assertEquals(Value.PSInt(2), below)
    }

    @Test
    fun testDefAndLoad() {
        val interp = Interpreter()
        // /x 10 def
        runTokens(interp, listOf(Value.PSName("x", executable = false), Value.PSInt(10), Value.PSName("def")))
        // pushing x should push 10
        interp.evalToken(Value.PSName("x"))
        val top = interp.operandStack.pop()
        assertEquals(Value.PSInt(10), top)
    }

    @Test
    fun testArrayAndLength() {
        val interp = Interpreter()
        // 5 array should return an array of length 5
        runTokens(interp, listOf(Value.PSInt(5), Value.PSName("array")))
        val arr = interp.operandStack.pop()
        assertTrue(arr is Value.PSArray)
        // push array back and call length
        interp.operandStack.push(arr)
        interp.evalToken(Value.PSName("length"))
        val len = interp.operandStack.pop()
        assertEquals(Value.PSInt(5), len)
    }

    @Test
    fun testStackUnderflowForAdd() {
        val interp = Interpreter()
        // calling add with insufficient items must throw PostScriptException (stackunderflow)
        // we assert that an exception occurs when invoking the operator
        assertFailsWith<PostScriptException> {
            interp.evalToken(Value.PSName("add"))
        }
    }

    @Test
    fun testDynamicScopingBehavior() {
        val i = Interpreter(lexical = false) // dynamic scoping
        // /x 1 def
        runTokens(i, listOf(Value.PSName("x", executable = false), Value.PSInt(1), Value.PSName("def")))
        // /p { x } def
        val pProc = Value.PSProc(listOf("x"))
        runTokens(i, listOf(Value.PSName("p", executable = false), pProc, Value.PSName("def")))
        // /x 2 def
        runTokens(i, listOf(Value.PSName("x", executable = false), Value.PSInt(2), Value.PSName("def")))
        // pushing p should push 2 under dynamic scoping
        i.evalToken(Value.PSName("p"))
        val dynResult = i.operandStack.pop()
        assertEquals(Value.PSInt(2), dynResult)
    }

    @Test
    fun testLexicalScopingBehavior() {
        val i = Interpreter(lexical = true) // lexical scoping
        // /x 1 def
        runTokens(i, listOf(Value.PSName("x", executable = false), Value.PSInt(1), Value.PSName("def")))
        // /p { x } def   (def captures lexical env when interpreter.lexical == true)
        val pProc = Value.PSProc(listOf("x"))
        runTokens(i, listOf(Value.PSName("p", executable = false), pProc, Value.PSName("def")))
        // /x 2 def
        runTokens(i, listOf(Value.PSName("x", executable = false), Value.PSInt(2), Value.PSName("def")))
        // pushing p should push 1 under lexical scoping
        i.evalToken(Value.PSName("p"))
        val lexResult = i.operandStack.pop()
        assertEquals(Value.PSInt(1), lexResult)
    }

    @Test
    fun testIfIfElseRepeat() {
        val interp = Interpreter()
        // test if: true { 5 } if -> pushes 5
        val proc5 = Value.PSProc(listOf("5"))
        runTokens(interp, listOf(Value.PSBool(true), proc5, Value.PSName("if")))
        assertEquals(Value.PSInt(5), interp.operandStack.pop())

        // test ifelse: false {1} {2} ifelse -> push 2
        val p1 = Value.PSProc(listOf("1"))
        val p2 = Value.PSProc(listOf("2"))
        runTokens(interp, listOf(Value.PSBool(false), p1, p2, Value.PSName("ifelse")))
        assertEquals(Value.PSInt(2), interp.operandStack.pop())

        // test repeat: 3 { 7 } repeat -> pushes 7 three times
        val p7 = Value.PSProc(listOf("7"))
        runTokens(interp, listOf(Value.PSInt(3), p7, Value.PSName("repeat")))
        // check last three are 7
        val a = interp.operandStack.pop()
        val b = interp.operandStack.pop()
        val c = interp.operandStack.pop()
        assertEquals(Value.PSInt(7), a)
        assertEquals(Value.PSInt(7), b)
        assertEquals(Value.PSInt(7), c)
    }
}
