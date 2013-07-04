import sbt._
import Keys._

object RootBuild extends Build {
  lazy val rootProject = Project(id = "root", base = file(".")) aggregate(ideaPluginProject, sbtPluginProject)

  lazy val ideaPluginProject = Project(id = "idea-plugin", base = file("idea-plugin"))

  lazy val sbtPluginProject = Project(id = "sbt-plugin", base = file("sbt-plugin"))
}