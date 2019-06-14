package il.ac.technion.cs.softwaredesign.calculator.ast

import java.math.BigInteger

internal data class Token(val type: Type,
                          val lexeme: String,
                          val literal: BigInteger?) {

    enum class Type {
        NUMBER,

        PLUS,
        MINUS,
        MUL,
        DIV,

        LEFT_PAREN,
        RIGHT_PAREN,

        EOF
    }
}
