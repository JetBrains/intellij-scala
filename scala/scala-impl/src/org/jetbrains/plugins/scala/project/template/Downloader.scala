package org.jetbrains.plugins.scala.project.template

import java.io.{File, FileNotFoundException}

import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.hydra.compiler.HydraRepositorySettings
import org.jetbrains.plugins.scala.project.Platform

/**
  * @author Pavel Fatin
  */
object Downloader {
  def downloadScala(platform: Platform, version: String, listener: String => Unit): Unit = {
    createTempSbtProject(platform, version, listener, sbtCommandsFor)
  }

  def downloadHydra(repositorySettings: HydraRepositorySettings, version: String, listener: String => Unit): Unit = {
    createTempSbtProject(Platform.Scala, version, listener, sbtCommandsForHydra(repositorySettings))
  }

  private def createTempSbtProject(platform: Platform, version: String, listener: String => Unit, sbtCommands: (Platform, String) => Seq[String]): Unit = {
    val buffer = new StringBuffer()

    usingTempFile("sbt-commands") { file =>
      writeLinesTo(file, sbtCommands(platform, version): _*)
      usingTempDirectory("sbt-project") { directory =>
        val process = Runtime.getRuntime.exec(osCommandsFor(file).toArray, null, directory)

        val listenerAdapter = new ProcessAdapter {
          override def onTextAvailable(event: ProcessEvent, outputType: Key[_]) {
            val text = event.getText
            listener(text)
            buffer.append(text)
          }
        }

        val handler = new OSProcessHandler(process, null, null)
        handler.addProcessListener(listenerAdapter)
        handler.startNotify()
        handler.waitFor()
        if (process.exitValue != 0) {
          throw new DownloadException(buffer.toString)
        }
      }
    }
  }

  private def osCommandsFor(file: File) = {
    val launcher = jarWith[this.type].getParentFile.getParentFile / "launcher" / "sbt-launch.jar"

    if (launcher.exists()) {
      Seq("java",
        "-Djline.terminal=jline.UnsupportedTerminal",
        "-Dsbt.log.noformat=true",
        "-jar",
        launcher.getAbsolutePath,
        "< " + file.getAbsolutePath)
    } else {
      throw new FileNotFoundException(launcher.getPath)
    }
  }

  private def sbtCommandsFor(platform: Platform, version: String) = platform match {
    case Platform.Scala => Seq(
      s"""set scalaVersion := "$version"""",
      "updateClassifiers")

    case Platform.Dotty => Seq(
      s"""set libraryDependencies := Seq("ch.epfl.lamp" % "dotty_2.11" % "$version" % "scala-tool")""",
      "updateClassifiers")
  }

  private def sbtCommandsForHydra(repositorySettings: HydraRepositorySettings)(platform: Platform, version: String) = {
    Seq(
      s"""set scalaVersion := "${version.split("_")(0)}"""",
      s"""set credentials := Seq(Credentials("${repositorySettings.repositoryRealm}", "${repositorySettings.repositoryName}", "${repositorySettings.login}", "${repositorySettings.password}"))""",
      s"""set resolvers := Seq(Resolver.url("Triplequote Plugins Ivy Releases", url("${repositorySettings.repositoryURL}/ivy-releases/"))(Resolver.ivyStylePatterns), Resolver.url("Triplequote sbt-plugin-relseases", url("${repositorySettings.repositoryURL}/sbt-plugins-release/"))(Resolver.ivyStylePatterns),  "Triplequote Plugins Releases" at "${repositorySettings.repositoryURL}/libs-release-local/")""",
      s"""set libraryDependencies := Seq("com.triplequote" % "hydra_${version.split("_")(0)}" % "${version.split("_")(1)}", ("com.triplequote" % "hydra-bridge_1_0" % "${version.split("_")(1)}").sources())""",
      "updateClassifiers",
      "show dependencyClasspath")
  }
}

class DownloadException(message: String) extends Exception(message)