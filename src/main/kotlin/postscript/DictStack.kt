package postscript

/**
 * Core function:
 *  - Implements the PostScript dictionary stack abstraction (begin/end, define, lookup, where, snapshot/restore)
 *  - Provides initial seed dictionaries: systemdict (bottom) and userdict (top)
 *
 * Notes:
 *  - PostScript names are stored without leading '/' in the dictionaries.
 *  - `snapshot()` returns a live list of dict objects (deep copy not made), callers should be careful not to mutate
 *    captured dicts if they expect immutability. For our lexical capture we store references; restoring reuses those dicts.
 */

typealias PostScriptDict = Value.PSDict

class DictStack {
    // internal stack; bottom is first element, top is last element
    private val stack = ArrayDeque<PostScriptDict>()

    init {
        // Simple initial setup: systemdict, userdict
        val system = Value.PSDict(mutableMapOf())
        val user = Value.PSDict(mutableMapOf())
        stack.addLast(system)
        stack.addLast(user)
    }

    fun begin(d: PostScriptDict) = stack.addLast(d)

    fun end() {
        if (stack.size <= 1) throw PostScriptException("dictstackunderflow")
        stack.removeLast()
    }

    // define in topmost (current) dict
    fun define(name: String, value: Value) {
        stack.last().map[name] = value
    }

    // store: find the topmost dict that contains name and replace; if none, error
    fun store(name: String, value: Value) {
        val w = where(name) ?: throw PostScriptException("undefined")
        w.map[name] = value
    }

    // find topmost dict that contains name
    fun where(name: String): PostScriptDict? {
        for (d in stack.asReversed()) {
            if (d.map.containsKey(name)) return d
        }
        return null
    }

    // lookup value by name searching top to bottom
    fun lookup(name: String): Value? {
        for (d in stack.asReversed()) {
            val v = d.map[name]
            if (v != null) return v
        }
        return null
    }

    fun count(): Int = stack.size

    fun snapshot(): List<PostScriptDict> = stack.toList()

    // Replace internal stack with the provided snapshot list (useful for lexical execution).
    // This simply uses the same dict objects in the list.
    fun replaceWith(snapshot: List<PostScriptDict>) {
        stack.clear()
        snapshot.forEach { stack.addLast(it) }
    }

    // returns copy of stack as a list in bottom-to-top order
    fun asList(): List<PostScriptDict> = stack.toList()

    // convenience: create an empty dict and push to top
    fun pushNewDict(): PostScriptDict {
        val d = Value.PSDict(mutableMapOf())
        begin(d)
        return d
    }

    // Put useful builtins into system/user dicts if desired (optional helper)
    fun seed(name: String, value: Value) {
        // put into bottom-most (system) dict
        val bottom = stack.first()
        bottom.map[name] = value
    }
}