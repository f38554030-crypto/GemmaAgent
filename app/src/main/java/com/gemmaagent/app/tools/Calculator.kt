package com.gemmaagent.app.tools

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.pow

/**
 * Kalkulator & konverter lokal (fitur 21) agar hasil angka presisi tanpa halusinasi LLM.
 * Mendukung: + - * / ^ %, kurung, sqrt, sin, cos, tan, log, ln, abs, round, pi, e.
 */
object Calculator {

    data class CalcResult(val expression: String, val value: String)

    private val pureExpr = Regex("^[0-9\\s+\\-*/^().,%x×÷]+$")
    private val prefix = Regex("^(/hitung|hitung|berapa|calc|kalkulasi)\\s+", RegexOption.IGNORE_CASE)

    /** Mengembalikan hasil jika teks adalah perintah hitung / konversi; null jika bukan. */
    fun tryHandle(input: String): CalcResult? {
        val text = input.trim().removeSuffix("?").removeSuffix("=").trim()
        UnitConverter.tryConvert(text)?.let { return it }
        val explicit = prefix.containsMatchIn(text)
        val expr = text.replace(prefix, "").trim()
        // Ekspresi yang hanya berisi "-" (mis. nomor telepon 0812-3456) wajib diawali kata "hitung".
        val looksMath = pureExpr.matches(expr) && expr.any { it.isDigit() } &&
            (expr.any { it in "+*/^×÷x%" } || (explicit && expr.contains('-')))
        val looksFunc = explicit && Regex("(sqrt|sin|cos|tan|log|ln|abs|round|pi)", RegexOption.IGNORE_CASE).containsMatchIn(expr)
        if (!(looksMath || looksFunc)) return null
        return runCatching { CalcResult(expr, format(evaluate(expr))) }.getOrNull()
    }

    fun evaluate(raw: String): Double {
        var s = raw.replace("×", "*").replace("÷", "/").replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
        // Format Indonesia: "1.000,5" → 1000.5 ; "2,5" → 2.5
        s = if (s.contains(',') && s.contains('.')) s.replace(".", "").replace(',', '.') else s.replace(',', '.')
        val p = Parser(s.lowercase().replace(" ", ""))
        val v = p.parseExpression()
        if (p.pos < p.s.length) throw IllegalArgumentException("Karakter tidak dikenal: ${p.s[p.pos]}")
        if (v.isNaN() || v.isInfinite()) throw ArithmeticException("Hasil tidak terdefinisi")
        return v
    }

    fun format(v: Double): String {
        if (abs(v) >= 1e15 || (abs(v) < 1e-6 && v != 0.0)) return v.toString()
        val bd = BigDecimal(v).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        val plain = bd.toPlainString()
        val (intPart, frac) = plain.split('.').let { it[0] to it.getOrNull(1) }
        val neg = intPart.startsWith("-")
        val digits = intPart.removePrefix("-").reversed().chunked(3).joinToString(".").reversed()
        return (if (neg) "-" else "") + digits + (frac?.let { ",$it" } ?: "")
    }

    private class Parser(val s: String) {
        var pos = 0

        fun parseExpression(): Double {
            var v = parseTerm()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; v += parseTerm() }
                    '-' -> { pos++; v -= parseTerm() }
                    else -> return v
                }
            }
            return v
        }

        fun parseTerm(): Double {
            var v = parseFactor()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; v *= parseFactor() }
                    '/' -> {
                        pos++
                        val d = parseFactor()
                        if (d == 0.0) throw ArithmeticException("Pembagian dengan nol")
                        v /= d
                    }
                    else -> return v
                }
            }
            return v
        }

        fun parseFactor(): Double {
            val base = parseUnary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                return base.pow(parseFactor())
            }
            return base
        }

        fun parseUnary(): Double {
            if (pos < s.length && s[pos] == '-') { pos++; return -parseUnary() }
            if (pos < s.length && s[pos] == '+') { pos++; return parseUnary() }
            var v = parsePrimary()
            while (pos < s.length && s[pos] == '%') { pos++; v /= 100.0 }
            return v
        }

        fun parsePrimary(): Double {
            if (pos >= s.length) throw IllegalArgumentException("Ekspresi tidak lengkap")
            val c = s[pos]
            if (c == '(') {
                pos++
                val v = parseExpression()
                expect(')')
                return v
            }
            if (c.isDigit() || c == '.') {
                val start = pos
                while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
                return s.substring(start, pos).toDouble()
            }
            if (c.isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val name = s.substring(start, pos)
                return when (name) {
                    "pi" -> Math.PI
                    "e" -> Math.E
                    else -> {
                        expect('(')
                        val arg = parseExpression()
                        expect(')')
                        when (name) {
                            "sqrt", "akar" -> kotlin.math.sqrt(arg)
                            "sin" -> kotlin.math.sin(Math.toRadians(arg))
                            "cos" -> kotlin.math.cos(Math.toRadians(arg))
                            "tan" -> kotlin.math.tan(Math.toRadians(arg))
                            "log" -> kotlin.math.log10(arg)
                            "ln" -> kotlin.math.ln(arg)
                            "abs" -> abs(arg)
                            "round" -> kotlin.math.round(arg)
                            else -> throw IllegalArgumentException("Fungsi tidak dikenal: $name")
                        }
                    }
                }
            }
            throw IllegalArgumentException("Karakter tidak dikenal: $c")
        }

        fun expect(ch: Char) {
            if (pos >= s.length || s[pos] != ch) throw IllegalArgumentException("Kurang '$ch'")
            pos++
        }
    }
}

