// A Little Scheme in Kotlin 1.3 (dependent on JVM) R02.04.07 by SUZUKI Hisao
package little_scheme

import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Paths

// Convert a Long into an Int or a BigInteger.
fun normalize(x: Long): Number {
    val i: Int = x.toInt()
    if (i.compareTo(x) == 0) {
        return i
    } else {
        return x.toBigInteger()
    }
}

// Convert a BigInteger into an Int if possible.
fun normalize(x: BigInteger): Number {
    if (x.bitLength() < 32) {
        return x.toInt()
    } else {
        return x
    }
}

// x + y
fun add(x: Number, y: Number): Number {
    if (x is Int && y is Int) {
        val a: Long = x.toLong() + y.toLong()
        return normalize(a)
    } else if (x is Double || y is Double) {
        return x.toDouble() + y.toDouble()
    } else {
        var a = x
        var b = y
        if (a is Int) {
            a = a.toBigInteger()
        } else if (b is Int) {
            b = b.toBigInteger()
        }
        val c: BigInteger = (a as BigInteger).add(b as BigInteger)
        return normalize(c)
    }
}

// x - y
fun subtract(x: Number, y: Number): Number {
    if (x is Int && y is Int) {
        val a: Long = x.toLong() - y.toLong()
        return normalize(a)
    } else if (x is Double || y is Double) {
        return x.toDouble() - y.toDouble()
    } else {
        var a = x
        var b = y
        if (a is Int) {
            a = a.toBigInteger()
        } else if (b is Int) {
            b = b.toBigInteger()
        }
        val c: BigInteger = (a as BigInteger).subtract(b as BigInteger)
        return normalize(c)
    }
}

// x * y
fun multiply(x: Number, y: Number): Number {
    if (x is Int && y is Int) {
        val a: Long = x.toLong() * y.toLong()
        return normalize(a)
    } else if (x is Double || y is Double) {
        return x.toDouble() * y.toDouble()
    } else {
        var a = x
        var b = y
        if (a is Int) {
            a = a.toBigInteger()
        } else if (b is Int) {
            b = b.toBigInteger()
        }
        val c: BigInteger = (a as BigInteger).multiply(b as BigInteger)
        return normalize(c)
    }
}

// x <=> y
// It returns -1, 0 or 1 as x is less than, equal to, or greater than y.
fun compare(x: Number, y: Number): Int {
    if (x is Int && y is Int) {
        val a: Long = x.toLong() - y.toLong()
        return if (a < 0) -1 else if (a > 0) 1 else 0
    } else if (x is Double || y is Double) {
        val a: Double = x.toDouble() - y.toDouble()
        return if (a < 0.0) -1 else if (a > 0.0) 1 else 0
    } else {
        var a = x
        var b = y
        if (a is Int) {
            a = a.toBigInteger()
        } else if (b is Int) {
            b = b.toBigInteger()
        }
        return (a as BigInteger).compareTo(b as BigInteger)
    }
}

// Parse a string as an Int, a BigInteger or a Double.
fun parseAsNumber(s: String): Number? {
    val i = s.toIntOrNull()
    if (i !== null) return i
    val b = s.toBigIntegerOrNull()
    if (b !== null) return b
    return s.toDoubleOrNull()
}

//----------------------------------------------------------------------

// Load a source code from a file.
fun load(fileName: String) {
    val bytes = Files.readAllBytes(Paths.get(fileName))
    val source = String(bytes) // Convert bytes to a sring using UTF-8.
    val tokens = splitStringIntoTokens(source).toMutableList()
    while (! tokens.isEmpty()) {
        val exp = readFromTokens(tokens)
        evaluate(exp, GLOBAL_ENV)
    }
}

fun main(args: Array<String>) {
    if (args.size > 0) {
        load(args[0])
        if (args.size == 1 || args[1] != "-") {
            return
        }
    }
    readEvalPrintLoop()
}
