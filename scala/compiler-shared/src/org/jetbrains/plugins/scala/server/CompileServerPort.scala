package org.jetbrains.plugins.scala.server

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.util.Try

sealed trait CompileServerPort {
  def forCommunication: Int = this match {
    case CompileServerPort.Local(port) => port
    case CompileServerPort.Remote(local, _) => local
  }

  def forToken: Int = this match {
    case CompileServerPort.Local(port) => port
    case CompileServerPort.Remote(_, remote) => remote
  }
}

object CompileServerPort {
  final case class Local(port: Int) extends CompileServerPort
  final case class Remote(local: Int, remote: Int) extends CompileServerPort

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
