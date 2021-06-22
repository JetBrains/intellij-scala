package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.DependencyManagerBase.{RichStr, scalaCompilerDescription, scalaLibraryDescription}
import org.jetbrains.plugins.scala.project.sdkdetect.repository.CoursierPaths
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.jetbrains.sbt.project.ProjectStructureDsl.{ScalaSdkAttributes, classes, library, scalaSdkSettings}

trait ProjectStructureExpectedLibrariesOps {

  private def coursierCacheArtifact(relativePath: String): String = {
    val cacheRoot = CoursierPaths.cacheDirectory.getAbsolutePath.stripSuffix("/").stripSuffix("\\")
    cacheRoot + "/https/repo1.maven.org/maven2/" + relativePath
  }

  protected sealed trait ResolveScalaLibraryFrom
  protected object ResolveScalaLibraryFrom {
    object Ivy extends ResolveScalaLibraryFrom
    object Coursier extends ResolveScalaLibraryFrom
  }

  protected def expectedScalaLibrary(scalaVersion: ScalaVersion, resolveFrom: ResolveScalaLibraryFrom = ResolveScalaLibraryFrom.Coursier): library = {
    resolveFrom match {
      case ResolveScalaLibraryFrom.Ivy      => expectedScalaLibraryIvy(scalaVersion)
      case ResolveScalaLibraryFrom.Coursier => expectedScalaLibraryCoursier(scalaVersion)
    }
  }

  // NOTE: currently it downloads from the internet if needed,
  // while in *Coursier implementation all paths are constructed manually, without invoking resolve
  protected def expectedScalaLibraryIvy(scalaVersion: ScalaVersion): library = {
    val scalaLibraryDependency = scalaLibraryDescription(scalaVersion)
    val scalaCompilerDependency = scalaCompilerDescription(scalaVersion).transitive()

    val manager: DependencyManagerBase = new DependencyManagerBase {
      override protected val artifactBlackList: Set[String] = Set.empty
    }

    val libraryJar: String = manager.resolveSingle(scalaLibraryDependency).file.getAbsolutePath
    val compilerClassPath: Seq[String] = manager.resolve(scalaCompilerDependency).map(_.file.getAbsolutePath)

    // NOTE: in Scala2 these compiler jars are not within transitive dependencies of scala-compiler (as in Scala 3)
    // TODO: support all scala versions, current implementation tested with 2.12.10
    val extraCompilerClassPath: Seq[String] = manager.resolve(
      "jline" % "jline" % "2.14.6",
      "org.fusesource.jansi" % "jansi" % "1.12",
    ).map(_.file.getAbsolutePath)

    new library(s"sbt: $scalaLibraryDependency:jar") {
      classes := Seq(libraryJar)

      scalaSdkSettings := Some(ScalaSdkAttributes(
        scalaVersion.languageLevel,
        classpath = compilerClassPath ++ extraCompilerClassPath
      ))
    }
  }

  protected def expectedScalaLibraryCoursier(scalaVersion: ScalaVersion): library = {
    val scalaVersionStr = scalaVersion.minor
    val dependency = scalaLibraryDescription(scalaVersion)

    new library(s"sbt: $dependency:jar") {
      classes := Seq(
        s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr.jar"
      ).map(coursierCacheArtifact)

      scalaSdkSettings := Some(ScalaSdkAttributes(
        scalaVersion.languageLevel,
        classpath = Seq(
          // TODO: build expected classpath depending on scalaVersion
          "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
          "org/jline/jline/3.19.0/jline-3.19.0.jar",
          s"org/scala-lang/scala-compiler/$scalaVersionStr/scala-compiler-$scalaVersionStr.jar",
          s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr.jar",
          s"org/scala-lang/scala-reflect/$scalaVersionStr/scala-reflect-$scalaVersionStr.jar",
        ).map(coursierCacheArtifact)
      ))
    }
  }
}