/** Konverter satuan: panjang, massa, volume, data, waktu, kecepatan, suhu. */
object UnitConverter {
    private data class U(val group: String, val factor: Double)

    private val units = mapOf(
        // panjang (meter)
        "mm" to U("len", 0.001), "cm" to U("len", 0.01), "m" to U("len", 1.0), "km" to U("len", 1000.0),
        "in" to U("len", 0.0254), "inch" to U("len", 0.0254), "inci" to U("len", 0.0254),
        "ft" to U("len", 0.3048), "kaki" to U("len", 0.3048), "yd" to U("len", 0.9144),
        "mi" to U("len", 1609.344), "mil" to U("len", 1609.344), "mile" to U("len", 1609.344),
        // massa (kg)
        "mg" to U("mass", 1e-6), "g" to U("mass", 0.001), "gram" to U("mass", 0.001), "ons" to U("mass", 0.1),
        "kg" to U("mass", 1.0), "ton" to U("mass", 1000.0), "lb" to U("mass", 0.45359237), "pon" to U("mass", 0.45359237),
        "oz" to U("mass", 0.028349523125),
        // volume (liter)
        "ml" to U("vol", 0.001), "l" to U("vol", 1.0), "liter" to U("vol", 1.0), "gal" to U("vol", 3.785411784),
        // data (byte)
        "b" to U("data", 1.0), "kb" to U("data", 1024.0), "mb" to U("data", 1024.0 * 1024),
        "gb" to U("data", 1024.0 * 1024 * 1024), "tb" to U("data", 1024.0 * 1024 * 1024 * 1024),
        // waktu (detik)
        "detik" to U("time", 1.0), "s" to U("time", 1.0), "menit" to U("time", 60.0), "min" to U("time", 60.0),
        "jam" to U("time", 3600.0), "h" to U("time", 3600.0), "hari" to U("time", 86400.0), "minggu" to U("time", 604800.0),
        // kecepatan (m/s)
        "m/s" to U("speed", 1.0), "km/jam" to U("speed", 1000.0 / 3600), "km/h" to U("speed", 1000.0 / 3600),
        "mph" to U("speed", 0.44704), "knot" to U("speed", 0.514444),
    )
    private val temps = setOf("c", "°c", "celsius", "f", "°f", "fahrenheit", "k", "kelvin")

    private val pattern = Regex(
        "^(?:konversi|ubah|convert)?\\s*(-?[\\d.,]+)\\s*([a-zA-Z°/]+)\\s+(?:ke|to|jadi|menjadi|=)\\s+([a-zA-Z°/]+)$",
        RegexOption.IGNORE_CASE,
    )

    fun tryConvert(text: String): Calculator.CalcResult? {
        val m = pattern.find(text.trim()) ?: return null
        val num = runCatching { Calculator.evaluate(m.groupValues[1]) }.getOrNull() ?: return null
        val from = m.groupValues[2].lowercase()
        val to = m.groupValues[3].lowercase()
        if (from in temps && to in temps) {
            val c = when (from.removePrefix("°").first()) { 'c' -> num; 'f' -> (num - 32) * 5 / 9; else -> num - 273.15 }
            val out = when (to.removePrefix("°").first()) { 'c' -> c; 'f' -> c * 9 / 5 + 32; else -> c + 273.15 }
            return Calculator.CalcResult("$num $from → $to", "${Calculator.format(out)} $to")
        }
        val a = units[from] ?: return null
        val b = units[to] ?: return null
        if (a.group != b.group) return null
        val out = num * a.factor / b.factor
        return Calculator.CalcResult("${Calculator.format(num)} $from → $to", "${Calculator.format(out)} $to")
    }
}
