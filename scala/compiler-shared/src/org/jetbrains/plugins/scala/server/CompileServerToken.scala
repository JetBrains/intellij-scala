package org.jetbrains.plugins.scala.server

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

object CompileServerToken {

  /** dulicated in `org.jetbrains.plugins.scala.nailgun.NailgunRunner#tokenPathFor(int)` */
  def tokenPathForPort(scalaCompileServerSystemDir: Path, port: Int): Path =
    scalaCompileServerSystemDir
      .resolve("tokens")
      .resolve(port.toString)

  def tokenForPort(scalaCompileServerSystemDir: Path, port: Int): Option[String] =
     readStringFrom(tokenPathForPort(scalaCompileServerSystemDir, port))

  private def readStringFrom(path: Path): Option[String] =
    if (path.toFile.exists)
      Some(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))
    else None
}
