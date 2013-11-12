import sbt._
import Keys._
import xml.PrettyPrinter

object RootBuild extends Build {
  lazy val mainProject = 
    Project(id = "root", base = file("."), settings = Defaults.defaultSettings :+ distTask :+ updatePluginVersionTask :+ generateUpdateDescriptorTask)
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
    version,
    baseDirectory.in(ideaPluginProject),
    baseDirectory.in(sbtPluginProject),
    packageBin.in(ideaPluginProject).in(Compile),
    packageBin.in(sbtPluginProject).in(Compile)) map { (s, target, pluginName, pluginVersion, ideaPluginBase, sbtPluginBase, ideaPluginJar, sbtPluginJar) =>

    val files = Seq((ideaPluginJar, pluginName + ".jar"),
      (sbtPluginBase / "sbt-launch.jar", "launcher/sbt-launch.jar"),
      (sbtPluginJar, "launcher/sbt-structure.jar"))

    val archive = target / (pluginName + "-bin-" + pluginVersion + ".zip")

    s.log.info("Creating a distribution archive " + archive.getPath + " ...")

    IO.delete(archive)
    IO.zip(files.map(p => (p._1, pluginName + "/lib/" + p._2)), archive)

    s.log.info("Done creating the distribution archive.")

    archive
  }

  val updatePluginVersion = TaskKey[String]("update-plugin-version", "Updates the plugin version in plugin.xml.")

  val updatePluginVersionTask = updatePluginVersion <<= (streams,
    baseDirectory.in(ideaPluginProject),
    version) map { (s, base, version) =>

    val descriptor = base / "src" / "main" / "resources" / "META-INF" / "plugin.xml"

    s.log.info("Updating plugin version in " + descriptor.getPath + " to " + version + "...")

    IO.writeLines(descriptor, IO.readLines(descriptor).map(_.replaceAll("(?<=<version>)\\S+(?=</version>)", version)))

    s.log.info("Done updating the plugin version.")

    version
  }

  val generateUpdateDescriptor = TaskKey[File]("generate-update-descriptor", "Generates a descriptor for auto-updates.")

  val generateUpdateDescriptorTask = generateUpdateDescriptor <<= (streams,
    target, 
    name.in(ideaPluginProject),
    version) map { (s, target, pluginName, version) =>

    val xml =
      <plugins>
        <plugin id="org.intellij.sbt" version={version} url={"http://download.jetbrains.com/scala/" + pluginName + "-bin-" + version + ".zip"} />
      </plugins>

    val descriptor = target / "sbt-nightly-leda.xml"

    s.log.info("Generating an update descriptor " + descriptor.getPath + " with version " + version + "...")

    val printer = new PrettyPrinter(180, 2)
    IO.write(descriptor, printer.format(xml))

    s.log.info("Done generating the update descriptor.")

    descriptor
  }
}