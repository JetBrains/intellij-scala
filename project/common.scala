import sbt._
import Keys._

object common extends Build {
  lazy val ideaBaseJars = SettingKey[Classpath]("")
  lazy val ideaICPluginJars = SettingKey[Classpath]("")
  lazy val ideaIUPluginJars = SettingKey[Classpath]("")
  lazy val allIdeaJars = SettingKey[Classpath]("")

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

  def readIdeaPropery(key: String): String = {
    import java.util.Properties
    val prop = new Properties()
    IO.load(prop, file("idea.properties"))
    prop.getProperty(key)
  }
}