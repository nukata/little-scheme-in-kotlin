// A Little Scheme in Kotlin 1.3, v0.2 R02.04.07 by SUZUKI Hisao
package little_scheme

// Cons cell
class Cell(val car: Any?, var cdr: Any?): Sequence<Any?> {
    override fun toString() = "($car . $cdr)"

    // Yield car, cadr, caddr and so on.
    override fun iterator() = object: Iterator<Any?> {
        var j: Any? = this@Cell

        override fun hasNext() = (if (j === null) false
                                  else if (j is Cell) true
                                  else throw ImproperListException(j!!))

        override fun next(): Any? {
            val c = j as Cell
            j = c.cdr
            return c.car
        }
    }
    // N.B. sequence { ... yield(j.car) ... }.iterator() is slow.

    // Length of the list
    val size: Int get() = sumBy {1}
}

// The last tail of the list is not null.
data class ImproperListException(val tail: Any): RuntimeException()

// Get the first element of x as a list.
fun fst(x: Cell?): Any? = x!!.car

// Get the second element of x as a list.
fun snd(x: Cell?): Any? = (x!!.cdr as Cell).car

//----------------------------------------------------------------------

// Scheme's symbol
class Sym private constructor(val name: String) {
    override fun toString() = name

    companion object {
        private val symbols: MutableMap<String, Sym> = HashMap()
 
        // Retrieve or construct an interned symbol.
        fun of(name: String): Sym = symbols.getOrPut(name) { Sym(name) }
    }
}

val QUOTE = Sym.of("quote")
val IF = Sym.of("if")
val BEGIN = Sym.of("begin")
val LAMBDA = Sym.of("lambda")
val DEFINE = Sym.of("define")
val SETQ = Sym.of("set!")
val APPLY = Sym.of("apply")
val CALLCC = Sym.of("call/cc")

//----------------------------------------------------------------------

// Linked list of bindings which map symbols to values
class Env(val sym: Sym?, var value: Any?, var next: Env?): Sequence<Env> {
    // Yield each binding.
    override fun iterator() = object: Iterator<Env> {
        var env: Env? = this@Env

        override fun hasNext() = (env !== null)

        override fun next(): Env {
            val current = env!!
            env = env!!.next
            return current
        }
    }
    // N.B. sequence<env> { ... yield(env) ... }.iterator() is slow.

    // Search the binding for a symbol.
    fun lookFor(symbol: Sym): Env =
        find { it.sym === symbol } ?: throw RuntimeException(
            "name not found: $symbol")

    // Build a new environment by prepending the bindings of symbols and data
    // to the present environment.
    fun prependDefs(symbols: Cell?, data_: Cell?): Env =
        if (symbols === null) {
            if (data_ !== null) {
                throw RuntimeException("surplus arg: ${stringify(data_)}")
            }
            this
        } else {
            if (data_ === null) {
                throw RuntimeException("surplus param: ${stringify(symbols)}")
            }
            Env(symbols.car as Sym, data_.car,
                prependDefs(symbols.cdr as Cell?, data_.cdr as Cell?))
        }
}

//----------------------------------------------------------------------

// Operations in continuations
enum class ContOp {
    THEN, BEGIN, DEFINE, SETQ, APPLY, APPLY_FUN, EVAL_ARG, CONS_ARGS,
    RESTORE_ENV
}

// Scheme's step in a continuation
typealias Step = Pair<ContOp, Any?>

// Scheme's continuation as a stack of steps
class Continuation() {
    private val stack: ArrayList<Step> = ArrayList()

    // Copy the steps from another continuation.
    constructor(other: Continuation): this() {
        stack.addAll(other.stack)
    }

    // Copy the steps from another continuation.
    fun copyFrom(other: Continuation) {
        stack.clear()
        stack.addAll(other.stack)
    }

    // Return true if the continuation is empty.
    fun isEmpty(): Boolean = stack.isEmpty()

    // Length of the continuation
    val size: Int get() = stack.size

    // Return a quasi-stack trace.
    override fun toString(): String {
        val ss = ArrayList<String>()
        for ((op, value) in stack) {
            ss.add("${op} ${stringify(value)}")
        }
        return "#<${ss.joinToString("\n\t ")}>"
    }

    // Append a step to the tail of the continuation.
    fun push(op: ContOp, value: Any?) = stack.add(Step(op, value))

    // Pop a step from the tail of the continuation.
    fun pop(): Step = stack.removeAt(stack.size - 1)

    // Push RESTORE_ENV unless on a tail call.
    fun pushRestoreEnv(env: Env) {
        val len = stack.size
        if (len == 0 || stack[len - 1].first !== ContOp.RESTORE_ENV) {
            push(ContOp.RESTORE_ENV, env)
        }
    }
}

