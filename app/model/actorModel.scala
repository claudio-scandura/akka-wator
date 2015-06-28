package model

import play.api.libs.json.Json

case class Position(row: Int, column: Int)

object Position {
  implicit val format = Json.format[Position]
}
