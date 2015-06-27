package actors.fsm

import java.util.concurrent.TimeUnit

import actors.Fish.Tick
import actors.Position
import actors.fsm.Cell.{Fill, Ko, Ok}
import akka.actor._
import akka.pattern.ask
import play.api.libs.json.Json

import scala.collection.Set
import scala.util.Random

sealed trait CellContent {
  def isEmpty: Boolean
}

sealed trait Animal extends CellContent {
  override def isEmpty: Boolean = false
}

case object Water extends CellContent {
  override def isEmpty: Boolean = true
}

case object Fish extends Animal

case object Shark extends Animal

object Cell {

  case class Fill(animal: Animal)

  case object Ok

  case object Ko

}

trait PositiveRandomNumberGen {
  def nextRandomNumber = math.abs(Random.nextInt) * 1000000
}

/**
 *
 * This actor will represent a single cell of the wator planet.
 * Actor paths will embed <rowIdx, columnIdx> so that each actor can indeed talk to its neighbours.
 * Cell is a FSM with three possible states: fish, shark and water. Each time
 * a cell actor transits from one state to the other it will advertise its new state to its neighbours; therefore,
 * every cell actor will now in what state its neighbours are. This information will be used to calculate the next move.
 *
 */
class Cell(position: Position, rows: Int, columns: Int, wsOut: Option[ActorRef]) extends Actor with ActorLogging with PositiveRandomNumberGen {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)


  import scala.collection.mutable.Map

  private[fsm] val systemPath = "/user/orchestrator/"

  private[fsm] val neighbours: Map[Position, CellContent] = neighboursOf(position)

  private final val PositionRegex = """.*(\d+)-(\d+)""".r

  private[fsm] lazy val neighboursRefs: Set[ActorSelection] = neighbours.keySet.map(actorRefFor)

  import context._

  private def neighboursOf(position: Position): Map[Position, CellContent] = {
    def circularIndex(index: Int, bound: Int) = (index + bound) % bound
    Map(
      Position(circularIndex(position.row - 1, rows), position.column) -> Water, //north
      Position(circularIndex(position.row + 1, rows), position.column) -> Water, //south
      Position(position.row, circularIndex(position.column + 1, columns)) -> Water, //east
      Position(position.row, circularIndex(position.column - 1, columns)) -> Water //west
    )
  }

  private def becomeFish: Unit = {
    log.info("Becoming fish")
    become(fish)
    notifyNewPosition("fish")
    advertiseStateToNeighbours(Fish)
  }

  private def becomeShark: Unit = {
    log.info("Becoming shark")
    become(shark)
    notifyNewPosition("shark")
    advertiseStateToNeighbours(Shark)
  }

  private def becomeWater: Unit = {
    log.info("Becoming water")
    become(water)
    advertiseStateToNeighbours(Water)
    notifyNewPosition("water")
  }

  def water: Receive = tickAndReceiveNeighboursUpdatesAs(Water) orElse {
    case Fill(Fish) =>
      becomeFish
      sender ! Ok
    case Fill(Shark) =>
      becomeShark
      sender ! Ok
    case msg =>
      log.info(s"Empty cell has no behaviour defined for message $msg")
      sender ! Ko
  }

  def fish: Receive = tickAndReceiveNeighboursUpdatesAs(Fish) orElse {
    case Fill(Fish) =>
      sender ! Ko
    case Fill(Shark) =>
      log.info(s"Fish $position has just been eaten")
      becomeShark
      advertiseStateToNeighbours(Shark)
      sender ! Ok
    case msg => log.info(s"Fish cell has no behaviour defined for message $msg")
  }

  def shark: Receive = tickAndReceiveNeighboursUpdatesAs(Shark) orElse {
    case Fill(Fish | Shark) => sender ! Ko
    case msg => log.info(s"Shark cell has no behaviour defined for message $msg")
  }

  def tickAndReceiveNeighboursUpdatesAs(content: CellContent): Receive = {
    case Tick => tickAs(content)
    case neighbourStatusUpdate: CellContent => updateNeighbourState(neighbourStatusUpdate, sender)
  }

  override def receive: Receive = water

  private[fsm] def tickAs(cellContent: CellContent): Unit = cellContent match {
    case Fish => availableEmptyCell map { position =>
      actorRefFor(position) ? Fill(Fish) onSuccess {
        case Ok =>
          log.info(s"Fish moved from ${this.position} to $position")
          becomeWater
        case msg =>
          log.info(s"Failed to move fish from ${this.position} to $position. Result was $msg")
      }
    } getOrElse {
      log.info(s"No available positions around $position. Fish is staying here")
    }

    case Shark => (availableFishCell orElse availableEmptyCell) map { position =>
      actorRefFor(position) ? Fill(Shark) onSuccess {
        case Ok =>
          log.info(s"Shark moved from ${this.position} to $position")
          becomeWater
        case msg =>
          log.info(s"Failed to move Shark from ${this.position} to $position. Result was $msg")
      }
    } getOrElse {
      log.info(s"No available positions around $position. Shark is staying here")
    }

    case _ => log.info("Empty cell will not react to tick message")
  }

  private def availableEmptyCell: Option[Position] = {
    val emptyCells = neighbours.filter(_._2.isEmpty).keySet.toSeq
    if (emptyCells.nonEmpty) Some(emptyCells(nextRandomNumber % emptyCells.size)) else None
  }

  private def availableFishCell: Option[Position] = {
    val fishCells = neighbours.filter {
      case (pos, Fish) => true
      case _ => false
    }.keySet.toSeq
    if (fishCells.nonEmpty) Some(fishCells(nextRandomNumber % fishCells.size)) else None
  }

  def updateNeighbourState(content: CellContent, ref: ActorRef): Unit = neighbours.put(cellPositionFor(ref), content)

  private def advertiseStateToNeighbours(state: CellContent): Unit = neighboursRefs foreach (_ ! state)


  /**
   * if actor cell has position <i, j> then its ActorPath will be /user/grid/i-j
   */
  private[fsm] def actorRefFor(position: Position): ActorSelection = system.actorSelection(s"$systemPath${position.row}-${position.column}")

  private def cellPositionFor(actor: ActorRef): Position = actor.path.toString match {
    case PositionRegex(row, column) => Position(row.toInt, column.toInt)
    case string => throw new RuntimeException(s"Could not extract position from: \'$string\'")
  }

  def notifyNewPosition(as: String): Unit = wsOut foreach { channel =>
    channel ! Json.obj("status" -> "update", "animal" -> as,
      "position" -> Json.toJson(this.position)
    )
  }

}