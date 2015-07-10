package actors.fsm

import java.util.concurrent.TimeUnit

import actors.Fish.Tick
import actors.fsm.Cell._
import akka.actor._
import akka.pattern.ask
import model._
import play.api.libs.json.Json

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Random, Try}


object Cell {

  case class Fill(animal: Animal)

  case object Ok

  case object Ko

  case object Kick

}


trait PositiveRandomNumberGen {
  def nextRandomNumber(range: Range) = Random.nextInt(range.length)
}

/**
 *
 * This actor represents a single cell of the wator planet.
 * Actor paths will embed <rowIdx, columnIdx> so that each actor can talk to its neighbours.
 * Cell is a FSM with three possible states: fish, shark and water. Each time
 * a cell actor transits from one state to the other it will advertise its new state to its neighbours; therefore,
 * every cell actor will know in what state its neighbours are. This information will be used to calculate the next move.
 *
 */
class Cell(position: Position, rows: Int, columns: Int, wsOut: Option[ActorRef], val heartBeatFrequency: Int = 1000) extends Actor
with ActorLogging with PositiveRandomNumberGen with HeartBeat {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  import scala.collection.mutable.{Map => MutableMap}

  implicit private val boundaries = PlanetBoundaries(rows, columns)

  private[fsm] val systemPath = "/user/orchestrator/"

  private[fsm] val neighbours: MutableMap[Direction, CellContent] = MutableMap(North -> Water, East -> Water, South -> Water, West -> Water)

  private final val PositionRegex = """.*(\d+)-(\d+)""".r

  var kicker: Cancellable = null

  private[fsm] lazy val neighboursRefs: Map[Direction, ActorSelection] = neighbours.toMap map {
    case (direction, _) => (direction -> actorRefFor(Direction.neighbourPositionFromDirection(position, direction)))
  }

  startHeartBeat

  import context._

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

  def fishInTransit: Receive = {
    case Ok =>
      kicker.cancel()
      becomeWater
    case Ko =>
      kicker.cancel()
      become(fish)
    case Kick => become(fish)
    case _ => Ko
  }


  def sharkInTransit: Receive = {
    case Ok =>
      becomeWater
      kicker.cancel()
    case Ko =>
      become(shark)
      kicker.cancel()
    case Kick => become(shark)
    case _ => Ko
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
      sender ! Ok
    case msg => log.info(s"Fish cell has no behaviour defined for message $msg")
  }

  def shark: Receive = tickAndReceiveNeighboursUpdatesAs(Shark) orElse {
    case Fill(_) => sender ! Ko
    case msg => log.info(s"Shark cell has no behaviour defined for message $msg")
  }

  def tickAndReceiveNeighboursUpdatesAs(content: CellContent): Receive = {
    case Tick => tickAs(content)
    case neighbourStatusUpdate: CellContent => updateNeighbourState(neighbourStatusUpdate, sender)
  }

  override def receive: Receive = water

  private[fsm] def tickAs(cellContent: CellContent): Unit = cellContent match {
    case Fish => availableEmptyCell map { direction =>
      become(fishInTransit)
      neighboursRefs(direction) ! Fill(Fish)
      registerKick
//      Await.result(neighboursRefs(direction) ? Fill(Fish), Duration(3000, TimeUnit.MILLISECONDS)) match {
//        case Ok =>
//          log.info(s"Fish moved from ${this.position} to $direction")
//          becomeWater
//        case msg =>
//          log.info(s"Failed to move fish from ${this.position} to $direction. Result was $msg")
//      }
    } getOrElse {
      log.info(s"No available positions around $position. Fish is staying here")
    }

    case Shark => (availableFishCell orElse availableEmptyCell) map { direction =>
      become(sharkInTransit)
      neighboursRefs(direction) ! Fill(Shark)
      registerKick
//      Await.result(neighboursRefs(direction) ? Fill(Shark), Duration(3000, TimeUnit.MILLISECONDS)) match {
//        case Ok =>
//          log.info(s"Shark moved from ${this.position} to $direction")
//          becomeWater
//        case msg =>
//          log.info(s"Failed to move Shark from ${this.position} to $direction. Result was $msg")
//      }
    } getOrElse {
      log.info(s"No available positions around $position. Shark is staying here")
    }

    case _ => log.info("Empty cell will not react to tick message")
  }

  private def availableEmptyCell: Option[Direction] = {
    val emptyCells = neighbours.filter(_._2.isEmpty).keySet.toSeq
    if (emptyCells.nonEmpty) Some(emptyCells(nextRandomNumber(0 until emptyCells.size))) else None
  }

  private def availableFishCell: Option[Direction] = {
    val fishCells = neighbours.filter {
      case (pos, Fish) => true
      case _ => false
    }.keySet.toSeq
    if (fishCells.nonEmpty) Some(fishCells(nextRandomNumber(0 until fishCells.size))) else None
  }

  def updateNeighbourState(content: CellContent, ref: ActorRef): Unit = {
    val neighbourPosition = cellPositionFor(ref)
    Try(Direction.directionOfNeighbour(position, neighbourPosition)).toOption foreach { directionFromThisPosition =>
      neighbours.put(directionFromThisPosition, content)
    }
  }

  private def advertiseStateToNeighbours(state: CellContent): Unit = neighboursRefs.values foreach (_ ! state)


  /**
   * if actor cell has position <i, j> then its ActorPath will be /user/grid/i-j
   */
  private[fsm] def actorRefFor(position: Position): ActorSelection =
    system.actorSelection(s"$systemPath${position.row}-${position.column}")

  private def cellPositionFor(actor: ActorRef): Position = actor.path.toString match {
    case PositionRegex(row, column) => Position(row.toInt, column.toInt)
    case string => throw new RuntimeException(s"Could not extract position from: \'$string\'")
  }

  def notifyNewPosition(as: String): Unit = wsOut foreach { channel =>
    channel ! Json.obj("status" -> "update", "animal" -> as,
      "position" -> Json.toJson(this.position)
    )
  }

  def registerKick = {
    kicker = context.system.scheduler.scheduleOnce(
      Duration(1, TimeUnit.SECONDS),
      self,
      Kick
    )
  }
}