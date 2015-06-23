package actors.fsm

import actors.Position
import actors.fsm.Cell.{Fill, Fish, Shark}
import akka.actor.{Actor, ActorLogging}

object Cell {

  sealed trait Animal

  case object Fish extends Animal

  case object Shark extends Animal

  case class Fill(animal: Animal)

  case class Move(animal: Animal)

}


/**
 * This actor will represent a single cell of the wator planet.
 * Actor paths will embed (rowIdx, columnIdx) so that each actor can indeed talk to its neighbours.
 * Cell is a FSM with three possible states: occupiedByFish, occupiedByShark, lookingForMove and empty. Each time
 * a cell actor transits from one state to the other it will advertise its new state to its neighbours (except for lookinfForMove state which is supposed to be temporary); therefore,
 * every cell actor will now in what state its neighbours are. This information will be used to calculate the next move.
 *
 */
class Cell(position: Position) extends Actor with ActorLogging {

  import context._

  def empty: Receive = {
    case Fill(Fish) =>
      //notify neighbours my state is Fish
      become(occupiedByFish)
    case Fill(Shark) =>
      //notify neighbours my state is Shark
      become(occupiedByShark)
    case msg => log.info(s"Empty cell has no behaviour defined for message $msg")
  }

  def occupiedByFish: Receive = empty

  def occupiedByShark: Receive = ???

  def lookingForMove: Receive = ???

  override def receive: Receive = empty
}
