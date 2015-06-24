import sbt._
import Keys._
import scala.language.implicitConversions
import scala.language.postfixOps

object Common {
  def newProject(projectName: String, basePath: String)(customSettings: Setting[_]*): Project = {
    Project(projectName, file(basePath)).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion,
      unmanagedSourceDirectories in Compile += baseDirectory.value / "src",
      unmanagedSourceDirectories in Test += baseDirectory.value / "test",
      unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"
    ).settings(customSettings:_*)
  }

  def newProject(projectName: String)(customSettings: Setting[_]*): Project =
    newProject(projectName, projectName)(customSettings:_*)

  def unmanagedJarsFrom(directories: String*): Def.Initialize[Task[Classpath]] = Def.task {
    val sdkPathFinder = directories.foldLeft(PathFinder.empty) { (finder, dir) =>
      finder +++ (baseDirectory.in(ThisBuild).value / "SDK" / dir)
    }
    (sdkPathFinder * globFilter("*.jar")).classpath
  }
}