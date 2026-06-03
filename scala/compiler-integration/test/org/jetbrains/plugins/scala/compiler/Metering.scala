package org.jetbrains.plugins.scala.compiler

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.math.BigDecimal.RoundingMode

object Metering {

  def metered[A](action: => A)
                (implicit scaleConfig: ScaleConfig, handler: MeteringHandler): A = {
    val (elapsedNanos, result) = meteredNanos(action)
    handleNanos(elapsedNanos)
    result
  }

  def benchmarked(times: Int)
                 (action: => Unit)
                 (implicit scalaConfig: ScaleConfig, handler: MeteringHandler): Unit = {
    val (elapsedNanos, _) = meteredNanos(for (_ <- Range(0, times)) action)
    val elapsedNanosPerAction = elapsedNanos / times
    handleNanos(elapsedNanosPerAction)
  }

  private def meteredNanos[A](action: => A): (Long, A) = {
    val startTime = System.nanoTime()
    val result = action
    val elapsedNanos = System.nanoTime() - startTime
    (elapsedNanos, result)
  }

  private def handleNanos(nanos: Long)
                         (implicit scaleConfig: ScaleConfig, handler: MeteringHandler): Unit = {
    val ScaleConfig(scale, unit) = scaleConfig
    val elapsed = FiniteDuration(nanos, TimeUnit.NANOSECONDS)
      .toUnit(unit)
    val elapsedScaled = BigDecimal(elapsed)
      .setScale(scale, RoundingMode.HALF_UP)
      .toDouble
    handler.handle(elapsedScaled, scaleConfig.unit)
  }

  case class ScaleConfig(scale: Int, unit: TimeUnit)

  trait MeteringHandler {
    def handle(time: Double, unit: TimeUnit): Unit
  }
}
