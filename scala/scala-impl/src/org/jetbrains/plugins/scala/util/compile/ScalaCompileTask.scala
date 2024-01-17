package org.jetbrains.plugins.scala.util.compile

import com.intellij.openapi.compiler.{CompileContext, CompileTask}
import com.intellij.openapi.diagnostic.Logger

import scala.concurrent.duration._

trait ScalaCompileTask extends CompileTask {
  final override def execute(context: CompileContext): Boolean = logExecutionTime {
    run(context)
  }

  protected def run(context: CompileContext): Boolean

  protected def presentableName: String

  protected def log: Logger

  private def logExecutionTime(body: => Boolean): Boolean = {
    val start = System.nanoTime()
    try body
    finally {
      val end = System.nanoTime()
      val millis = (end - start).nanos.toMillis.millis
      val durationString = pretty(millis)
      val message = s"$presentableName - done in $durationString"
      log.info(message)
    }
  }

  private def pretty(duration: FiniteDuration): String = {
    var rest = duration
    val days = rest.toDays.days
    rest -= days
    val hours = rest.toHours.hours
    rest -= hours
    val minutes = rest.toMinutes.minutes
    rest -= minutes
    val seconds = rest.toSeconds.seconds
    rest -= seconds
    val millis = rest.toMillis.millis

    val daysStr = if (days > Duration.Zero) s"${days.length} d, " else ""
    val hoursStr = if (hours > Duration.Zero) s"${hours.length} h, " else ""
    val minutesStr = if (minutes > Duration.Zero) s"${minutes.length} min, " else ""
    val secondsStr = if (seconds > Duration.Zero) s"${seconds.length} sec, " else ""
    val millisStr = s"${millis.length} ms"
    s"$daysStr$hoursStr$minutesStr$secondsStr$millisStr"
  }
}
