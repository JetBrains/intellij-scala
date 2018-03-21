package org.jetbrains.plugins.scala.project.template

import java.io.{File, FileNotFoundException}

import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.util.net.HttpConfigurable

import scala.collection.JavaConverters

/**
  * @author Pavel Fatin
  */
object Downloader {

  private val DefaultCommands = Array("java",
    "-Djline.terminal=jline.UnsupportedTerminal",
    "-Dsbt.log.noformat=true"
  )

  class DownloadProcessAdapter(private val progressManager: ProgressManager) extends ProcessAdapter {

    private val builder = new StringBuilder()

    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
      val text = event.getText
      progressManager.getProgressIndicator.setText(text)
      builder ++= text
    }

    def text(exitValue: Int = 0): String = {
      val result = builder.toString
      exitValue match {
        case 0 => result
        case _ => throw new RuntimeException(result)
      }
    }
  }

  def setScalaSBTCommand(scalaVersion: String): String =
    s"""set scalaVersion := "$scalaVersion""""

  def setDependenciesSBTCommand(dependencies: String*): String =
    s"""set libraryDependencies := Seq(${dependencies.mkString(", ")})"""

  val UpdateClassifiersSBTCommand: String = "updateClassifiers"

  def createTempSbtProject(version: String,
                           processAdapter: DownloadProcessAdapter,
                           sbtCommands: String*): Unit =
    usingTempFile("sbt-commands") { file =>
      writeLinesTo(file, sbtCommands: _*)

      usingTempDirectory("sbt-project") { directory =>
        val process = executeOn(file, directory)

        val handler = new OSProcessHandler(process, null, null)
        handler.addProcessListener(processAdapter)
        handler.startNotify()
        handler.waitFor()

        processAdapter.text(process.exitValue)
      }
    }

  private def executeOn(file: File, directory: File) = {
    val launcherOptions = this.launcherOptions(file)
    Runtime.getRuntime.exec(DefaultCommands ++ vmOptions ++ launcherOptions, null, directory)
  }

  private def launcherOptions(file: File) =
    jarWith[this.type].getParentFile.getParentFile / "launcher" / "sbt-launch.jar" match {
      case launcher if launcher.exists => Seq("-jar", launcher.getAbsolutePath, "< " + file.getAbsolutePath)
      case launcher => throw new FileNotFoundException(launcher.getPath)
    }

  private def vmOptions = {
    import JavaConverters._
    HttpConfigurable.getInstance.getJvmProperties(false, null)
      .asScala.map { pair =>
      s"-D${pair.getFirst}=${pair.getSecond}"
    }
  }
}
