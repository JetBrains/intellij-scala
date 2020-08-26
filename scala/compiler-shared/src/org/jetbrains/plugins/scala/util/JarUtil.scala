package org.jetbrains.plugins.scala.util

import java.io.File

object JarUtil {

  case class JarFileWithName(file: File, name: String)

  final val JarExtension = ".jar"

  def collectJars(files: collection.Seq[File]): collection.Seq[JarFileWithName] =
    for {
      file <- files
      name = file.getName
      if name.endsWith(JarExtension)
    } yield JarFileWithName(file, name)
}
