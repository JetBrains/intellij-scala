package org.jetbrains.plugins.scala.server

import java.nio.file.{Files, Path}

object CompileServerToken {

  /** dulicated in [[org.jetbrains.plugins.scala.nailgun.NailgunRunner#tokenPathFor(int)]] */
  def tokenPathForPort(buildSystemDir: Path, port: Int): Path =
    buildSystemDir
      .resolve("tokens")
      .resolve(port.toString)

  def tokenForPort(buildSystemDir: Path, port: Int): Option[String] =
     readStringFrom(tokenPathForPort(buildSystemDir, port))

  private def readStringFrom(path: Path): Option[String] =
    if (path.toFile.exists)
      Some(new String(Files.readAllBytes(path)))
    else None
}
