package org.jetbrains.plugins.scala.project

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle

object DebuggingInfoLevelDescription {
  @Nls
  def get(level: DebuggingInfoLevel): String = level match {
    case DebuggingInfoLevel.None        => ScalaBundle.message("debug.info.level.none")
    case DebuggingInfoLevel.Source      => ScalaBundle.message("debug.info.level.source")
    case DebuggingInfoLevel.Line        => ScalaBundle.message("debug.info.level.source.and.line.number")
    case DebuggingInfoLevel.Vars        => ScalaBundle.message("debug.info.level.source.line.number.and.local.variable")
    case DebuggingInfoLevel.Notailcalls => ScalaBundle.message("debug.info.level.complete.no.tail.call.optimization")
  }
}
