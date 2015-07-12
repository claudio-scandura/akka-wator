package actors.fsm

import java.util.concurrent.TimeUnit

import actors.Fish.Tick
import actors.fsm.Cell.{Fill, Ko, Ok}
import akka.actor._
import akka.pattern.ask
import akka.testkit.{TestActorRef, TestKit, TestProbe}
import model.{CellContent, _}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.libs.json.JsObject

import scala.concurrent.duration.FiniteDuration

class CellAsyncSpec extends TestKit(ActorSystem("TestWatorSystem")) with WordSpecLike with BeforeAndAfterAll with ScalaFutures with Matchers with Eventually {

  implicit val timeout = akka.util.Timeout(2000, TimeUnit.MILLISECONDS)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  trait Setup {
    val maxDuration = FiniteDuration(1L, TimeUnit.SECONDS)
    val initialState: CellContent = Water

    val northState: CellContent = Water
    val southState: CellContent = Water
    val eastState: CellContent = Water
    val westState: CellContent = Water

    val initialRow = 1
    val initialColumn = 1
    val position = Position(0, 0)
    lazy val stubRandomNumber = 0
    val newPositionAvailable: Option[Position] = Some(Position(1, 0))
    val wsOutProbe = TestProbe()
    val neighbourProbe = TestProbe()

    val sharkStarvation: SharkStarvation = SharkStarvation(10)
    val sharkReproduction: SharkReproduction = SharkReproduction(5)
    val fishReproduction: FishReproduction = FishReproduction(3)

    lazy val lifeDeathParams = LifeDeathParameters(sharkStarvation, sharkReproduction, fishReproduction)

    class CellForTest(position: Position, rows: Int, columns: Int, wsOut: Option[ActorRef], neighbourProbeSelection: ActorSelection, initialState: CellContent) extends Cell(position, rows, columns, wsOut, lifeDeathParams = lifeDeathParams) {
      import scala.collection.mutable.{Map => MutableMap}

      override def nextRandomNumber(range: Range): Int = stubRandomNumber

      override private[fsm] def actorRefFor(position: Position): ActorSelection = neighbourProbeSelection

      override private[fsm] lazy val neighboursRefs: Map[Direction, ActorSelection] = Map(
        North -> neighbourProbeSelection,
        West -> neighbourProbeSelection,
        East -> neighbourProbeSelection,
        South -> neighbourProbeSelection
      )

      override def startHeartBeat: Cancellable = null

      override private[fsm] val neighbours: MutableMap[Direction, CellContent] = {
        MutableMap(
          North -> northState,
          South -> southState,
          East -> eastState,
          West -> westState
        )
      }


      content = initialState

      override def receive: Receive = initialState match {
        case Water => super.water
        case f: Fish => super.fish
        case s: Shark => super.shark
        case other => fail(s"invalid initial state for Cell: $other")
      }
    }

    lazy val cell = TestActorRef(new CellForTest(position, 3, 3, Some(wsOutProbe.ref), ActorSelection(neighbourProbe.ref, ""), initialState), "CellForTest")

    def startAndStopCell(testBody: => Unit): Unit = {
      cell.start()
      try {
        testBody
      } finally {
        cell.stop()
      }
    }
  }

