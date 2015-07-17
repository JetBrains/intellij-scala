import sbt._
import Keys._
import scala.language.implicitConversions
import scala.language.postfixOps

object Common {
  def newProject(projectName: String, base: File): Project =
    Project(projectName, base).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
      unmanagedSourceDirectories in Test += baseDirectory.value / "test",
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
      libraryDependencies += Dependencies.junitInterface
    )

  def newProject(projectName: String): Project =
    newProject(projectName, file(projectName))

  def unmanagedJarsFrom(sdkDirectory: File, subdirectories: String*): Classpath = {
    val sdkPathFinder = subdirectories.foldLeft(PathFinder.empty) { (finder, dir) =>
      finder +++ (sdkDirectory / dir)
    }
    (sdkPathFinder * globFilter("*.jar")).classpath
  }

  def filterTestClasspath(classpath: Def.Classpath): Def.Classpath =
    classpath.filterNot(_.data.getName.endsWith("lucene-core-2.4.1.jar"))
}
