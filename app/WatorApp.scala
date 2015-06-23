import actors._
import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.{Application, GlobalSettings}

object WatorApp extends GlobalSettings {



  override def beforeStart(app: Application): Unit = {
    super.beforeStart(app)
    Akka.system.actorOf(Props(new Orchestrator), "orchestrator")
  }

}

