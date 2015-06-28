package actors

import java.util.concurrent.TimeUnit

import actors.Fish.Tick
import actors.Orchestrator.StartSimulation
import actors.fsm.Cell.Fill
import actors.fsm.{Fish => FSMFish, Shark => FSMShark, _}
import akka.actor._
import controllers.SimulationParameters

import scala.collection.immutable.Iterable
import scala.collection.mutable.Queue
import scala.util.Random

class Orchestrator extends Actor with ActorLogging {
  private var simulationStarted = false
  private var planetManager: ActorRef = null
  private var channelOut: ActorRef = null

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  def availablePositions(rows: Int, columns: Int): Queue[Position] = Queue(Random.shuffle(
    for {
      row <- (0 until rows)
      column <- (0 until columns)
    } yield Position(row, column)
  ): _*)

  private def actorRefFor(position: Position): ActorSelection = context.system.actorSelection(s"/user/orchestrator/${position.row}-${position.column}")

  private def startSimulation(params: SimulationParameters, outChannel: ActorRef) = if (!simulationStarted) {
    simulationStarted = true

    val cells = for {
      row <- (0 until params.rows)
      column <- (0 until params.columns)
    } yield context.actorOf(Props(new Cell(Position(row, column), params.rows, params.columns, Some(channelOut), params.chronosFrequency)), s"$row-$column")

    val randomPositions = availablePositions(params.rows, params.columns)
    val sharks = (1 to params.sharkPopulation) map (_ => 'Shark)
    val fish = (1 to params.fishPopulation) map (_ => 'Fish)
    val sharkAndFishShuffled = Random.shuffle(sharks ++ fish)
    sharkAndFishShuffled foreach {
      case 'Shark => actorRefFor(randomPositions.dequeue()) ! Fill(FSMShark)
      case 'Fish => actorRefFor(randomPositions.dequeue()) ! Fill(FSMFish)
      case _ => Unit
    }

  }

  override def receive: Receive = {
    case StartSimulation(params: SimulationParameters, channelOut: ActorRef) =>
      this.channelOut = channelOut
      startSimulation(params, channelOut)

    case Tick =>
      val children: Iterable[ActorRef] = context.children
      log.info(s"Broadcasting Tick message to all ${children.size} my children")
      children foreach (_ ! Tick)

    case unknown => log.debug(s"Unknown message received: $unknown")
  }

}

object Orchestrator {

  case class StartSimulation(params: SimulationParameters, channelOut: ActorRef)

  sealed trait StatusUpdate

  case object NoUpdate extends StatusUpdate

  case class PositionUpdate(animal: String, newPosition: Position, oldPosition: Position) extends StatusUpdate

}
