package org.jetbrains.sbt.project

import org.jetbrains.plugins.scala.DependencyManagerBase.scalaLibraryDescription
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.project.sdkdetect.repository.CoursierPaths
import org.jetbrains.sbt.project.ProjectStructureDsl.{ScalaSdkAttributes, classes, library, scalaSdkSettings}

trait ProjectStructureExpectedLibrariesOps {

  private def coursierCacheArtifact(relativePath: String): String = {
    val cacheRoot = CoursierPaths.cacheDirectory.getAbsolutePath.stripSuffix("/").stripSuffix("\\")
    cacheRoot + "/https/repo1.maven.org/maven2/" + relativePath
  }

  // todo: now it only supports Coursier but we might also also support Ivy to test with old sbt versions
  protected def expectedScalaLibrary(scalaVersion: ScalaVersion): library = {
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
