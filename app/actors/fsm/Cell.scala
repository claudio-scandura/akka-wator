package actors.fsm

import java.util.concurrent.TimeUnit

import actors.Fish.Tick
import actors.Position
import actors.fsm.Cell.{Fill, Ko, Ok}
import akka.actor._
import akka.pattern.ask
import play.api.libs.json.Json
import play.api.mvc.WebSocket

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


/**
 *
 * This actor will represent a single cell of the wator planet.
 * Actor paths will embed <rowIdx, columnIdx> so that each actor can indeed talk to its neighbours.
 * Cell is a FSM with three possible states: fish, shark and water. Each time
 * a cell actor transits from one state to the other it will advertise its new state to its neighbours; therefore,
 * every cell actor will now in what state its neighbours are. This information will be used to calculate the next move.
 *
 */
class Cell(position: Position, rows: Int, columns: Int, wsOut: Option[ActorRef]) extends Actor with ActorLogging {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)


  import scala.collection.mutable.Map

  private val neighbours = neighboursOf(position)

//  lazy val position = cellPositionFor(self)

  private final val PositionRegex = """.*(\d+)-(\d+)""".r

  private lazy val neighboursRefs: Set[ActorSelection] = neighbours.keySet.map(actorRefFor)

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

  def becomeFish: Unit = {
    become(fish)
  }

  def becomeShark: Unit = {
    become(shark)
  }

  def becomeWater: Unit = {
    become(water)
  }

  def water: Receive = {
    case Tick => tickAs(Water)
    case neighbourStatusUpdate: CellContent => updateNeighbourState(neighbourStatusUpdate, sender)
    case Fill(Fish) =>
      log.info("Becoming fish")
      becomeFish
      notifyNewPosition(position, "fish")
      advertiseStateToNeighbours(Fish)
      sender ! Ok
    case Fill(Shark) =>
      log.info("Becoming shark")
      notifyNewPosition(position, "shark")
      becomeShark
      advertiseStateToNeighbours(Shark)
      sender ! Ok
    case msg => log.info(s"Empty cell has no behaviour defined for message $msg")
  }

  def fish: Receive = {
    case Tick => tickAs(Fish)
    case neighbourStatusUpdate: CellContent => updateNeighbourState(neighbourStatusUpdate, sender)
    case Fill(Fish) =>
      sender ! Ko
    case Fill(Shark) =>
      becomeShark
      advertiseStateToNeighbours(Shark)
      sender ! Ok
    case msg => log.info(s"Fish cell has no behaviour defined for message $msg")
  }

  def shark: Receive = {
    case Tick => tickAs(Shark)
    case neighbourStatusUpdate: CellContent => updateNeighbourState(neighbourStatusUpdate, sender)
    case Fill(Fish | Shark) => sender ! Ko
    case msg => log.info(s"Shark cell has no behaviour defined for message $msg")
  }

  override def receive: Receive = water

  private[fsm] def tickAs(cellContent: CellContent): Unit = cellContent match {
    case Fish => availableEmptyCell map { position =>
      actorRefFor(position) ? Fill(Fish) onSuccess {
        case Ok =>
          log.info(s"Fish moved from ${this.position} to $position")
          notifyNewPosition(position, "fish")
          advertiseStateToNeighbours(Water)
          becomeWater
        case msg =>
          log.info(s"Failed to move fish from ${this.position} to $position. Result was $msg")
          self ! Tick
      }
    } getOrElse {
      log.info(s"No available positions around $position. Fish is staying here")
    }

    case Shark => (availableFishCell orElse availableEmptyCell) map { position =>
      actorRefFor(position) ? Fill(Shark) onSuccess {
        case Ok =>
          log.info(s"Shark moved from ${this.position} to $position")
          notifyNewPosition(position, "shark")
          advertiseStateToNeighbours(Water)
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
    if (emptyCells.nonEmpty) Some(emptyCells(math.abs(Random.nextInt) % emptyCells.size)) else None
  }

  private def availableFishCell: Option[Position] = {
    val emptyCells = neighbours.filter {
      case (pos, Fish) => true
      case _ => false
    }.keySet.toSeq
    if (emptyCells.nonEmpty) Some(emptyCells(math.abs(Random.nextInt) % emptyCells.size)) else None
  }

  def updateNeighbourState(content: CellContent, ref: ActorRef): Unit = neighbours.put(cellPositionFor(ref), content)

  private def advertiseStateToNeighbours(state: CellContent): Unit = neighboursRefs foreach (_ ! state)


  /**
   * if actor cell has position <i, j> then its ActoPath will be /user/grid/i-j
   */
  private def actorRefFor(position: Position): ActorSelection = system.actorSelection(s"/user/orchestrator/${position.row}-${position.column}")

  private def cellPositionFor(actor: ActorRef): Position = actor.path.toString match {
    case PositionRegex(row, column) => Position(row.toInt, column.toInt)
    case string => throw new RuntimeException(s"Could not extract position from: \'$string\'")
  }

  def notifyNewPosition(newPosition: Position, as: String): Unit = wsOut foreach { channel =>
    channel ! Json.obj("status" -> "update", "animal" -> as,
      "position" -> Json.toJson(newPosition),
      "oldPosition" -> Json.toJson(this.position))
  }

}
