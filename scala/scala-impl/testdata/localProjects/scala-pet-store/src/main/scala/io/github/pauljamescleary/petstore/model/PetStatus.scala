package io.github.pauljamescleary.petstore.model

sealed trait PetStatus extends Product with Serializable
case object Available extends PetStatus
case object Pending extends PetStatus
case object Adopted extends PetStatus

object PetStatus {
  def apply(name: String): PetStatus = name match {
    case "Available" => Available
    case "Pending" => Pending
    case "Adopted" => Adopted
  }

  def nameOf(status: PetStatus): String = status match {
    case Available => "Available"
    case Pending => "Pending"
    case Adopted => "Adopted"
  }
}
