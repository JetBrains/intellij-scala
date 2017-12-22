package io.github.pauljamescleary.petstore.model

sealed trait OrderStatus extends Product with Serializable
case object Approved extends OrderStatus
case object Delivered extends OrderStatus
case object Placed extends OrderStatus

object OrderStatus {
  def apply(name: String): OrderStatus = name match {
    case "Approved" => Approved
    case "Delivered" => Delivered
    case "Placed" => Placed
  }

  def nameOf(status: OrderStatus): String = status match {
    case Approved => "Approved"
    case Delivered => "Delivered"
    case Placed => "Placed"
  }
}