//----------------------------------------------------------------------

// Lambda expression with its environment
class Closure(val params: Cell?, val body: Cell, val env: Env) {
    override fun toString() =
        "#<${stringify(params)}:${stringify(body)}:${stringify(env)}>"
}
    
// Body of a built-in function
typealias IntrinsicBody = (Cell?) -> Any?

// Built-in function
class Intrinsic(val name: String, val arity: Int, val func: IntrinsicBody) {
    override fun toString() = "#<${name}:${arity}>"
}

// Exception thrown by error procedure of SRFI-23
class ErrorException(reason: Any?, arg: Any?): RuntimeException(
    "Error: ${stringify(reason, false)}: ${stringify(arg)}")

//----------------------------------------------------------------------

// A unique value which means the expression has no value
object NONE {
    override fun toString() = "#<VOID>"
}

// A unique value which means the End Of File
object EOF {
    override fun toString() = "#<EOF>"
}

// A unique value which represents the call/cc procedure
object CALLCC_VAL {
    override fun toString() = "#<call/cc>"
}

// A unique value which represents the apply procedure
object APPLY_VAL {
    override fun toString() = "#<apply>"
}

// Convert an expression to a string.
fun stringify(exp: Any?, quote: Boolean = true): String =
    when (exp) {
        true -> "#t"
        false -> "#f"
        null -> "()"
        is Cell -> {
            val ss = ArrayList<String>()
            try {
                for (e in exp) {
                    ss.add(stringify(e, quote))
                }
            } catch (ex: ImproperListException) {
                ss.add(".")
                ss.add(stringify(ex.tail, quote))
            }
            "(${ss.joinToString(" ")})"
        }
        is Env -> {
            val ss = ArrayList<String>()
            for (env in exp) {
                if (env === GLOBAL_ENV) {
                    ss.add("GlobalEnv")
                    break
                } else if (env.sym === null) {
                    ss.add("|") //frame marker
                } else {
                    ss.add(env.sym.toString())
                }
            }
            "#<${ss.joinToString(" ")}>"
        }
        is String -> if (quote) "\"${exp}\"" else exp
        else -> "${exp}"
    }

//----------------------------------------------------------------------

// Return a list of symbols of the global environment.
fun globals(): Cell? {
    var j: Cell? = null;
    val env = GLOBAL_ENV.next   // Skip the frame marker.
    for (e in env!!) {
        j = Cell(e.sym, j)
    }
    return j
}

private fun c(name: String, arity: Int, func: IntrinsicBody, next: Env?) =
    Env(Sym.of(name), Intrinsic(name, arity, func), next)

private val G1 =
    c("+", 2, { add(fst(it) as Number, snd(it) as Number) },
      c("-", 2, { subtract(fst(it) as Number, snd(it) as Number) },
        c("*", 2, { multiply(fst(it) as Number, snd(it) as Number) },
          c("<", 2, { compare(fst(it) as Number, snd(it) as Number) < 0 },
            c("=", 2, { compare(fst(it) as Number, snd(it) as Number) == 0 },
              c("number?", 1, { fst(it) is Number },
                c("error", 2, { throw ErrorException(fst(it), snd(it)) },
                  c("globals", 0, { globals() },
                    null))))))))

// Scheme's global environment
val GLOBAL_ENV = Env(
    null,                       // frame marker
    null,
    c("car", 1, { (fst(it) as Cell).car },
      c("cdr", 1, { (fst(it) as Cell).cdr },
        c("cons", 2, { Cell(fst(it), snd(it)) },
          c("eq?", 2, { fst(it) === snd(it) },
            c("pair?", 1, { fst(it) is Cell },
              c("null?", 1, { fst(it) === null },
                c("not", 1, { fst(it) == false },
                  c("list", -1, { it },
                    c("display", 1, { print(stringify(fst(it), false)); NONE },
                      c("newline", 0, { println(); NONE },
                        c("read", 0, { readExpression("", "") },
                          c("eof-object?", 1, { fst(it) === EOF },
                            c("symbol?", 1, { fst(it) is Sym },
                              Env(CALLCC, CALLCC_VAL,
                                  Env(APPLY, APPLY_VAL,
                                      G1))))))))))))))))

//----------------------------------------------------------------------

