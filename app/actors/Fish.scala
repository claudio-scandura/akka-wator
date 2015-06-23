
package actors

import java.util.concurrent.TimeUnit

import actors.Fish.{AssignPosition, Devour, Tick}
import actors.Messages._
import actors.PlanetManager.messages.requests.{CompleteAttack, FreePosition, GetNextMove}
import actors.PlanetManager.messages.responses.{AttackFish, MoveToPosition}
import actors.Shark.CannotEatShark
import akka.actor._
import akka.pattern._
import play.api.libs.json.Json
import utils.AdjacencyCalculator

import scala.concurrent.ExecutionContext.Implicits.global


case class Position(row: Int, column: Int)
object Position {
  implicit val format = Json.format[Position]
}

class Fish(var position: Position, planetManager: ActorRef, wsOut: Option[ActorRef] = None) extends Actor with ActorLogging with AdjacencyCalculator  {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  override def receive: Actor.Receive = {
    case Devour(fromPosition: Position) if (areAdjacent(position, fromPosition)) =>
      planetManager ! FreePosition(position)
      sender ! Dead
      context.stop(self)
    case AssignPosition(position) =>
      assignPosition(position)
    case Tick =>
      planetManager ? GetNextMove(self, position, false) onSuccess {
        case Some(move: MoveToPosition) =>
          log.info(s"Moving from $position to ${move.position}")
          val oldPosition = position
          position = move.position
          notifyNewPosition(oldPosition)
        case _ =>
          log.info(s"Moving from $position to nowhere as there is no new position available")
      }
    case _ => sender ! TooLate
  }

  def notifyNewPosition(oldPosition: Position, as: String = "fish"): Unit = wsOut foreach { channel =>
     channel ! Json.obj("status" -> "update", "animal" -> as,
       "position" -> Json.toJson(this.position),
       "oldPosition" -> Json.toJson(oldPosition))
  }

  protected def assignPosition(newPosition: Position) {
    if (this.position == null) {
      this.position = newPosition
    }
  }
}





object Fish {
  def apply() = new Fish(Position(0, 0), null)
  def apply(row: Int, column: Int) = new Fish(Position(row, column), null)
  def apply(row: Int, column: Int, positionCalculator: ActorRef) = new Fish(Position(row, column), positionCalculator)
  def apply(positionCalculator: ActorRef, wsOut: ActorRef) = new Fish(null, positionCalculator, Option(wsOut))
  def props: Props = Props(classOf[Fish])

  case object Tick
  case class AssignPosition(position: Position)
  case class Devour(fromPosition: Position)
}

object Messages {
  case object Dead
  case object TooLate
}



