package org

case class Index private (time: Short)

object Index:
  def fromVelocity (velocity: Double): Index =
    ???