// Evaluate an expression in an environment.
fun evaluate(expression: Any?, environment: Env): Any? {
    var exp = expression
    var env = environment
    var k = Continuation()
    try {
        while (true) {
            Loop1@
            while (true) when (exp) {
                is Cell -> {
                    val kar = exp.car
                    val kdr = exp.cdr as Cell?
                    when (kar) {
                        // (quote e)
                        QUOTE -> {
                            exp = fst(kdr)
                            break@Loop1
                        }
                        // (if e1 e2 [e3])
                        IF -> {
                            exp = fst(kdr)
                            k.push(ContOp.THEN, kdr!!.cdr)
                        }
                        // (begin e...)
                        BEGIN -> {
                            exp = fst(kdr)
                            val tl = kdr!!.cdr
                            if (tl !== null) {
                                k.push(ContOp.BEGIN, tl)
                            }
                        }
                        // (lambda (v...) e...)
                        LAMBDA -> {
                            val params = fst(kdr) as Cell?
                            val body = kdr!!.cdr as Cell
                            exp = Closure(params, body, env)
                            break@Loop1
                        }
                        // (define v e)
                        DEFINE -> {
                            exp = snd(kdr)
                            k.push(ContOp.DEFINE, fst(kdr))
                        }
                        // (set! v e)
                        SETQ -> {
                            exp = snd(kdr)
                            val v = fst(kdr) as Sym
                            k.push(ContOp.SETQ, env.lookFor(v))
                        }
                        // (fun arg...)
                        else -> {
                            exp = kar
                            k.push(ContOp.APPLY, kdr)
                        }
                    }
                }
                is Sym -> {
                    exp = env.lookFor(exp).value
                    break@Loop1
                }
                else -> break@Loop1 // #t, #f, 0, 1, 2 etc.
            }
            Loop2@
            while (true) {
                // print("_${k.size}")
                if (k.isEmpty()) {
                    return exp
                }
                val (op, x) = k.pop()
                when (op) {
                    ContOp.THEN -> {
                        // x is (e2) or (e2 e3).
                        val j = x as Cell
                        if (exp == false) {
                            if (j.cdr === null) {
                                exp = NONE
                            } else {
                                exp = snd(j) //e3
                                break@Loop2
                            }
                        } else {
                            exp = j.car
                            break@Loop2
                        }
                    }
                    ContOp.BEGIN -> {
                        // x is (e...).
                        val j = x as Cell
                        // Unless on a tail call...
                        if (j.cdr !== null) {
                            k.push(ContOp.BEGIN, j.cdr)
                        }
                        exp = j.car
                        break@Loop2
                    }
                    ContOp.DEFINE -> {
                        // x is a variable symbol.
                        assert(env.sym === null) //Check for the frame top
                        env.next = Env(x as Sym, exp, env.next)
                        exp = NONE
                    }
                    ContOp.SETQ -> {
                        // x is an Env.
                        (x as Env).value = exp
                        exp = NONE
                    }
                    ContOp.APPLY -> {
                        // x is a list of args; exp is a function.
                        if (x === null) {
                            val r = applyFunction(exp, null, k, env)
                            exp = r.first
                            env = r.second
                        } else {
                            k.push(ContOp.APPLY_FUN, exp)
                            var j = x as Cell
                            while (j.cdr !== null) {
                                k.push(ContOp.EVAL_ARG, j.car)
                                j = j.cdr as Cell
                            }
                            exp = j.car
                            k.push(ContOp.CONS_ARGS, null)
                            break@Loop2
                        }
                    }
                    ContOp.CONS_ARGS -> {
                        // x is a list of evaluated args (to be cdr);
                        // exp is a newly evaluated arg (to be car).
                        val args = Cell(exp, x)
                        val (op2, x2) = k.pop()
                        when (op2) {
                            ContOp.EVAL_ARG -> {
                                // x2 is the next arg.
                                exp = x2
                                k.push(ContOp.CONS_ARGS, args)
                                break@Loop2
                            }
                            ContOp.APPLY_FUN -> {
                                // x2 is a function.
                                val r = applyFunction(x2, args, k, env)
                                exp = r.first
                                env = r.second
                            }
                            else -> throw RuntimeException(
                                "unexpected op: ${op}")
                        }
                    }
                    ContOp.RESTORE_ENV -> {
                        // x is an Env.
                        env = x as Env
                    }
                    else -> throw RuntimeException("bad op: ${op}")
                }
            }
        }
    } catch (ex: ErrorException) {
        throw ex
    } catch (ex: Exception) {
        if (k.isEmpty()) {
            throw ex
        }
        throw RuntimeException("${ex.message}\n\t${stringify(k)}", ex)
    }
}

