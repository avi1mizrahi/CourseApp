package il.ac.technion.cs.softwaredesign.tests

import il.ac.technion.cs.softwaredesign.calculator.CalculatorException
import il.ac.technion.cs.softwaredesign.calculator.calculate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows


internal class CalculatorTest {
    @Test
    fun good() {
        assertEquals(7, calculate("  4+3").toInt())
        assertEquals(7, calculate("4 + 3       ").toInt())
        assertEquals(1, calculate("4 + -3       ").toInt())
        assertEquals(7, calculate("4 --3       ").toInt())
        assertEquals(-1, calculate("-4 + 3       ").toInt())
        assertEquals(-10, calculate("4 - 14       ").toInt())
        assertEquals(7, calculate("\t4 +3\t").toInt())
        assertEquals(7, calculate("4+3").toInt())
        assertEquals(64, calculate("4+3*20").toInt())
        assertEquals(19, calculate("4 * 4+3").toInt())
        assertEquals(28, calculate("4 * (4+3)").toInt())
        assertEquals(28, calculate("4 * ((((4+3))))").toInt())
        assertEquals(144, calculate("(10 * (9/1)) + (2+1) * (3*(5+1))").toInt())
        assertEquals(1, calculate("4/4").toInt())
        assertEquals(18, calculate("90/5").toInt())
        assertEquals(-18, calculate("-90/5").toInt())
        assertEquals(18, calculate("-90/-5").toInt())
        assertEquals(6, calculate("34/5").toInt())
        assertEquals(16, calculate("14 - 7 * 1 + 81 / 9").toInt())
    }

    @Test
    fun bad() {
        assertThrows<CalculatorException> { calculate("  4+3+") }
        assertThrows<CalculatorException> { calculate("  4+(3") }
        assertThrows<CalculatorException> { calculate(" 4+3)") }
        assertThrows<CalculatorException> { calculate(" 4+3)") }
        assertThrows<CalculatorException> { calculate(" +)") }
        assertThrows<CalculatorException> { calculate(" +") }
        assertThrows<CalculatorException> { calculate("-") }
        assertThrows<CalculatorException> { calculate("-4-") }
        assertThrows<CalculatorException> { calculate("()") }
        assertThrows<CalculatorException> { calculate("(9+)") }
        assertThrows<CalculatorException> { calculate("(-)") }
        assertThrows<CalculatorException> { calculate("(1-)") }
        assertThrows<CalculatorException> { calculate("((23+ 32)))") }
    }

}
