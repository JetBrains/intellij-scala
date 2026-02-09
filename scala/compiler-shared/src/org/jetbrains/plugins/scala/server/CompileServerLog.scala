package org.jetbrains.plugins.scala.server

import java.nio.file.Path

object CompileServerLog {
  final val LogFileName = "scala-compile-server.log"

  def logFilePath(logDir: Path): Path = logDir.resolve(LogFileName)
}