// Apply a function to arguments with a continuation and an environment.
fun applyFunction(function: Any?, arguments: Cell?,
                  k: Continuation, env: Env): Pair<Any?, Env> {
    var fnc = function
    var arg = arguments
    while (true) {
        if (fnc === CALLCC_VAL) {
            k.pushRestoreEnv(env)
            fnc = fst(arg)
            arg = Cell(Continuation(k), null)
        } else if (fnc === APPLY_VAL) {
            fnc = fst(arg)
            arg = snd(arg) as Cell?
        } else {
            break
        }
    }
    when (fnc) {
        is Intrinsic -> {
            if (fnc.arity >= 0 &&
                if (arg === null) fnc.arity > 0 else (arg.size != fnc.arity)) {
                throw RuntimeException(
                    "arity not matched: ${fnc} and ${stringify(arg)}")
            }
            val result = fnc.func(arg)
            return Pair(result, env)
        }
        is Closure -> {
            k.pushRestoreEnv(env)
            k.push(ContOp.BEGIN, fnc.body)
            return Pair(NONE,
                        Env(null, // frame marker
                            null,
                            fnc.env.prependDefs(fnc.params, arg)))
        }
        is Continuation -> {
            k.copyFrom(fnc)
            return Pair(fst(arg), env)
        }
        else -> throw RuntimeException(
            "not a function: ${stringify(fnc)} with ${stringify(arg)}")
    }
}

//----------------------------------------------------------------------

// Split a string into a sequence of tokens.
// For "(a 1)", it yields ["(", "a", "1", ")"].
fun splitStringIntoTokens(source: String) = sequence<String> {
    for (line in source.lineSequence()) {
        val ss = ArrayList<String>()
        val x = ArrayList<String>()
        var i = true
        for (e in line.splitToSequence('"')) {
            if (i) {
                x.add(e)
            } else {
                ss.add("\"" + e) //Store a string literal.
                x.add("#s")
            }
            i = !i
        }
        var s = x.joinToString(" ").split(';')[0] //Igore ;-comment.
        s = s.replace("'", " ' ")
        s = s.replace("(", " ( ")
        s = s.replace(")", " ) ")
        for (e in s.splitToSequence(' ', '\t', '\u000b', '\u000c')) {
            if (e == "#s") {
                yield(ss.removeAt(0))
            } else if (! e.isEmpty()) {
                yield(e)
            }
        }
    }
}

// Read an expression from tokens.
// Tokens will be left with the rest of the token strings if any.
fun readFromTokens(tokens: MutableList<String>): Any? {
    val token = tokens.removeAt(0)
    when (token) {
        "(" -> {
            val z = Cell(null, null)
            var y = z
            while (tokens[0] != ")") {
                if (tokens[0] == ".") {
                    tokens.removeAt(0)
                    y.cdr = readFromTokens(tokens)
                    if (tokens[0] != ")")
                        throw RuntimeException(") is expected")
                    break
                }
                val e = readFromTokens(tokens)
                val x = Cell(e, null)
                y.cdr = x
                y = x
            }
            tokens.removeAt(0)
            return z.cdr
        }
        ")" -> throw RuntimeException("unexpected )")
        "'" -> {
            val e = readFromTokens(tokens)
            return Cell(QUOTE, Cell(e, null)) // 'e => (quote e)
        }
        "#f" -> return false
        "#t" -> return true
    }
    if (token[0] == '"') {
        return token.substring(1)
    }
    return parseAsNumber(token) ?: Sym.of(token)
}

//----------------------------------------------------------------------

// Tokens from the standard-in
private var stdInTokens = ArrayList<String>()

// Read an expression from the standard-in.
fun readExpression(prompt1: String, prompt2: String): Any? {
    while (true) {
        var old = ArrayList<String>(stdInTokens)
        try {
            return readFromTokens(stdInTokens)
        } catch (ex: IndexOutOfBoundsException) {
            print(if (old.isEmpty()) prompt1 else prompt2)
            val line = readLine()
            if (line === null) {
                return EOF
            }
            stdInTokens = old
            for (token in splitStringIntoTokens(line)) {
                stdInTokens.add(token)
            }
        } catch (ex: Exception) {
            stdInTokens.clear() //Discard the erroneous tokens.
            throw ex
        }
    }
}

// Repeat Read-Eval-Print until End-Of-File.
fun readEvalPrintLoop() {
    while (true) {
        var result: Any?
        try {
            val exp = readExpression("> ", "| ")
            if (exp === EOF) {
                println("Goodbye")
                return
            }
            result = evaluate(exp, GLOBAL_ENV)
        } catch (ex: RuntimeException) {
            println(ex.message)
            result = NONE
        }
        if (result !== NONE) {
            println(stringify(result))
        }
    }
}
