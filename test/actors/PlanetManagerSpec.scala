package actors

import java.util.concurrent.TimeUnit

import actors.PlanetManager.messages.requests.{GetRandomPosition, GetNextMove}
import actors.PlanetManager.messages.responses.{AttackFish, MoveToPosition}
import actors.PlanetManager.model.Animal
import akka.actor.{Actor, ActorLogging, ActorSystem}
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit}
import model.Position
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PlanetManagerSpec extends TestKit(ActorSystem("WatorSystem")) with WordSpecLike with BeforeAndAfterAll with ScalaFutures with Matchers with Eventually {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  class TestActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case _ => log.info("Got message!")
    }
  }

  abstract class Setup(setCurrentPositionInPlanet: Boolean = false) {
    val planetSize = 9
    val planetManager = TestActorRef(new PlanetManager(3, 3))
    val fakeAssignee = TestActorRef(new TestActor)

    val currentPosX = 2
    val currentPosY = 0
    val currentPosition = Position(currentPosX, currentPosY)

    val availablePositionsQueue = planetManager.underlyingActor.availablePositions
    val planet = planetManager.underlyingActor.planet
    val pendingAttacks = planetManager.underlyingActor.pendingAttacks
    availablePositionsQueue should have size planetSize

    planet.foreach { row =>
      row should have size 3
      row.foreach(_ shouldNot be(defined))
    }
    if (setCurrentPositionInPlanet) {
      planet(currentPosX)(currentPosY) = Some(Animal(fakeAssignee, isShark = false))
      val filteredQueue = availablePositionsQueue.filterNot(_ == currentPosition)
      availablePositionsQueue.clear()
      availablePositionsQueue.enqueue(filteredQueue.toSeq: _*)
    }
  }

  "Getting a random position" ignore {
    "return a random free position and mark it as occupied if there is a free position" in new Setup {
      val result = (planetManager ? GetRandomPosition(fakeAssignee)).futureValue
      result shouldBe a[Some[_]]

      val pos = result.asInstanceOf[Option[Position]].get
      availablePositionsQueue should have size planetSize - 1
      planet(pos.row)(pos.column) shouldBe Some(Animal(fakeAssignee, isShark = false))
    }

    "return None if there is no free position" in new Setup {
      availablePositionsQueue.clear()
      val result = (planetManager ? GetRandomPosition(fakeAssignee)).futureValue
      result shouldBe None
    }

    "consume all the availablePositions that are marked as busy in the planet until an available one is found" in new Setup {
      val nextRandomPosition = availablePositionsQueue.front
      planet(nextRandomPosition.row)(nextRandomPosition.column) = Some(Animal(TestActorRef(new TestActor), isShark = false))
      val result = (planetManager ? GetRandomPosition(fakeAssignee)).futureValue

      result shouldBe a[Some[_]]
      val pos = result.asInstanceOf[Option[Position]].get
      availablePositionsQueue should have size planetSize - 2
      planet(pos.row)(pos.column) shouldBe Some(Animal(fakeAssignee, isShark = false))
    }

    "return None and empty the availablePosition stack if there are free positions in the availablePosition but they are all flagged as busy in the planet" in new Setup {
      availablePositionsQueue.toList.foreach {
        case Position(row, column) => planet(row)(column) = Some(Animal(TestActorRef(new TestActor), isShark = false))
      }

      val result = (planetManager ? GetRandomPosition(fakeAssignee)).futureValue
      result shouldBe None
      availablePositionsQueue shouldBe empty
    }
  }

  "Asking for a new move" ignore {

    "requested by a fish" should {

      "return a random position out of the available four surrounding the current position, mark it as busy and free the current one" in new Setup(setCurrentPositionInPlanet = true) {
        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = false)).futureValue
        result shouldBe a[Some[_]]
        val move = result.asInstanceOf[Option[MoveToPosition]].get
        Set(
          Position(currentPosX - 1, currentPosY),
          Position(currentPosX, 2),
          Position(0, currentPosY),
          Position(currentPosX, currentPosY + 1)
        ) should contain(move.position)

        planet(currentPosition.row)(currentPosition.column) shouldNot be(defined)
        planet(move.position.row)(move.position.column) shouldBe defined

        availablePositionsQueue should contain(currentPosition)
      }

      "return a random position out of the available 3 surrounding the current position, mark it as busy and free the current one" in new Setup(setCurrentPositionInPlanet = true) {
        planet(currentPosX - 1)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = false))

        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = false)).futureValue
        result shouldBe a[Some[_]]
        val newPosition = result.asInstanceOf[Option[MoveToPosition]].get.position
        Set(
          Position(currentPosX, 2),
          Position(0, currentPosY),
          Position(currentPosX, currentPosY + 1)
        ) should contain(newPosition)

        planet(currentPosition.row)(currentPosition.column) shouldNot be(defined)
        planet(newPosition.row)(newPosition.column) shouldBe defined

        availablePositionsQueue should contain(currentPosition)
      }

      "return a random position out of the available 2 surrounding the current position, mark it as busy and free the current one" in new Setup(setCurrentPositionInPlanet = true) {
        planet(currentPosX - 1)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = false))
        planet(currentPosX)(2) = Some(Animal(TestActorRef(new TestActor), isShark = true))

        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = false)).futureValue
        result shouldBe a[Some[_]]
        val newPosition = result.asInstanceOf[Option[MoveToPosition]].get.position
        Set(
          Position(0, currentPosY),
          Position(currentPosX, currentPosY + 1)
        ) should contain(newPosition)

        planet(currentPosition.row)(currentPosition.column) shouldNot be(defined)
        planet(newPosition.row)(newPosition.column) shouldBe defined

        availablePositionsQueue should contain(currentPosition)
      }

      "return a the only available position surrounding the current position, mark it as busy and free the current one" in new Setup(setCurrentPositionInPlanet = true) {
        planet(currentPosX - 1)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = false))
        planet(currentPosX)(2) = Some(Animal(TestActorRef(new TestActor), isShark = true))
        planet(0)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = false))

        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = false)).futureValue
        result shouldBe a[Some[_]]
        val newPosition = result.asInstanceOf[Option[MoveToPosition]].get.position
        Set(
          Position(currentPosX, currentPosY + 1)
        ) should contain(newPosition)

        planet(currentPosition.row)(currentPosition.column) shouldNot be(defined)
        planet(newPosition.row)(newPosition.column) shouldBe defined

        availablePositionsQueue should contain(currentPosition)
      }

      "return None if there is no available position surrounding the current position" in new Setup(setCurrentPositionInPlanet = true) {
        planet(currentPosX - 1)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = false))
        planet(currentPosX)(2) = Some(Animal(TestActorRef(new TestActor), isShark = false))
        planet(0)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = false))
        planet(currentPosX)(currentPosY + 1) = Some(Animal(TestActorRef(new TestActor), isShark = false))

        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = false)).futureValue
        result shouldBe None

        planet(currentPosition.row)(currentPosition.column) shouldBe defined

        availablePositionsQueue shouldNot contain(currentPosition)

      }
    }

    "requested by a shark" should {

      "return an Attack move out of the available four surrounding the current position and mark it as a pending attack" in new Setup(setCurrentPositionInPlanet = true) {
        val fish1 = TestActorRef(new TestActor)
        val fish2 = TestActorRef(new TestActor)
        planet(currentPosX - 1)(currentPosY) = Some(Animal(fish1, isShark = false))
        planet(currentPosX)(2) = Some(Animal(fish2, isShark = false))
        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = true)).futureValue
        result shouldBe a[Some[_]]
        val move = result.asInstanceOf[Option[AttackFish]].get

        Set(
          AttackFish(fish1, Position(currentPosX - 1, currentPosY)),
          AttackFish(fish2, Position(currentPosX, 2))
        ) should contain(move)

        planet(currentPosition.row)(currentPosition.column) shouldBe defined
        pendingAttacks.values should contain(move)
      }

      "return a MoveToPosition move if there are no fish around and at least an available position, mark it as busy and free its current one" in new Setup(setCurrentPositionInPlanet = true) {
        planet(currentPosX - 1)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = true))
        planet(currentPosX)(2) = Some(Animal(TestActorRef(new TestActor), isShark = true))
        planet(currentPosX)(currentPosY + 1) = Some(Animal(TestActorRef(new TestActor), isShark = true))

        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = true)).futureValue
        result shouldBe a[Some[_]]
        val move = result.asInstanceOf[Option[MoveToPosition]].get
        move shouldBe MoveToPosition(Position(0, currentPosY))

        planet(currentPosition.row)(currentPosition.column) shouldNot be(defined)
        planet(move.position.row)(move.position.column) shouldBe defined

        availablePositionsQueue should contain(currentPosition)
      }

      "return None if there is no available position surrounding the current position (shark surrounded by sharks)" in new Setup(setCurrentPositionInPlanet = true) {
        planet(currentPosX - 1)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = true))
        planet(currentPosX)(2) = Some(Animal(TestActorRef(new TestActor), isShark = true))
        planet(0)(currentPosY) = Some(Animal(TestActorRef(new TestActor), isShark = true))
        planet(currentPosX)(currentPosY + 1) = Some(Animal(TestActorRef(new TestActor), isShark = true))

        val result = (planetManager ? GetNextMove(fakeAssignee, currentPosition, asShark = true)).futureValue
        result shouldBe None

        planet(currentPosition.row)(currentPosition.column) shouldBe defined

        availablePositionsQueue shouldNot contain(currentPosition)
      }

    }

  }

}
