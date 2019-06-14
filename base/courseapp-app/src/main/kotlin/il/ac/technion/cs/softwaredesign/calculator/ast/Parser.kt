package il.ac.technion.cs.softwaredesign.calculator.ast

import il.ac.technion.cs.softwaredesign.calculator.CalculatorException
import il.ac.technion.cs.softwaredesign.calculator.ast.Token.Type.*
import java.math.BigInteger

internal class Parser private constructor(private val tokens: List<Token>) {
    companion object {
        fun parse(tokens: List<Token>) = Parser(tokens).parse()
    }

    private var current = 0

    private fun parse(): Expr {
        val expr = expression()

        if (hasNext()) throw CalculatorException("Expected end of expression, found '${peek().lexeme}'")

        return expr
    }

    private fun expression(): Expr {
        return addition()
    }

    private fun addition(): Expr {
        var left = multiplication()

        while (true) {
            val operator = consume(PLUS, MINUS) ?: return left
            val right = multiplication()

            left = BinaryExpr(left, operator, right)
        }
    }

    private fun multiplication(): Expr {
        var left = unary()

        while (true) {
            val operator = consume(MUL, DIV) ?: return left
            val right = unary()

            left = BinaryExpr(left, operator, right)
        }
    }

    private fun unary(): Expr {
        val operator = consume(MINUS) ?: return primary()
        val right = unary()
        return UnaryExpr(operator, right)
    }

    private fun primary(): Expr {
        return consume(NUMBER)?.let {
            LiteralExpr(it.literal as BigInteger)

        } ?: consume(LEFT_PAREN)?.let {
            val expr = expression()
            consume(RIGHT_PAREN) ?: throw CalculatorException("Expected ')' after '${it.lexeme}'.")
            GroupingExpr(expr)

        } ?: throw CalculatorException("Expected expression")
    }

    private fun consume(vararg types: Token.Type): Token? {
        return if (hasNext() && peek().type in types) advance()
        else null
    }

    private fun advance() = tokens.getOrNull(current++)

    private fun hasNext() = peek().type != EOF

    private fun peek() = tokens[current]
}