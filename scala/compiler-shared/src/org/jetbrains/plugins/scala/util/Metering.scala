package org.jetbrains.plugins.scala.util

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.math.BigDecimal.RoundingMode

object Metering {

  def metered[A](action: => A)
                (implicit scaleConfig: ScaleConfig, handler: MeteringHandler): A = {
    val ScaleConfig(scale, unit) = scaleConfig
    val startTime = System.nanoTime()
    val result = action
    val endTime = System.nanoTime() - startTime
    val elapsed = FiniteDuration(endTime, TimeUnit.NANOSECONDS)
      .toUnit(unit)
    val elapsedScaled = BigDecimal(elapsed)
      .setScale(scale, RoundingMode.HALF_UP)
      .toDouble
    handler.handle(elapsedScaled, scaleConfig.unit)
    result
  }

  case class ScaleConfig(scale: Int, unit: TimeUnit)

  trait MeteringHandler {
    def handle(time: Double, unit: TimeUnit): Unit
  }
}
