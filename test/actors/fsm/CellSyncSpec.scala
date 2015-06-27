package actors.fsm

import actors.Position
import akka.actor.ActorSystem
import akka.testkit.{TestKit, TestActorRef}
import org.scalatest.{Matchers, WordSpecLike}

class CellSyncSpec  extends TestKit(ActorSystem("TestWatorSystem")) with Matchers with WordSpecLike  {

  private trait Setup {
    val cell = TestActorRef(new Cell(Position(0,0), 3, 3, None))
  }

  "updating neighbour state" should {

    "set a neighbour content to water" in new Setup {
      val neighbour = TestActorRef("2-3")
      cell.underlyingActor.updateNeighbourState(Water, neighbour)
      cell.underlyingActor.neighbours should contain(Position(2, 3) -> Water)
    }

    "set a neighbour content to Shark" in new Setup {
      val neighbour = TestActorRef("2-3")
      cell.underlyingActor.updateNeighbourState(Shark, neighbour)
      cell.underlyingActor.neighbours should contain(Position(2, 3) -> Shark)
    }

    "set a neighbour content to fish" in new Setup {
      val neighbour = TestActorRef("2-3")
      cell.underlyingActor.updateNeighbourState(Fish, neighbour)
      cell.underlyingActor.neighbours should contain(Position(2, 3) -> Fish)
    }
  }


}
