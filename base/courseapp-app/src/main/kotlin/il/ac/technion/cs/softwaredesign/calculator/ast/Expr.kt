package il.ac.technion.cs.softwaredesign.calculator.ast

internal sealed class Expr {
    abstract fun <T> accept(visitor: ExprVisitor<T>): T
}

internal class BinaryExpr(val left: Expr, val operator: Token, val right: Expr) : Expr() {
    override fun <T> accept(visitor: ExprVisitor<T>): T = visitor.visitBinaryExpr(this)
}

internal class UnaryExpr(val operator: Token, val right: Expr) : Expr() {
    override fun <T> accept(visitor: ExprVisitor<T>): T = visitor.visitUnaryExpr(this)
}

internal class LiteralExpr(val value: Any) : Expr() {
    override fun <T> accept(visitor: ExprVisitor<T>): T = visitor.visitLiteralExpr(this)
}

internal class GroupingExpr(val expression: Expr) : Expr() {
    override fun <T> accept(visitor: ExprVisitor<T>): T = visitor.visitGroupingExpr(this)
}

internal interface ExprVisitor<out T> {

    fun visitBinaryExpr(expr: BinaryExpr): T
    fun visitUnaryExpr(expr: UnaryExpr): T
    fun visitLiteralExpr(expr: LiteralExpr): T
    fun visitGroupingExpr(expr: GroupingExpr): T
}