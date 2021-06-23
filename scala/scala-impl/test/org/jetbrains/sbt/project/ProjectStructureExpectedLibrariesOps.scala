package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.DependencyManagerBase.scalaLibraryDescription
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.sdkdetect.repository.CoursierPaths
import org.jetbrains.sbt.project.ProjectStructureDsl.{ScalaSdkAttributes, classes, library, scalaSdkSettings}

trait ProjectStructureExpectedLibrariesOps {

  private val systemHome = sys.props.get("user.home").get
  private val ivyCacheRootHome = withoutPathSuffix(systemHome) + "/.ivy2/cache"
  private val coursierCacheRoot = withoutPathSuffix(CoursierPaths.cacheDirectory.getAbsolutePath)

  private def withoutPathSuffix(path: String) =
    path.stripSuffix("/").stripSuffix("\\")

  private def coursierCacheArtifact(relativePath: String): String =
    coursierCacheRoot + "/https/repo1.maven.org/maven2/" + relativePath

  private def ivyCacheArtifact(relativePath: String): String =
    ivyCacheRootHome + "/" + relativePath

  private def coursierCacheArtifacts(relativePaths: String*): Seq[String] =
    relativePaths.map(coursierCacheArtifact)

  private def ivyCacheArtifacts(relativePaths: String*): Seq[String] =
    relativePaths.map(ivyCacheArtifact)

  protected sealed trait ResolveScalaLibraryFrom
  protected object ResolveScalaLibraryFrom {
    object Ivy extends ResolveScalaLibraryFrom
    object Coursier extends ResolveScalaLibraryFrom
  }

  protected def expectedScalaLibrary(scalaVersion: ScalaVersion): library =
    expectedScalaLibraryFromCoursier(scalaVersion)

  protected def expectedScalaLibraryFromIvy(scalaVersion: ScalaVersion): library = {
    val scalaVersionStr = scalaVersion.minor
    val dependency = scalaLibraryDescription(scalaVersion)

    new library(s"sbt: $dependency:jar") {
      classes := ivyCacheArtifacts(
        s"org.scala-lang/scala-library/jars/scala-library-$scalaVersionStr.jar"
      )
      scalaSdkSettings := Some(ScalaSdkAttributes(
        scalaVersion.languageLevel,
        classpath = ivyCacheArtifacts(
          // TODO: build expected classpath depending on scalaVersion, currently extra classpath tested only for 2.12.10
          "jline/jline/jars/jline-2.14.6.jar",
          "org.fusesource.jansi/jansi/jars/jansi-1.12.jar",
          "org.scala-lang.modules/scala-xml_2.12/bundles/scala-xml_2.12-1.0.6.jar",
          "org.scala-lang/scala-compiler/jars/scala-compiler-2.12.10.jar",
          "org.scala-lang/scala-library/jars/scala-library-2.12.10.jar",
          "org.scala-lang/scala-reflect/jars/scala-reflect-2.12.10.jar",
        )
      ))
    }
  }

  protected def expectedScalaLibraryFromCoursier(scalaVersion: ScalaVersion): library = {
    val scalaVersionStr = scalaVersion.minor
    val dependency = scalaLibraryDescription(scalaVersion)

    new library(s"sbt: $dependency:jar") {
      classes := coursierCacheArtifacts(
        s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr.jar"
      )

      scalaSdkSettings := Some(ScalaSdkAttributes(
        scalaVersion.languageLevel,
        classpath = coursierCacheArtifacts(
          // TODO: build expected classpath depending on scalaVersion, currently extra classpath tested only for 2.13.5, 2.13.6
          "net/java/dev/jna/jna/5.3.1/jna-5.3.1.jar",
          "org/jline/jline/3.19.0/jline-3.19.0.jar",
          s"org/scala-lang/scala-compiler/$scalaVersionStr/scala-compiler-$scalaVersionStr.jar",
          s"org/scala-lang/scala-library/$scalaVersionStr/scala-library-$scalaVersionStr.jar",
          s"org/scala-lang/scala-reflect/$scalaVersionStr/scala-reflect-$scalaVersionStr.jar",
        )
      ))
    }
  }
}
