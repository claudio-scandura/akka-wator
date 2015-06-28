package model

import play.api.libs.json.Json

case class Position(row: Int, column: Int)

case class PlanetBoundaries(rows: Int, columns: Int)

object Position {
  implicit val format = Json.format[Position]
}

sealed trait Direction

case object East extends Direction

case object West extends Direction

case object South extends Direction

case object North extends Direction

object Direction {

  private def circularIndex(index: Int, bound: Int) = (index + bound) % bound

  def directionOfNeighbour(thisPosition: Position, neighbouringPosition: Position)(implicit boundaries: PlanetBoundaries): Direction = {
    Map(
      Position(circularIndex(thisPosition.row - 1, boundaries.rows), thisPosition.column) -> North,
      Position(circularIndex(thisPosition.row + 1, boundaries.rows), thisPosition.column) -> South,
      Position(thisPosition.row, circularIndex(thisPosition.column + 1, boundaries.columns)) -> East,
      Position(thisPosition.row, circularIndex(thisPosition.column - 1, boundaries.columns)) -> West
    ) getOrElse(neighbouringPosition, throw new IllegalArgumentException(s"position: $neighbouringPosition is not a neighbour of $thisPosition"))
  }

  def neighbourPositionFromDirection(thisPosition: Position, direction: Direction)(implicit boundaries: PlanetBoundaries): Position = direction match {
    case North => thisPosition.copy(row = circularIndex(thisPosition.row - 1, boundaries.rows))
    case South => thisPosition.copy(row = circularIndex(thisPosition.row + 1, boundaries.rows))
    case East => thisPosition.copy(column = circularIndex(thisPosition.column + 1, boundaries.columns))
    case West => thisPosition.copy(column = circularIndex(thisPosition.column - 1, boundaries.columns))
  }
}
