package org.jetbrains.jps.incremental.scala.data

import java.io.File

import org.jetbrains.jps.incremental.scala.data.CompilerJarsFactory.CompilerJarsResolveError
import org.jetbrains.jps.incremental.scala.{containsDotty, readProperty}

/**
 * @author Pavel Fatin
 */
case class CompilerJars(library: File,
                        compiler: File,
                        extra: Seq[File]) {

  def hasDotty: Boolean =
    containsDotty(extra)

  def allJars: Seq[File] =
    library +: compiler +: extra
}

object CompilerJars extends CompilerJarsFactory {

  val JarExtension = ".jar"

  case class JarFileWithName(file: File, name: String)

  override def fromFiles(files: Seq[File]): Either[CompilerJarsResolveError, CompilerJars] = {
    val jarFiles = collectJars(files)
    fromFiles(jarFiles)
  }

  private[data] def fromFiles(files: Seq[JarFileWithName])(implicit d: DummyImplicit): Either[CompilerJarsResolveError, CompilerJars] = {
    val compilerPrefix = if (containsDotty(files.map(_.file))) "dotty" else "scala"
    for {
      library <- find(files, s"$compilerPrefix-library")
      compiler <- find(files, s"$compilerPrefix-compiler")

      extra = files.filter {
        case `library` | `compiler` => false
        case _                      => true
      }

      _ <- scalaReflect(compiler, extra)
    } yield CompilerJars(
      library.file,
      compiler.file,
      extra.map(_.file)
    )
  }

  private def find(files: Seq[JarFileWithName], kind: String): Either[CompilerJarsResolveError, JarFileWithName] = {
    val filesOfKind = files.filter(_.name.startsWith(kind)).distinct
    filesOfKind match {
      case Seq(file)  => Right(file)
      case Seq()      => Left(CompilerJarsResolveError.NotFound(kind))
      case duplicates => Left(CompilerJarsResolveError.DuplicatesFound(kind, duplicates))
    }
  }

  private def scalaReflect(compiler: JarFileWithName, extra: Seq[JarFileWithName]): Either[CompilerJarsResolveError, Unit] =
    if (versionIn(compiler.file, "2.10")) find(extra, "scala-reflect").map(_ => ())
    else Right(())

  def collectJars(files: Seq[File]): Seq[JarFileWithName] =
    for {
      file <- files
      name = file.getName
      if name.endsWith(JarExtension)
    } yield JarFileWithName(file, name)

  // TODO implement a better version comparison
  def versionIn(compiler: File, versions: String*): Boolean =
    compilerVersion(compiler).exists { version => versions.exists(version.startsWith) }

  private def compilerVersion(compiler: File): Option[String] =
    readProperty(compiler, "compiler.properties", "version.number")
}

