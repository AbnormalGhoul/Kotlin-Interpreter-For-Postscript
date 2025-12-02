package postscript

/**
 * Core function:
 *  - Implements the postscript.main interpreter runtime: operand stack, dict stack, operator registry, evaluation model, and REPL
 *  - Supports dynamic scoping (by default) and lexical scoping (if the `lexical` flag is true),
 *  `def` captures a proc's lexical environment and procedures with captured envs are executed under that snapshot
 *
 * Key behaviors:
 *  - `evalToken` pushes literals and resolves executable names:
 *      - First check registered native operators
 *      - Then check dictstack for a definition
 *      - If definition is a procedure: execute it (dynamic or lexical depending on settings)
 */

class PostScriptException(message: String) : RuntimeException(message)

class Interpreter(val lexical: Boolean = false) {
    val operandStack = OperandStack()
    val dictStack = DictStack()
    private val operators = mutableMapOf<String, (Interpreter) -> Unit>()
    private val parser = Parser()
    var requestQuit: Boolean = false

    init {
        Builtins.registerAll(this)
    }

    // Registers operator name and native action
    // Also creates a name in the current userdict for callers that use 'where' / 'load' instructions
    fun registerOperator(name: String, action: (Interpreter) -> Unit) {
        operators[name] = action
        // define a token name in userdict so 'where' can see it
        dictStack.define(name, Value.PSName(name, executable = true))
    }

    // Evaluate an incoming postscript.Value token (literal or executable name)
    fun evalToken(token: Value) {
        when (token) {
            is Value.PSInt, is Value.PSReal, is Value.PSBool, is Value.PSString, is Value.PSArray, is Value.PSDict -> {
                // push literal values
                operandStack.push(token)
            }
            is Value.PSProc -> {
                // when encountering a procedure literal, push as a value (not automatically executed)
                operandStack.push(token)
            }
            is Value.PSName -> {
                // literal name (executable=false) then push name object
                if (!token.executable) {
                    operandStack.push(token)
                    return
                }
                val name = token.name
                // first check for native operator
                val native = operators[name]
                if (native != null) {
                    native(this)
                    return
                }
                // try dictstack lookup (dynamic)
                val value = dictStack.lookup(name)
                if (value != null) {
                    if (value is Value.PSProc) {
                        // if lexical scoping and this proc was captured, execute with lexical env
                        if (lexical && value.lexicalEnv != null) {
                            executeProcedureWithLexicalEnv(value)
                        } else {
                            executeProcedure(value)
                        }
                    } else {
                        // non-proc value -> push
                        operandStack.push(value)
                    }
                    return
                }
                throw PostScriptException("undefined: $name")
            }
            else -> throw PostScriptException("unhandled token: $token")
        }
    }

    // Executes a PSProc using current dictstack (dynamic semantics)
    // The proc.tokens list contains token-lines which will each be parsed by the parser
    fun executeProcedure(proc: Value.PSProc) {
        for (t in proc.tokens) {
            // parseLine expects one token per line
            val objs = parser.parseLine(t)
            for (o in objs) {
                evalToken(o)
                if (requestQuit) return
            }
            if (requestQuit) return
        }
    }

    /**
     * Executes a PSProc using its captured lexical environment (proc.lexicalEnv)
     * Implementation approach:
     *  - Saves current dictstack snapshot
     *  - Replaces dictstack with proc.lexicalEnv snapshot
     *  - Executes procedure
     *  - Restores original dictstack
     */
    fun executeProcedureWithLexicalEnv(proc: Value.PSProc) {
        val env = proc.lexicalEnv ?: throw PostScriptException("internal")
        val old = dictStack.snapshot()
        try {
            dictStack.replaceWith(env)
            executeProcedure(proc)
        } finally {
            dictStack.replaceWith(old)
        }
    }

    // REPL: reads one token-per-line, parseLine returns Values (or empty) which are evaluated
    fun repl() {
        val reader = java.io.BufferedReader(java.io.InputStreamReader(System.`in`))
        requestQuit = false
        while (!requestQuit) {
            try {
                // Print prompt and flush so it always appears before user types
                print(">> ")
                System.out.flush()

                val line = reader.readLine() ?: break  // exit REPL
                // Ignore empty lines (but still show next prompt)
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    continue
                }

                // Parse and evaluate tokens returned for this single input line
                val vals = parser.parseLine(line)
                for (v in vals) {
                    evalToken(v)
                    if (requestQuit) break
                }
            } catch (ex: PostScriptException) {
                // Print PostScript-style short error but keep REPL running
                println("Error: ${ex.message}")
            } catch (ex: Exception) {
                // For unexpected exceptions, print stacktrace then continue
                println("Unhandled: ${ex.message}")
                ex.printStackTrace()
            }
        }
        println("Exiting interpreter.")
    }
}