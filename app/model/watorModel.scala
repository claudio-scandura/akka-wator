package model

sealed trait CellContent {
  def isEmpty: Boolean
}

sealed trait Animal extends CellContent {
  override def isEmpty: Boolean = false
}

case object Water extends CellContent {
  override def isEmpty: Boolean = true
}

case object Fish extends Animal

case object Shark extends Animal
