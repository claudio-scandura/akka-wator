package actors

import actors.Fish.{AssignPosition, Devour, Tick}
import actors.Messages.{Dead, TooLate}
import actors.PlanetManager.messages.requests.{CompleteAttack, GetNextMove}
import actors.PlanetManager.messages.responses.{AttackFish, MoveToPosition}
import actors.Shark.CannotEatShark
import akka.actor._
import akka.pattern.ask

import scala.concurrent.ExecutionContext.Implicits.global

class Shark(planetManager: ActorRef, wsOut: Option[ActorRef] = None) extends Fish(null, planetManager, wsOut) {
  override def receive: Actor.Receive = {
    case AssignPosition(position) => super.assignPosition(position)
    case Devour(_) => sender ! CannotEatShark
    case Tick =>
      planetManager ? GetNextMove(self, position, true) onSuccess {
        case Some(MoveToPosition(newPosition: Position)) =>
          log.info(s"Moving from $position to $newPosition")
          val oldPosition = position
          position = newPosition
          notifyNewPosition(oldPosition, "shark")

        case Some(AttackFish(fish, position)) =>
          log.info(s"Trying to devour a fish at pos $position with ref: $fish")
          fish ? Devour(this.position) onSuccess {
            case Dead =>
              log.info("Yeeeeeah I got the bastard!")
              planetManager ! CompleteAttack(this.position)
              val oldPosition = position
              this.position = position
              notifyNewPosition(oldPosition, "shark")
            case other =>
              log.info(s"Couldn't manage to eat the damn fish.. answer was: $other. Trying to get a new move..")
              self ! Tick
          }
        case _ =>
          log.info(s"Moving from $position to nowhere as there is no new position available")
      }

    case _ => sender ! TooLate
  }

}

object Shark {
  case object CannotEatShark
}
