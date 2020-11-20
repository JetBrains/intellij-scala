package org.jetbrains.plugins.scala.compilationCharts.ui

import scala.concurrent.duration.{DurationDouble, FiniteDuration}

final case class Zoom(durationStep: FiniteDuration,
                      durationLabelPeriod: Int) {

  private lazy val scale = 5.5e7 / durationStep.toNanos

  def toPixels(duration: FiniteDuration): Double = scale * duration.toMillis

  def fromPixels(pixels: Double): FiniteDuration = (pixels / scale).millis
}
