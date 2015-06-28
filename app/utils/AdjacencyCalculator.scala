package utils

import model.Position

case class WatorBoundaries(rows: Int = 10, columns: Int = 10)

trait AdjacencyCalculator {
  def areAdjacent(thisPosition: Position, otherPosition: Position): Boolean = {
    east(thisPosition, otherPosition) ||
    west(thisPosition, otherPosition) ||
    north(thisPosition, otherPosition) ||
    south(thisPosition, otherPosition)
  }

  def east(thisPosition: Position, otherPosition: Position): Boolean = {
    thisPosition.column - 1 == otherPosition.column && thisPosition.row == otherPosition.row
  }

  def west(thisPosition: Position, otherPosition: Position): Boolean = {
    thisPosition.column + 1 == otherPosition.column && thisPosition.row == otherPosition.row
  }

  def north(thisPosition: Position, otherPosition: Position): Boolean = {
    thisPosition.column == otherPosition.column && thisPosition.row - 1 == otherPosition.row
  }

  def south(thisPosition: Position, otherPosition: Position): Boolean = {
    thisPosition.column == otherPosition.column && thisPosition.row + 1 == otherPosition.row
  }
}
