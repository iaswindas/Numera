package com.numera.model.application

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class FormulaEngine {
    fun evaluate(formula: String, values: Map<String, BigDecimal?>): BigDecimal? {
        val parser = Parser(tokenize(formula), values)
        return parser.parseExpression().normalize()
    }

    private fun BigDecimal?.normalize(): BigDecimal? = this?.setScale(4, RoundingMode.HALF_UP)?.stripTrailingZeros()

    private enum class TokenType {
        NUMBER,
        ITEM,
        IDENT,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        LPAREN,
        RPAREN,
        COMMA,
        COLON,
        LT,
        GT,
        LE,
        GE,
        EQ,
        EOF,
    }

    private data class Token(val type: TokenType, val text: String)

    private fun tokenize(source: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < source.length) {
            when (val ch = source[i]) {
                ' ', '\t', '\n', '\r' -> i++
                '+' -> { tokens += Token(TokenType.PLUS, "+"); i++ }
                '-' -> { tokens += Token(TokenType.MINUS, "-"); i++ }
                '*' -> { tokens += Token(TokenType.STAR, "*"); i++ }
                '/' -> { tokens += Token(TokenType.SLASH, "/"); i++ }
                '(' -> { tokens += Token(TokenType.LPAREN, "("); i++ }
                ')' -> { tokens += Token(TokenType.RPAREN, ")"); i++ }
                ',' -> { tokens += Token(TokenType.COMMA, ","); i++ }
                ':' -> { tokens += Token(TokenType.COLON, ":"); i++ }
                '=' -> { tokens += Token(TokenType.EQ, "="); i++ }
                '<' -> {
                    if (i + 1 < source.length && source[i + 1] == '=') {
                        tokens += Token(TokenType.LE, "<=")
                        i += 2
                    } else {
                        tokens += Token(TokenType.LT, "<")
                        i++
                    }
                }
                '>' -> {
                    if (i + 1 < source.length && source[i + 1] == '=') {
                        tokens += Token(TokenType.GE, ">=")
                        i += 2
                    } else {
                        tokens += Token(TokenType.GT, ">")
                        i++
                    }
                }
                '{' -> {
                    var j = i + 1
                    while (j < source.length && source[j] != '}') j++
                    val code = source.substring(i + 1, j)
                    tokens += Token(TokenType.ITEM, code)
                    i = (j + 1).coerceAtMost(source.length)
                }
                else -> {
                    if (ch.isDigit() || ch == '.') {
                        var j = i + 1
                        while (j < source.length && (source[j].isDigit() || source[j] == '.')) j++
                        tokens += Token(TokenType.NUMBER, source.substring(i, j))
                        i = j
                    } else if (ch.isLetter()) {
                        var j = i + 1
                        while (j < source.length && (source[j].isLetterOrDigit() || source[j] == '_')) j++
                        tokens += Token(TokenType.IDENT, source.substring(i, j).uppercase())
                        i = j
                    } else {
                        i++
                    }
                }
            }
        }
        tokens += Token(TokenType.EOF, "")
        return tokens
    }

    private class Parser(
        private val tokens: List<Token>,
        private val values: Map<String, BigDecimal?>,
    ) {
        private var pos = 0

        fun parseExpression(): BigDecimal? {
            val result = parseAddSub()
            expect(TokenType.EOF)
            return result
        }

        private fun parseAddSub(): BigDecimal? {
            var left = parseMulDiv()
            while (peek().type == TokenType.PLUS || peek().type == TokenType.MINUS) {
                val op = advance().type
                val right = parseMulDiv()
                left = when (op) {
                    TokenType.PLUS -> if (left == null || right == null) null else left + right
                    TokenType.MINUS -> if (left == null || right == null) null else left - right
                    else -> left
                }
            }
            return left
        }

        private fun parseMulDiv(): BigDecimal? {
            var left = parseUnary()
            while (peek().type == TokenType.STAR || peek().type == TokenType.SLASH) {
                val op = advance().type
                val right = parseUnary()
                left = when (op) {
                    TokenType.STAR -> if (left == null || right == null) null else left * right
                    TokenType.SLASH -> if (left == null || right == null || right.compareTo(BigDecimal.ZERO) == 0) {
                        null
                    } else {
                        left.divide(right, 4, RoundingMode.HALF_UP)
                    }
                    else -> left
                }
            }
            return left
        }

        private fun parseUnary(): BigDecimal? {
            return if (peek().type == TokenType.MINUS) {
                advance()
                parseUnary()?.negate()
            } else {
                parsePrimary()
            }
        }

        private fun parsePrimary(): BigDecimal? {
            val token = peek()
            return when (token.type) {
                TokenType.NUMBER -> {
                    advance()
                    token.text.toBigDecimalOrNull()
                }
                TokenType.ITEM -> {
                    advance()
                    values[token.text]
                }
                TokenType.IDENT -> parseFunction()
                TokenType.LPAREN -> {
                    advance()
                    val expr = parseAddSub()
                    expect(TokenType.RPAREN)
                    expr
                }
                else -> {
                    advance()
                    null
                }
            }
        }

        private fun parseFunction(): BigDecimal? {
            val function = expect(TokenType.IDENT).text
            expect(TokenType.LPAREN)
            return when (function) {
                "ABS" -> {
                    val value = parseAddSub()
                    expect(TokenType.RPAREN)
                    value?.abs()
                }
                "SUM" -> {
                    val from = expect(TokenType.ITEM).text
                    expect(TokenType.COLON)
                    val to = expect(TokenType.ITEM).text
                    expect(TokenType.RPAREN)
                    val sorted = values.keys.sorted()
                    val start = sorted.indexOf(from)
                    val end = sorted.indexOf(to)
                    if (start == -1 || end == -1) {
                        null
                    } else {
                        val range = if (start <= end) sorted.subList(start, end + 1) else sorted.subList(end, start + 1)
                        if (range.any { values[it] == null }) {
                            null
                        } else {
                            range.mapNotNull { values[it] }.fold(BigDecimal.ZERO, BigDecimal::add)
                        }
                    }
                }
                "IF" -> {
                    val cond = parseCondition()
                    expect(TokenType.COMMA)
                    val trueExpr = parseAddSub()
                    expect(TokenType.COMMA)
                    val falseExpr = parseAddSub()
                    expect(TokenType.RPAREN)
                    if (cond == true) trueExpr else falseExpr
                }
                else -> {
                    while (peek().type != TokenType.RPAREN && peek().type != TokenType.EOF) {
                        advance()
                    }
                    expect(TokenType.RPAREN)
                    null
                }
            }
        }

        private fun parseCondition(): Boolean? {
            val left = parseAddSub()
            val op = advance().type
            val right = parseAddSub()
            if (left == null || right == null) {
                return null
            }
            return when (op) {
                TokenType.LT -> left < right
                TokenType.GT -> left > right
                TokenType.LE -> left <= right
                TokenType.GE -> left >= right
                TokenType.EQ -> left.compareTo(right) == 0
                else -> null
            }
        }

        private fun peek(): Token = tokens.getOrElse(pos) { Token(TokenType.EOF, "") }

        private fun advance(): Token = tokens.getOrElse(pos++) { Token(TokenType.EOF, "") }

        private fun expect(type: TokenType): Token {
            val current = advance()
            if (current.type != type) {
                throw IllegalArgumentException("Expected $type but found ${current.type}")
            }
            return current
        }
    }
}
