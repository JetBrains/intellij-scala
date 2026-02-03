package org.jetbrains.plugins.scala.server

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

object CompileServerPort {
  final val DefaultPort = 3200
  final val PortFileName = "port.txt"

  def portFilePath(scalaCompileServerSystemDir: Path): Path =
    scalaCompileServerSystemDir.resolve(PortFileName)

  def readPortFile(scalaCompileServerSystemDir: Path): Option[Int] = {
    val path = portFilePath(scalaCompileServerSystemDir)
    Try(Files.readAllBytes(path))
      .map(bytes => new String(bytes, StandardCharsets.UTF_8))
      .toOption
      .flatMap(_.toIntOption)
  }
}
