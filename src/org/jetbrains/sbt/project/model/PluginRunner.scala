package org.jetbrains.sbt
package project.model

import java.io.File
import scala.xml.{Elem, XML}
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.OutputListener

/**
 * @author Pavel Fatin
 */
object PluginRunner {
  private val JavaVM = (System.getProperty("java.home").toFile / "bin" / "java").canonicalPath
  private val LauncherDir = ((jarWith[this.type].toFile <<) <<) / "launcher"
  private val SbtLauncher = (LauncherDir / "sbt-launch.jar").canonicalPath
  private val SbtPlugin = (LauncherDir / "sbt-structure.jar").canonicalPath
  private val JavaOpts = Option(System.getenv("JAVA_OPTS")).getOrElse("")

  def read(directory: File): (Output, Option[Elem]) = {
    val tempFile = File.createTempFile("sbt-structure", "xml")
    tempFile.deleteOnExit()

    val command = JavaVM + " " + JavaOpts + """ -jar """ + SbtLauncher +
      """ "; set artifactPath := new File(\"""" + canonicalPath(tempFile) +
      """\") ; apply -cp """ + SbtPlugin + """ org.jetbrains.sbt.Plugin""""

    val process = Runtime.getRuntime.exec(command, null, directory)

    val listener = new OutputListener()
    val handler = new OSProcessHandler(process, null, null)
    handler.addProcessListener(listener)
    handler.startNotify()

    process.waitFor()

    val output = {
      val o = listener.getOutput
      Output(o.getExitCode, o.getStdout, o.getStderr)
    }

    val xml = try {
      Some(XML.load(tempFile.toURI.toURL))
    } catch {
      case _: Exception => None
    } finally {
      tempFile.delete()
    }

    (output, xml)
  }

  private def canonicalPath(file: File): String = file.getAbsolutePath.replace('\\', '/')
}

case class Output(code: Int, stdout: String, stderr: String)
