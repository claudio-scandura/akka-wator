package model

sealed trait CellContent {
  def isEmpty: Boolean
}

sealed trait Animal extends CellContent {
  override def isEmpty: Boolean = false
  val aliveFor: Int
}

case object Water extends CellContent {
  override def isEmpty: Boolean = true
}

case class Fish(aliveFor: Int = 1) extends Animal

case class Shark(aliveFor: Int = 1, didNotEatFor: Int = 1) extends Animal

case class LifeDeathParameters(sharkStarvation: SharkStarvation, sharkReproduction: SharkReproduction, fishReproduction: FishReproduction)

case class SharkStarvation(afterTicks: Int) extends AnyVal

case class SharkReproduction(afterTicks: Int) extends AnyVal

case class FishReproduction(afterTicks: Int) extends AnyVal

object LifeDeathParameters {
  def default = new LifeDeathParameters(SharkStarvation(10), SharkReproduction(20), FishReproduction(8))
}
