import sbt._
import Keys._
import scala.language.implicitConversions
import scala.language.postfixOps

object common extends Build {
  lazy val packagePlugin = taskKey[Unit]("package scala plugin locally")
  lazy val packagePluginZip = taskKey[Unit]("package and compress scala plugin locally")
  lazy val packageStructure = taskKey[Seq[(File, String)]]("plugin artifact structure")

  // merge multiple jars in one and return it
  def merge(files: File*): File = {
    IO.withTemporaryDirectory({ tmp =>
      files.foreach(IO.unzip(_, tmp))
      val zipFile = IO.temporaryDirectory / "sbt-merge-result.jar"
      zipFile.delete()
      IO.zip((tmp ***) pair (relativeTo(tmp), false), zipFile)
      zipFile
    })
  }

  def newProject(projectName: String, basePath: String)(customSettings: Setting[_]*): Project = {
    Project(projectName, file(basePath)).settings(
      name := projectName,
      organization := "JetBrains",
      scalaVersion := Versions.scalaVersion
    ).settings(customSettings:_*)
  }

  def newProject(projectName: String)(customSettings: Setting[_]*): Project =
    newProject(projectName, projectName)(customSettings:_*)
}