  "An empty actor cell" should {

    "respond Ok, send a notificationMessage to the UI actor and advertise the new state to the neigbhours" when {

      "receiving a Fill(shark) message" in new Setup {
        startAndStopCell {
          (cell ? Fill(Shark())).futureValue shouldBe Ok

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "shark"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Shark())
        }
      }

      "receiving a Fill(Fish)" in new Setup {
        startAndStopCell {
          (cell ? Fill(Fish())).futureValue shouldBe Ok

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "fish"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Fish())
        }
      }
    }

    "do nothing" when {

      "receiving a Tick message " in new Setup {
        startAndStopCell {
          cell ! Tick
          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }

      "receiving a Fill(shark) message but the shark has starved" in new Setup {
        startAndStopCell {
          cell ! Fill(Shark(2, sharkStarvation.afterTicks + 1))

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }

      "receiving an unknown message " in new Setup {
        startAndStopCell {
          (cell ? "Do something else!").futureValue shouldBe Ko
          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }
    }
  }

  "An actor cell containing a fish" should {

    "send a notificationMessage to the UI actor and advertise the new state to the neigbhours" when {

      "receiving a Fill(shark) message" in new Setup {
        override val initialState: CellContent = Fish()
        startAndStopCell {
          (cell ? Fill(Shark())).futureValue shouldBe Ok

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "shark"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Shark())
        }
      }

      "receiving a Fill(shark) message and the shark is starving" in new Setup {
        override val initialState: CellContent = Fish()
        startAndStopCell {
          (cell ? Fill(Shark(1, sharkStarvation.afterTicks + 1))).futureValue shouldBe Ok

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "shark"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Shark())
        }
      }


      "receiving a Tick message which results in a transition of the fish in another cell" in new Setup {
        override val initialState: CellContent = Fish(1)
        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Fish(2)))
          neighbourProbe.reply(Ok)

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "water"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Water)
        }
      }
    }

    "spawn a new fish if there is an empty neighbour cell" when {

      "receiving a Tick message" in new Setup {
        override val initialState: CellContent = Fish(fishReproduction.afterTicks)
        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Fish()))
          neighbourProbe.reply(Ok)

          wsOutProbe.expectNoMsg(maxDuration)
          neighbourProbe.expectNoMsg(maxDuration)
        }
      }
    }

    "do nothing" when {

      "receiving a Tick message which does not result in a transition of the fish in another cell because it has already been taken" in new Setup {
        override val initialState: CellContent = Fish(1)
        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Fish(2)))
          neighbourProbe.reply(Ko)

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }

      "receiving a Tick message which does not result in a transition of the fish in another cell because there is none available" in new Setup {
        override val initialState: CellContent = Fish()
        override val northState: CellContent = Fish()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Fish()
        override val westState: CellContent = Shark()

        startAndStopCell {
          cell ! Tick

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }

      "receiving a Tick message which does not result in a new fish being spawned because there is no cell available" in new Setup {
        override val initialState: CellContent = Fish(fishReproduction.afterTicks)
        override val northState: CellContent = Fish()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Fish()
        override val westState: CellContent = Shark()

        startAndStopCell {
          cell ! Tick

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }
    }

    "respond Ko and not send other messages" when {

      "receiving a Fill(Fish()) message" in  new Setup {
        override val initialState: CellContent = Fish()

        startAndStopCell {
          (cell ? Fill(Fish())).futureValue shouldBe Ko

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }
    }

  }

  "An actor cell containing a shark" should {

    "send a notificationMessage to the UI actor and advertise the new state to the neigbhours" when {

      "receiving a Tick message which results in a transition of the Shark() in another cell as there is no fish around and one free cell" in new Setup {
        override val initialState: CellContent = Shark(1, 1)
        override val northState: CellContent = Shark()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Water
        override val westState: CellContent = Shark()

        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Shark(2, 2)))
          neighbourProbe.reply(Ok)

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "water"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Water)
        }
      }

      "receiving a Tick message which results in a transition of the Shark() in another cell as there is a fish around" in new Setup {
        override val initialState: CellContent = Shark(1, 1)
        override val northState: CellContent = Shark()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Fish()
        override val westState: CellContent = Shark()

        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Shark(2, 2)))
          neighbourProbe.reply(Ok)

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "water"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Water)
        }
      }

      "receiving a Tick message which results in the shark dieing as it did not manage to eat nor to move anywhere and it starved" in new Setup {
        override val initialState: CellContent = Shark(1, sharkStarvation.afterTicks - 1)
        override val northState: CellContent = Shark()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Fish()
        override val westState: CellContent = Shark()

        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Shark(2, sharkStarvation.afterTicks)))
          neighbourProbe.reply(Ko)

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "water"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Water)
        }
      }
    }

    "spawn a new shark if there is an empty neighbour cell" when {

      "receiving a Tick message" in new Setup {
        override val initialState: CellContent = Shark(sharkReproduction.afterTicks)
        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Shark()))
          neighbourProbe.reply(Ok)

          wsOutProbe.expectNoMsg(maxDuration)
          neighbourProbe.expectNoMsg(maxDuration)
        }
      }
    }

    "kill shark if it did not eat for a number of ticks equivalent to the limit and there is no fish around" when {

      "receiving a Tick message" in new Setup {
        override val initialState: CellContent = Shark(2, sharkStarvation.afterTicks)
        override val northState: CellContent = Shark()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Water
        override val westState: CellContent = Shark()

        startAndStopCell {
          cell ! Tick

          val result = wsOutProbe.expectMsgAnyClassOf(classOf[JsObject])
          (result \ "animal").as[String] shouldBe "water"
          (result \ "position").as[Position] shouldBe position

          neighbourProbe.expectMsg[CellContent](Water)
        }
      }
    }

    "do nothing" when {

      "receiving a Tick message which does not result in a transition of the shark in another cell because it has already been taken" in new Setup {
        override val initialState: CellContent = Shark(1, 1)
        startAndStopCell {
          cell ! Tick
          neighbourProbe.expectMsg(Fill(Shark(2, 2)))
          neighbourProbe.reply(Ko)

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }

      "receiving a Tick message which does not result in a transition of the shark in another cell because all the surrounding cells are occupied by sharks" in new Setup {
        override val initialState: CellContent = Shark()
        override val northState: CellContent = Shark()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Shark()
        override val westState: CellContent = Shark()
          startAndStopCell {
            cell ! Tick

            wsOutProbe.expectNoMsg(maxDuration)

            neighbourProbe.expectNoMsg(maxDuration)
          }
      }

      "receiving a Tick message which does not result in a new shark being spawned because there is no cell available" in new Setup {
        override val initialState: CellContent = Fish(sharkReproduction.afterTicks)
        override val northState: CellContent = Shark()
        override val eastState: CellContent = Shark()
        override val southState: CellContent = Shark()
        override val westState: CellContent = Shark()

        startAndStopCell {
          cell ! Tick

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }
    }

      "respond Ko and not send other messages" when {

      "receiving a Fill(Fish()) message" in  new Setup {
        override val initialState: CellContent = Shark()

        startAndStopCell {
          (cell ? Fill(Fish())).futureValue shouldBe Ko

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }

      "receiving a Fill(Shark()) message" in  new Setup {
        override val initialState: CellContent = Shark()

        startAndStopCell {
          (cell ? Fill(Shark())).futureValue shouldBe Ko

          wsOutProbe.expectNoMsg(maxDuration)

          neighbourProbe.expectNoMsg(maxDuration)
        }
      }
    }
  }


}
