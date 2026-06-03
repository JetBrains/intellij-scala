package org.jetbrains.plugins.scala.util

import java.nio.file.Path

object JarUtil {

  case class JarFileWithName(file: Path, name: String)

  final val JarExtension = ".jar"

  def collectJars(files: Seq[Path]): Seq[JarFileWithName] =
    for {
      file <- files
      name = file.getFileName.toString
      if name.endsWith(JarExtension)
    } yield JarFileWithName(file, name)
}
