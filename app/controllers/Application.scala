package controllers

import actors.Orchestrator.{StopSimulation, StartSimulation}
import akka.actor._
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, Controller, WebSocket}

object Application extends Controller {

  def index: Action[AnyContent] = Action { implicit request =>
    Ok(views.html.index())
  }


  def ws: WebSocket[JsValue, JsValue] = WebSocket.acceptWithActor[JsValue, JsValue] {
    implicit request => out =>
      WSHandler.props(out)
  }


}

object WSHandler {
  def props(out: ActorRef) = Props(new WSHandler(out))
}

object SimulationParameters {
  implicit val formats = Json.format[SimulationParameters]
}

class WSHandler(out: ActorRef) extends Actor with ActorLogging {
  def receive = {
    case wsRequest: JsValue if wsRequest.validate[SimulationParameters].isSuccess =>
      wsRequest.validate[SimulationParameters].asOpt.fold(
        out ! Json.obj("status" -> "KO")
      ){ paramters =>
        val kickIt = StartSimulation(paramters, out)
        Akka.system.actorSelection("/user/orchestrator") ! kickIt
      out ! Json.obj("status" -> "OK")
      }

    case stopRequest: JsValue if ((stopRequest \ "stop").asOpt[Boolean]).isDefined =>
      Akka.system.actorSelection("/user/orchestrator") ! StopSimulation

    case _ => log.info("No SHIT!")
  }
}

case class SimulationParameters(rows: Int, columns: Int, sharkPopulation: Int, fishPopulation: Int, chronosFrequency: Int)