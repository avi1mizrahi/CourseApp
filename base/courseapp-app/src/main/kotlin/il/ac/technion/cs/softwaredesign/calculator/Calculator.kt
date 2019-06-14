package il.ac.technion.cs.softwaredesign.calculator

import il.ac.technion.cs.softwaredesign.calculator.ast.*
import il.ac.technion.cs.softwaredesign.calculator.ast.Token.Type.*
import java.math.BigInteger

class CalculatorException(message: String) : RuntimeException(message)

private class Evaluator : ExprVisitor<BigInteger> {
    fun eval(expr: Expr): BigInteger = expr.accept(this)

    override fun visitBinaryExpr(expr: BinaryExpr): BigInteger =
            when (expr.operator.type) {
                PLUS  -> BigInteger::plus
                MINUS -> BigInteger::minus
                MUL   -> BigInteger::times
                DIV   -> BigInteger::divide
                else  -> throw CalculatorException("Invalid binary operator '${expr.operator.lexeme}'")
            }.invoke(eval(expr.left), eval(expr.right))

    override fun visitUnaryExpr(expr: UnaryExpr): BigInteger {
        if (expr.operator.type != MINUS) throw CalculatorException("Invalid unary operator")

        return eval(expr.right).negate()
    }

    override fun visitLiteralExpr(expr: LiteralExpr): BigInteger = expr.value as BigInteger

    override fun visitGroupingExpr(expr: GroupingExpr): BigInteger = eval(expr.expression)
}

fun calculate(expression: String): BigInteger {
    return Evaluator().eval(Parser.parse(Scanner.scan(expression)))
}
