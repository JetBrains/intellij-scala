package org.jetbrains.plugins.scala.compilationCharts.ui

import java.awt.{BasicStroke, Stroke}

class LineStroke(val thickness: Float,
                 val dashLength: Option[Float] = None) {

  lazy val toStroke: Stroke = {
    val dashes = dashLength.map(Array(_)).orNull
    new BasicStroke(thickness, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, dashes, 0)
  }
}
