package org.jetbrains.plugins.scala.util.compile

import com.intellij.openapi.compiler.{CompileContext, CompileTask, CompilerMessageCategory}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle

import scala.concurrent.duration._

trait ScalaCompileTask extends CompileTask {
  final override def execute(context: CompileContext): Boolean = {
    val start = System.nanoTime()
    try {
      @Nls val message = ScalaBundle.message("scala.compile.task.measure.start", presentableName)
      context.addMessage(CompilerMessageCategory.STATISTICS, message, null, -1, -1)
      run(context)
    } finally {
      val end = System.nanoTime()
      val millis = (end - start).nanos.toMillis.millis
      val durationString = pretty(millis)
      @Nls val message = ScalaBundle.message("scala.compile.task.measure.end", presentableName, durationString)
      context.addMessage(CompilerMessageCategory.STATISTICS, message, null, -1, -1)
    }
  }

  protected def run(context: CompileContext): Boolean

  @Nls
  protected def presentableName: String

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
