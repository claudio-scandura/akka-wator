package actors.fsm

import java.util.concurrent.TimeUnit

import actors.Fish.Tick
import actors.fsm.Cell._
import akka.actor._
import akka.pattern.ask
import model._
import play.api.libs.json.Json

import scala.util.{Random, Success, Try}


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
class Cell(position: Position, rows: Int, columns: Int, wsOut: Option[ActorRef], val heartBeatFrequency: Int = 1000, lifeDeathParams: LifeDeathParameters = LifeDeathParameters.default) extends Actor
with ActorLogging with PositiveRandomNumberGen with HeartBeat {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  import scala.collection.mutable.{Map => MutableMap}

  implicit private val boundaries = PlanetBoundaries(rows, columns)

  private[fsm] val systemPath = "/user/orchestrator/"

  private[fsm] val neighbours: MutableMap[Direction, CellContent] = MutableMap(North -> Water, East -> Water, South -> Water, West -> Water)

  private final val PositionRegex = """.*(\d+)-(\d+)""".r

  private[fsm] lazy val neighboursRefs: Map[Direction, ActorSelection] = neighbours.toMap map {
    case (direction, _) => (direction -> actorRefFor(Direction.neighbourPositionFromDirection(position, direction)))
  }

  private[fsm] var content: CellContent = Water

  var sharkStarvation: SharkStarvation = lifeDeathParams.sharkStarvation
  var sharkReproduction: SharkReproduction = lifeDeathParams.sharkReproduction
  var fishReproduction: FishReproduction = lifeDeathParams.fishReproduction

  startHeartBeat

  import context._

  private def becomeFish: Unit = {
    log.info("Becoming fish")
    become(fish)
    notifyNewPosition("fish")
    advertiseStateToNeighbours(Fish())
  }

  private def becomeShark: Unit = {
    log.info("Becoming shark")
    become(shark)
    notifyNewPosition("shark")
    advertiseStateToNeighbours(Shark())
  }

  private def becomeWater: Unit = {
    log.info("Becoming water")
    become(water)
    advertiseStateToNeighbours(Water)
    notifyNewPosition("water")
  }

  def water: Receive = tickAndReceiveNeighboursUpdates orElse {
    case Fill(fish: Fish) =>
      content = fish
      becomeFish
      sender ! Ok
    case Fill(shark@Shark(_, didNoEatFor)) if didNoEatFor <= sharkStarvation.afterTicks =>
      content = shark
      becomeShark
      sender ! Ok
    case msg =>
      log.info(s"Empty cell has no behaviour defined for message $msg")
      sender ! Ko
  }

  def fish: Receive = tickAndReceiveNeighboursUpdates orElse {
    case Fill(fish: Fish) =>
      sender ! Ko
    case Fill(shark: Shark) =>
      log.info(s"Fish $position has just been eaten")
      content = shark.copy(didNotEatFor = 1)
      becomeShark
      sender ! Ok
    case msg => log.info(s"Fish cell has no behaviour defined for message $msg")
  }

  def shark: Receive = tickAndReceiveNeighboursUpdates orElse {
    case Fill(_) => sender ! Ko
    case msg => log.info(s"Shark cell has no behaviour defined for message $msg")
  }

  def tickAndReceiveNeighboursUpdates: Receive = {
    case Tick => tickAs(content)
    case neighbourStatusUpdate: CellContent => updateNeighbourState(neighbourStatusUpdate, sender)
  }

  override def receive: Receive = water

  def dormant: Receive = {
    case neighbourStatusUpdate: CellContent => updateNeighbourState(neighbourStatusUpdate, sender)
    case other => log.info(s"Cell is dormant and will not react to message: $other")
  }

  private[fsm] def tickAs: PartialFunction[CellContent, Unit] = tickAsFish orElse tickAsShark orElse {
    case _ => log.info("Empty cell will not react to tick message")
  }


  val tickAsFish: PartialFunction[CellContent, Unit] = {
    case thisFish@Fish(aliveFor) if aliveFor % fishReproduction.afterTicks == 0 =>
      content = thisFish.copy(aliveFor = aliveFor + 1)
      availableEmptyCell map { direction =>
        neighboursRefs(direction) ! Fill(Fish())
      } getOrElse {
        log.info(s"No available positions around $position. New fish cannot be spawned")
      }

    case thisFish@Fish(aliveFor) => availableEmptyCell map { direction =>
      become(dormant)
      (neighboursRefs(direction) ? Fill(Fish(aliveFor + 1))) onComplete {
        case Success(Ok) =>
          log.info(s"Fish moved from ${this.position} to $direction")
          content = Water
          becomeWater
        case msg =>
          log.info(s"Failed to move fish from ${this.position} to $direction. Result was $msg")
          content = thisFish.copy(aliveFor = aliveFor + 1)
          become(fish)
      }
    } getOrElse {
      log.info(s"No available positions around $position. Fish is staying here")
    }
  }

  val tickAsShark: PartialFunction[CellContent, Unit] = {
    case Shark(aliveFor, didNotEatFor) if didNotEatFor >= sharkStarvation.afterTicks =>
      content = Water
      becomeWater
      availableFishCell map { direction =>
        neighboursRefs(direction) ! Fill(Shark(aliveFor + 1, didNotEatFor + 1))
      }

    //TODO: I don't like this..
    case thisShark@Shark(aliveFor, didNotEatFor) if aliveFor % sharkReproduction.afterTicks == 0 =>
      content = thisShark.copy(aliveFor + 1, didNotEatFor + 1)
      availableEmptyCell map { direction =>
        neighboursRefs(direction) ! Fill(Shark())
      } getOrElse {
        log.info(s"No available positions around $position. New shark cannot be spawned")
      }

    case thisShark@Shark(aliveFor, didNotEatFor) => (availableFishCell orElse availableEmptyCell) map { direction =>
      become(dormant)
      //TODO: now we need to check if shark's gonna eat or is just moving
      (neighboursRefs(direction) ? Fill(Shark(aliveFor + 1, didNotEatFor + 1))) onComplete {
        case Success(Ok) =>
          log.info(s"Shark moved from ${this.position} to $direction")
          content = Water
          becomeWater
        case msg =>
          log.info(s"Failed to move Shark from ${this.position} to $direction. Result was $msg")
          if (didNotEatFor + 1 < sharkStarvation.afterTicks) {
            content = thisShark.copy(aliveFor = aliveFor + 1, didNotEatFor = didNotEatFor + 1)
            become(shark)
          }
          else {
            content = Water
            becomeWater
          }
      }
    } getOrElse {
      log.info(s"No available positions around $position. Shark is staying here")
    }
  }

  private def availableEmptyCell: Option[Direction] = {
    val emptyCells = neighbours.filter(_._2.isEmpty).keySet.toSeq
    if (emptyCells.nonEmpty) Some(emptyCells(nextRandomNumber(0 until emptyCells.size))) else None
  }

  private def availableFishCell: Option[Direction] = {
    val fishCells = neighbours.filter {
      case (pos, f: Fish) => true
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

}