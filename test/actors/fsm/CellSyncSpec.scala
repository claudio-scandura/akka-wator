package actors.fsm

import actors.Position
import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestKit}
import org.scalatest.{Matchers, WordSpecLike}

class CellSyncSpec extends TestKit(ActorSystem("TestWatorSystem")) with Matchers with WordSpecLike {

  private trait Setup {
    val cell = TestActorRef(new Cell(Position(0, 0), 3, 3, None))
  }

  "updating neighbour state" should {

    "set a neighbour content to water" in new Setup {
      val neighbour = TestActorRef("0-1")
      cell.underlyingActor.updateNeighbourState(Water, neighbour)
      cell.underlyingActor.neighbours should contain(Position(0, 1) -> Water)
    }

    "set a neighbour content to Shark" in new Setup {
      val neighbour = TestActorRef("1-0")
      cell.underlyingActor.updateNeighbourState(Shark, neighbour)
      cell.underlyingActor.neighbours should contain(Position(1, 0) -> Shark)
    }

    "set a neighbour content to fish" in new Setup {
      val neighbour = TestActorRef("0-2")
      cell.underlyingActor.updateNeighbourState(Fish, neighbour)
      cell.underlyingActor.neighbours should contain(Position(0, 2) -> Fish)
    }

    "do not set a neighbour content to a new state if the position is not neighbouring" in new Setup {
      val neighbour = TestActorRef("2-3")
      cell.underlyingActor.updateNeighbourState(Fish, neighbour)
      cell.underlyingActor.neighbours should not contain(Position(2, 3) -> Fish)
    }
  }
}
