package org.jetbrains.plugins.scala.annotator.hints

case class Corners(topLeft: Boolean,    topRight: Boolean,
                   bottomLeft: Boolean, bottomRight: Boolean) {

  def intersect(other: Corners): Corners = Corners(topLeft && other.topLeft,       topRight && other.topRight,
                                                   bottomLeft && other.bottomLeft, bottomRight && other.bottomRight)

  def union(other: Corners): Corners     = Corners(topLeft || other.topLeft,       topRight || other.topRight,
                                                   bottomLeft || other.bottomLeft, bottomRight || other.bottomRight)
}

object Corners {
  val All: Corners    = Corners(true, true,
                                true, true)

  val Left: Corners   = Corners(true, false,
                                true, false)

  val Right: Corners  = Corners(false, true,
                                false, true)

  val Top: Corners    = Corners(true,  true,
                                false, false)

  val Bottom: Corners = Corners(false, false,
                                true,  true)

  val None: Corners   = Corners(false, false,
                                false, false)
}
