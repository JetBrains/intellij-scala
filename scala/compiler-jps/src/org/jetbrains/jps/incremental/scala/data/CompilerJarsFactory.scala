package org.jetbrains.jps.incremental.scala.data

import java.io.File

import org.jetbrains.jps.incremental.scala.data.CompilerJars.JarFileWithName
import org.jetbrains.jps.incremental.scala.data.CompilerJarsFactory.CompilerJarsResolveError

trait CompilerJarsFactory {

  def fromFiles(files: Seq[File]): Either[CompilerJarsResolveError, CompilerJars]
}

object CompilerJarsFactory {

  sealed trait CompilerJarsResolveError
  object CompilerJarsResolveError {
    case class NotFound(kind: String) extends CompilerJarsResolveError
    case class DuplicatesFound(kind: String, duplicates: Seq[JarFileWithName]) extends CompilerJarsResolveError
    case class FilesDoNotExist(files: Seq[File]) extends CompilerJarsResolveError
  }
}
