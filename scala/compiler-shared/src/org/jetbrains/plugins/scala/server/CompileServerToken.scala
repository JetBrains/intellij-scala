package org.jetbrains.plugins.scala.server

import java.nio.file.{Files, Path, Paths}

object CompileServerToken {

  /** dulicated in [[org.jetbrains.plugins.scala.nailgun.NailgunRunner#tokenPathFor(int)]] */
  def tokenPathForPort(port: Int): Path =
    Paths.get(System.getProperty("user.home"), ".idea-build", "tokens", port.toString)

   def tokenForPort(port: Int): Option[String] =
     readStringFrom(tokenPathForPort(port))

  private def readStringFrom(path: Path): Option[String] =
    if (path.toFile.exists)
      Some(new String(Files.readAllBytes(path)))
    else None
}
