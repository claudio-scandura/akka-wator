package actors

import java.util.concurrent.TimeUnit

import actors.Fish.Tick
import actors.Orchestrator.StartSimulation
import actors.PlanetManager.messages.requests.GetRandomPosition
import akka.actor._
import akka.pattern.ask
import controllers.SimulationParameters

import scala.collection.immutable.Iterable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration

class Orchestrator extends Actor with ActorLogging {
  private var simulationStarted = false
  private var planetManager: ActorRef = null
  private var channelOut: ActorRef = null

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  private def startSimulation(params: SimulationParameters, outChannel: ActorRef) = if (!simulationStarted) {
    simulationStarted = true
    planetManager = context.system.actorOf(Props(new PlanetManager(params.rows, params.columns)))
    log.info("Requesting random positions to positionCalculator...")
    (1 to params.sharkPopulation) foreach { i =>
      val shark = context.actorOf(Props(new Shark(planetManager, Some(outChannel))), s"shark-$i")
      planetManager ? GetRandomPosition(shark, forShark = true) map {
        case Some(position: Position) =>
          log.info(s"Fish $shark was successfully assigned position $position")
        case _ =>
          log.debug("Looks like there are no more available positions in the planet. I Will kill this animal.")
          shark ! PoisonPill
      }
    }

    (1 to params.fishPopulation).foreach { i =>
      val fish = context.actorOf(Props(Fish(planetManager, outChannel)), s"fish-$i")
      planetManager ? GetRandomPosition(fish, forShark = false) map {
        case Some(position: Position) =>
          log.info(s"Fish $fish was successfully assigned position $position")
        case _ =>
          log.debug("Looks like there are no more available positions in the planet. I Will kill this animal.")
          fish ! PoisonPill
      }
    }

    context.system.scheduler.schedule(
      FiniteDuration(2, TimeUnit.SECONDS),
      FiniteDuration(params.chronosFrequency, TimeUnit.MILLISECONDS),
      self,
      Tick
    )

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
