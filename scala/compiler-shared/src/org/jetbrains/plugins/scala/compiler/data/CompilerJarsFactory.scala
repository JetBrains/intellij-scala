package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.{compilerVersionIn, containsScala3}
import org.jetbrains.plugins.scala.util.JarUtil
import org.jetbrains.plugins.scala.util.JarUtil.JarFileWithName

import java.io.File

object CompilerJarsFactory {

  sealed trait CompilerJarsResolveError

  object CompilerJarsResolveError {
    case class NotFound(kind: String) extends CompilerJarsResolveError
    case class DuplicatesFound(kind: String, duplicates: Seq[JarFileWithName]) extends CompilerJarsResolveError
    case class FilesDoNotExist(files: Seq[File]) extends CompilerJarsResolveError
  }

  def fromFiles(
    compilerClasspathJars: Seq[File],
    compilerBridgeJar: Option[File],
  ): Either[CompilerJarsResolveError, CompilerJars] = {
    val withName = JarUtil.collectJars(compilerClasspathJars)
    fromJarFiles(withName, compilerBridgeJar)
  }

  private def fromJarFiles(
    compilerClasspathJars: Seq[JarFileWithName],
    compilerBridgeJar: Option[File]
  ): Either[CompilerJarsResolveError, CompilerJars] = {
    val ioFiles = compilerClasspathJars.map(_.file)
    val isScala3 = containsScala3(ioFiles)
    val compilerPrefix =
      if (isScala3) "scala3"
      else "scala"


    val init: Either[CompilerJarsResolveError, Seq[JarFileWithName]] = Right(Seq.empty)
    val libraryJarsE = Set("scala-library", s"$compilerPrefix-library").foldLeft(init) { (acc, kind) =>
      for {
        jars <- acc
        jar <- find(compilerClasspathJars, kind)
      } yield jars :+ jar
    }

    for {
      libraryJars <- libraryJarsE
      compilerJars = compilerClasspathJars.filterNot(libraryJars.contains)
      compilerJar <- find(compilerClasspathJars, s"$compilerPrefix-compiler")
      _           <- scalaReflectIfRequired(compilerJar, compilerJars)
    } yield CompilerJars(
      libraryJars = libraryJars.map(_.file),
      compilerJars = compilerJars.map(_.file),
      compilerJar = compilerJar.file,
      compilerBridgeJar,
    )
  }

  private def find(files: Seq[JarFileWithName], kind: String): Either[CompilerJarsResolveError, JarFileWithName] = {
    val filesOfKind = files.filter(_.name.startsWith(kind)).distinct
    filesOfKind match {
      case Seq(file) => Right(file)
      case Seq() => Left(CompilerJarsResolveError.NotFound(kind))
      case duplicates => Left(CompilerJarsResolveError.DuplicatesFound(kind, duplicates))
    }
  }

  private def scalaReflectIfRequired(compiler: JarFileWithName, compilerJars: Seq[JarFileWithName]): Either[CompilerJarsResolveError, Unit] =
    if (compilerVersionIn(compiler.file, "2.10")) find(compilerJars, "scala-reflect").map(_ => ())
    else Right(())
}
