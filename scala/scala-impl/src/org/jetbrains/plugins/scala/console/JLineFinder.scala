package org.jetbrains.plugins.scala.console

import java.io.File

import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.sbt.RichFile

import scala.util.{Failure, Success, Try}

private[console]
object JLineFinder {
  private val Log = Logger.getInstance(this.getClass)

  private val ScalaCompiler = "scala-compiler"
  //this is a dependency of scala-compiler-2.13.0
  //see https://mvnrepository.com/artifact/org.scala-lang/scala-compiler/2.13.0
  private val JLineVersionInScala213 = "2.14.6"
  val JLineJarName = s"jline-$JLineVersionInScala213.jar"

  def locateJLineJar(classPath: Seq[File]): Option[File] = Try {
    for {
      compilerJar <- classPath.find(_.getName.startsWith(ScalaCompiler))
      result <- findInSameFolder(compilerJar)
        .orElse(findInIvy(compilerJar))
        .orElse(findInMaven(compilerJar))
    } yield result
  } match {
    case Success(fileOpt) => fileOpt
    case Failure(ex) =>
      Log.error(s"An error occurred in searching of $JLineJarName", ex)
      None
  }

  private def findInSameFolder(compilerJar: File): Option[File] = for {
    parent <- compilerJar.parent
    jLineJar <- (parent / JLineJarName).asFile
    _ = Log.info(s"Found $JLineJarName in same folder with $ScalaCompiler")
  } yield jLineJar

  //location of `scala-compiler-x.x.x.jar` : .ivy2/cache/org.scala-lang/scala-compiler/jars
  //location of `jline-x.x.x.jar`          : .ivy2/cache/jline/jline/jars
  private def findInIvy(compilerJar: File): Option[File] = for {
    cacheFolder <- compilerJar.parent(level = 4)
    jLineFolder <- (cacheFolder / "jline" / "jline" / "jars").asDir
    jLineJar <- (jLineFolder / JLineJarName).asFile
    _ = Log.info(s"Found $JLineJarName in ivy cache")
  } yield jLineJar

  //location of `scala-compiler-x.x.x.jar` : .m2/repository/org/scala-lang/scala-compiler/x.x.x
  //location of `jline-x.x.x.jar`          : .m2/repository/jline/jline/x.x.x
  private def findInMaven(compilerJar: File): Option[File] = for {
    repositoryFolder <- compilerJar.parent(level = 5)
    jLineFolder <- (repositoryFolder / "jline" / "jline" / JLineVersionInScala213).asDir
    jLineJar <- (jLineFolder / JLineJarName).asFile
    _ = Log.info(s"Found $JLineJarName in maven cache")
  } yield jLineJar
}