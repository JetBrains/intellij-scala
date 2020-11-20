package org.jetbrains.plugins.scala.compilationCharts.ui

import java.awt.geom.Path2D

class Polygon2D(points: Seq[(Double, Double)]) {

  def toPath2D: Path2D = {
    val path = new Path2D.Double
    points match {
      case Seq((x0, y0), tail@_*) =>
        path.moveTo(x0, y0)
        tail.foreach { case (x, y) => path.lineTo(x, y) }
      case _ =>
    }
    path.closePath()
    path
  }
}
