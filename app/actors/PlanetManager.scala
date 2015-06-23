package actors

import actors.Fish.AssignPosition
import actors.PlanetManager.messages.requests.{CompleteAttack, FreePosition, GetNextMove, GetRandomPosition}
import actors.PlanetManager.messages.responses.{AttackFish, Move, MoveToPosition}
import actors.PlanetManager.model.Animal
import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.collection.mutable.{Map, Queue}
import scala.util.{Random, Try}

class PlanetManager(rows: Int, columns: Int) extends Actor with ActorLogging {

  val planet: Array[Array[Option[Animal]]] = Array.fill(rows, columns)(None)

  val availablePositions: Queue[Position] = Queue(Random.shuffle(
    for {
      row <- (0 until rows)
      column <- (0 until columns)
    } yield Position(row, column)
  ): _*)

  val pendingAttacks: Map[ActorRef, AttackFish] = Map()


  override def receive: Actor.Receive = {
    case GetRandomPosition(assignee, isShark) =>
      val result = assignRandomPosition(assignee, isShark)
      result foreach { pos =>
        log.info(s"Assigning random position to $assignee: $pos, there are ${availablePositions.size} positions left")
        assignee ! AssignPosition(pos)
      }
      sender ! result
    case GetNextMove(senderRef, currentPosition, false) =>
      log.info(s"Got request from fish $sender that is currently at position: $currentPosition")
      val nextMove = calculateNextMoveForFish(currentPosition, senderRef)
      sender ! nextMove
    case GetNextMove(senderRef, currentPosition, true) =>
      log.info(s"Got request from shark $sender that is currently at position: $currentPosition")
      val nextMove = calculateNextMoveForShark(currentPosition, senderRef)
      sender ! nextMove
    case FreePosition(position) =>
      log.info(s"Freeing  position $position")
      planet(position.row)(position.column) = None
    case CompleteAttack(fromPosition) =>
      log.info(s"Shark $sender managed to devour fish")
      planet(fromPosition.row)(fromPosition.column) foreach { shark =>
        log.info(s"sender and animal in planet should match: $sender =? ${shark.ref} ")
        pendingAttacks.remove(shark.ref) foreach { attack =>
          log.info(s"Found pending attack for the shark: $attack")
          planet(attack.position.row)(attack.position.column) = Some(shark)
          planet(fromPosition.row)(fromPosition.column) = None
          availablePositions.enqueue(fromPosition)
        }
      }
      
  }

  private def calculateNextMoveForFish(fromPosition: Position, senderRef: ActorRef): Option[Move] = {
    val neighboursCoordinates = neighboursOf(fromPosition)
    val availableNeighbourPositions: Seq[MoveToPosition] = neighboursCoordinates flatMap {
      case (row, column) if planet(row)(column).isEmpty => Some(MoveToPosition(Position(row, column)))
      case _ => None
    }
    val randomIndex = Random.nextInt % availableNeighbourPositions.size
    val randomMove = Try(availableNeighbourPositions(randomIndex)).toOption
    randomMove foreach { move =>
      planet(move.position.row)(move.position.column) = Some(Animal(senderRef, isShark =  false))
      planet(fromPosition.row)(fromPosition.column) = None
      availablePositions.enqueue(fromPosition)
    }
    randomMove
  }

  private def calculateNextMoveForShark(fromPosition: Position, senderRef: ActorRef): Option[Move] = {
    val neighboursCoordinates = neighboursOf(fromPosition)

    val nearbyFish: Seq[AttackFish] = neighboursCoordinates flatMap {
      case (row, column) =>
        planet(row)(column) flatMap {
          case Animal(path, false) => Some(AttackFish(path, Position(row, column)))
          case _ => None
        }
    }
    if (nearbyFish.nonEmpty) {
      val randomIndex = Random.nextInt % nearbyFish.size
      val randomFish = nearbyFish(randomIndex)
      pendingAttacks.put(senderRef, randomFish)
      log.info(s"Returning random fish to shark $randomFish")
      Some(randomFish)
    }
    else {
      val availableNeighbourPositions: Seq[MoveToPosition] = neighboursCoordinates flatMap {
        case (row, column) if planet(row)(column).isEmpty => Some(MoveToPosition(Position(row, column)))
        case _ => None
      }
      val randomIndex = Random.nextInt % availableNeighbourPositions.size
      val randomPosition = Try(availableNeighbourPositions(randomIndex)).toOption
      randomPosition foreach { move =>
        planet(move.position.row)(move.position.column) = Some(Animal(senderRef, isShark = true))
        planet(fromPosition.row)(fromPosition.column) = None
        availablePositions.enqueue(fromPosition)
        pendingAttacks.remove(senderRef)
      }
      randomPosition
    }
  }

  private def neighboursOf(position: Position) = {
    def circularIndex(index: Int, bound: Int) = (index + bound) % bound
    Seq(
      (circularIndex(position.row - 1, rows), position.column), //north
      (circularIndex(position.row + 1, rows), position.column), //south
      (position.row, circularIndex(position.column + 1, columns)), //east
      (position.row, circularIndex(position.column - 1, columns)) //west
    )
  }

  private def assignRandomPosition(assignee: ActorRef, isShark: Boolean): Option[Position] = if (availablePositions.nonEmpty) {
    val assignedPosition = availablePositions.dequeue()
    if (planet(assignedPosition.row)(assignedPosition.column).isDefined) assignRandomPosition(assignee, isShark)
    else {
      planet(assignedPosition.row)(assignedPosition.column) = Some(Animal(assignee, isShark))
      Some(assignedPosition)
    }
  } else None
}

object PlanetManager {

  object messages {

    object requests {

      case class GetRandomPosition(assignee: ActorRef, forShark: Boolean = false)

      case class GetNextMove(sender: ActorRef, fromPosition: Position, asShark: Boolean)

      case class FreePosition(position: Position)

      case class CompleteAttack(fromPosition: Position)
    }

    object responses {

      sealed trait Move {
        def position: Position
      }

      case class MoveToPosition(position: Position) extends Move
      case class AttackFish(fish: ActorRef, position: Position) extends Move

    }

  }

  object model {

    case class Animal(ref: ActorRef, isShark: Boolean = false)

  }

}

