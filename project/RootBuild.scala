import sbt._
import Keys._

object RootBuild extends Build {
  lazy val rootProject = Project(id = "root", base = file("."), settings = Defaults.defaultSettings :+ distTask)
    .aggregate(ideaPluginProject, sbtPluginProject)

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

  val dist = TaskKey[File]("dist", "Creates a zip file.")

  val distTask = dist <<= (streams,
    target,
    name.in(ideaPluginProject),
    baseDirectory.in(ideaPluginProject),
    baseDirectory.in(sbtPluginProject),
    packageBin.in(ideaPluginProject).in(Compile),
    packageBin.in(sbtPluginProject).in(Compile)) map { (s, target, pluginName, ideaPluginBase, sbtPluginBase, ideaPluginJar, sbtPluginJar) =>

    val files = Seq((ideaPluginJar, pluginName + ".jar"),
      (ideaPluginBase / "lib" / "external-system.jar", "external-system.jar"),
      (sbtPluginBase / "sbt-launch.jar", "launcher/sbt-launch.jar"),
      (sbtPluginJar, "launcher/sbt-structure.jar"))

    val archive = target / (pluginName + "-bin.zip")

    s.log.info("Creating a distribution archive " + archive.getPath + " ...")

    IO.delete(archive)
    IO.zip(files.map(p => (p._1, pluginName + "/lib/" + p._2)), archive)

    s.log.info("Done creating the distribution archive.")

    archive
  }
}