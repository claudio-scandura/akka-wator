package actors.fsm

import java.util.concurrent.TimeUnit

import actors.Position
import actors.fsm.Cell.{Fill, Ko, Ok}
import akka.actor.ActorSystem
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.libs.json.JsObject

class CellAsyncSpec extends  TestKit(ActorSystem("TestWatorSystem")) with WordSpecLike with BeforeAndAfterAll with ScalaFutures with Matchers with Eventually {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  trait Setup {
    val initialRow = 1
    val initialColumn = 1
    val position = Position(0, 0)
    val newPositionAvailable: Option[Position] = Some(Position(1, 0))
    val wsOutProbe = TestProbe()
    val cell = TestActorRef(new Cell(position, 3, 3, Some(wsOutProbe.ref)))
  }

  "An empty actor cell" should {

    "send a notificationMessage to the UI actor" when {

      "receiving a Fill(shark) message" in new Setup {
        (cell ? Fill(Shark)).futureValue shouldBe Ok
        val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
        (result \ "animal").as[String] shouldBe "shark"
        (result \ "oldPosition").as[Position] shouldBe position
      }

      "receiving a Fill(Fish)" in new Setup {
        (cell ? Fill(Fish)).futureValue shouldBe Ok
        val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
        (result \ "animal").as[String] shouldBe "fish"
        (result \ "oldPosition").as[Position] shouldBe position
      }
    }

    "do nothing" when {

      "receiving any other message " in new Setup {
        (cell ? "Do something else!").futureValue shouldBe Ko
        wsOutProbe.expectNoMsg
      }
    }
  }

}
