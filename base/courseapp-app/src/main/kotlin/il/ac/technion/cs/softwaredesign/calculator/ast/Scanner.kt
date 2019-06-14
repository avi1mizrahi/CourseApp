package il.ac.technion.cs.softwaredesign.calculator.ast

import il.ac.technion.cs.softwaredesign.calculator.CalculatorException
import il.ac.technion.cs.softwaredesign.calculator.ast.Token.Type.*

internal class Scanner private constructor(private val source: String) {

    companion object {
        fun scan(source: String) = Scanner(source).scan()
    }

    private val tokens: MutableList<Token> = mutableListOf()
    private var start = 0
    private val length = source.length
    private var current = 0

    private fun scan(): List<Token> {
        while (!isAtEnd()) scanToken()

        tokens.add(Token(EOF, "", null))
        return tokens
    }

    private fun isAtEnd(): Boolean = current >= length

    private fun scanToken() {
        start = current

        when (val c = advance()) {
            ' ',
            '\n',
            '\r',
            '\t' -> {/* whitespace */
            }
            '+'  -> addToken(PLUS)
            '-'  -> addToken(MINUS)
            '*'  -> addToken(MUL)
            '/'  -> addToken(DIV)
            '('  -> addToken(LEFT_PAREN)
            ')'  -> addToken(RIGHT_PAREN)
            else -> if (c.isDigit()) number()
            else throw CalculatorException("Invalid token '$c'")
        }
    }

    private fun number() {
        while (peek()?.isDigit() == true) advance()

        addToken(NUMBER)
    }

    private fun advance() = source[current++]

    private fun peek(): Char? = source.getOrNull(current)

    private fun addToken(type: Token.Type) {
        val text = source.substring(start, current)
        tokens.add(Token(type,
                         text,
                         if (type == NUMBER) text.toBigInteger()
                         else null))
    }
}