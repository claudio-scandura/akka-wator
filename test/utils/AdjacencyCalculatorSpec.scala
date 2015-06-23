package utils

import actors.Position
import org.scalatest.{Matchers, WordSpec}

class AdjacencyCalculatorSpec extends WordSpec with Matchers {

  class CalculatorUnderTest extends AdjacencyCalculator

  trait Setup {
    val calculatorUnderTest = new CalculatorUnderTest
    val fixedPosition = Position(2, 1)
  }

  "isAdjacent" should {
    "return true if given position is east of this position" in new Setup {
      val eastPosition = Position(fixedPosition.row, fixedPosition.column - 1)
      calculatorUnderTest.areAdjacent(fixedPosition, eastPosition) shouldBe true
    }

    "return true if given position is north of this position" in new Setup {
      val northPosition = Position(fixedPosition.row - 1, fixedPosition.column)
      calculatorUnderTest.areAdjacent(fixedPosition, northPosition) shouldBe true
    }

    "return true if given position is west of this position" in new Setup {
      val westPosition = Position(fixedPosition.row, fixedPosition.column + 1)
      calculatorUnderTest.areAdjacent(fixedPosition, westPosition) shouldBe true
    }

    "return true if given position is south of this position" in new Setup {
      val southPosition = Position(fixedPosition.row + 1, fixedPosition.column)
      calculatorUnderTest.areAdjacent(fixedPosition, southPosition) shouldBe true
    }

    "return false if given position is not adjacent to this position" in new Setup {
      val nonAdjacentPosition = Position(fixedPosition.row + 1, fixedPosition.column + 1)
      calculatorUnderTest.areAdjacent(fixedPosition, nonAdjacentPosition) shouldBe false
    }
  }
}
