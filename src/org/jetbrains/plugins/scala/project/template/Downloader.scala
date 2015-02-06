package org.jetbrains.plugins.scala.project.template

import java.io.{File, FileNotFoundException}

import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.util.Key

/**
 * @author Pavel Fatin
 */
object Downloader {
  def downloadScala(version: String, listener: String => Unit) {
    val buffer = new StringBuffer()

    usingTempFile("sbt-commands") { file =>
      writeLinesTo(file, sbtCommandsFor(version): _*)
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

  private def sbtCommandsFor(version: String) = {
    Seq( s"""set scalaVersion := "$version"""", "updateClassifiers")
  }
}

class DownloadException(message: String) extends Exception(message)