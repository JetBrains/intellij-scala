import sbt._
import Keys._
import scala.language.implicitConversions
import scala.language.postfixOps

import CustomKeys._

object Common {
  def newProject(projectName: String, basePath: String): Project =
    Project(projectName, file(basePath)).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
      unmanagedSourceDirectories in Test += baseDirectory.value / "test",
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"
    )

  def newProject(projectName: String): Project =
    newProject(projectName, projectName)

  def unmanagedJarsFromSdk(directories: String*): Def.Initialize[Task[Classpath]] = Def.task {
    val sdkPathFinder = directories.foldLeft(PathFinder.empty) { (finder, dir) =>
      finder +++ (sdkDirectory.in(ThisBuild).value / dir)
    }
    (sdkPathFinder * globFilter("*.jar")).classpath
  }
}
