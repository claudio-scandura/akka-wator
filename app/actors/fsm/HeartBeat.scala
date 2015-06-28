package actors.fsm

import java.util.concurrent.TimeUnit

import actors.Fish.Tick

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.Random


trait HeartBeat {
  this: Cell =>

  val randomInt = Random.nextInt((500 to 2500).length)

  def startHeartBeat = context.system.scheduler.schedule(
    FiniteDuration(randomInt, TimeUnit.MILLISECONDS),
    FiniteDuration(heartBeatFrequency, TimeUnit.MILLISECONDS),
    self,
    Tick
  )

}