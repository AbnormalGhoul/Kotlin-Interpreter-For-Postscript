package postscript

/**
 * Core function:
 *  - Program entrypoint. Parses command line args to choose scoping mode and starts the REPL.
 */

fun main(args: Array<String>) {
    val lexical = args.contains("--lexical")
    val interp = Interpreter(lexical = lexical)

    println("PostScript Kotlin (line-oriented). Scoping: ${if (lexical) "lexical" else "dynamic"}")
    println("Enter one token per line. Use '{' and '}' on separate lines to create procedures.")
    println("Type 'quit' to exit.")
    interp.repl()
}