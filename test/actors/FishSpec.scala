package actors

import java.util.concurrent.TimeUnit

import actors.Fish.{AssignPosition, Devour, Tick}
import actors.Messages.{Dead, TooLate}
import actors.PlanetManager.messages.requests.GetNextMove
import actors.PlanetManager.messages.responses.MoveToPosition
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import model.Position
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


class FishSpec extends  TestKit(ActorSystem("WatorSystem")) with WordSpecLike with BeforeAndAfterAll with ScalaFutures with Matchers with Eventually {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait Setup {
    val initialRow = 1
    val initialColumn = 1
    val newPositionAvailable: Option[Position] = Some(Position(1, 0))
    class PositionCalculatorStub extends PlanetManager(10, 10) {
      override def receive: Receive = {
        case GetNextMove(_, _, _) => sender ! newPositionAvailable.map(MoveToPosition.apply)
      }
    }

    val positionCalculator = TestActorRef(new PositionCalculatorStub)
    val fish = TestActorRef(Fish(initialRow, initialColumn, positionCalculator))
    val probe = TestProbe()
    probe watch fish
  }


  "when sending a devour message a Fish" should {

    "answer \'Dead\' and die if the message comes from an adjacent actor" in new Setup {
      val response = fish ? Devour(Position(initialRow, initialColumn - 1))
      response.futureValue shouldBe Dead

      probe expectTerminated fish
    }

    "answer TooLate if the message comes from a non adjacent actor" in new Setup {
      val response = fish ? Devour(Position(3, 2))
      response.futureValue shouldBe TooLate
    }
  }

  "when sending a tick message a Fish" should {

    "change position to whatever the positionCalculator responds with if there is a position available" in new Setup {
      fish ! Tick
      eventually {
        Some(fish.underlyingActor.position) shouldBe newPositionAvailable
      }
    }

    "do not move in a position if the positionCalculator return None" in new Setup {
      override val newPositionAvailable = None
      fish ! Tick
      eventually {
        fish.underlyingActor.position shouldBe Position(initialRow, initialColumn)
      }
    }

  }

  "when sending an AssignPosition message a fish" should {

    "assign the position if the current one is null" in new Setup {
      override val fish = TestActorRef(Fish(positionCalculator, null))
      val position = Position(initialRow, initialColumn)
       fish ! AssignPosition(position)
      eventually {
        fish.underlyingActor.position shouldBe position
      }
    }

    "do nothing if current position is not null" in new Setup {
      val position = Position(initialRow + 1, initialColumn + 1)
      fish ! AssignPosition(position)
      eventually {
        fish.underlyingActor.position shouldNot be (position)
      }
    }
  }
}
