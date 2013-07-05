import sbt._
import Keys._

object RootBuild extends Build {
  lazy val rootProject = Project(id = "root", base = file(".")) aggregate(ideaPluginProject, sbtPluginProject)

  lazy val ideaPluginProject = Project(id = "idea-plugin", base = file("idea-plugin"))

  lazy val sbtPluginProject = Project(id = "sbt-plugin", base = file("sbt-plugin"))

  val ideaDirectory = SettingKey[File]("idea-directory", "The base directory of IDEA.") ??
    sys.env.get("IDEA_HOME").map(file).getOrElse {
      sys.error("""Please either set "idea-directory" or define "IDEA_HOME"""")
    }

  val scalaPluginDirectory = SettingKey[File]("scala-plugin-directory", "The base directory of Scala plugin.") ??
    sys.env.get("SCALA_PLUGIN_HOME").map(file).getOrElse {
      sys.error("""Please either set "scala-plugin-directory" or define "SCALA_PLUGIN_HOME"""")
    }
}