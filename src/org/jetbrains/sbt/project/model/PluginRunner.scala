package org.jetbrains.sbt
package project.model

import java.io.{PrintWriter, File}
import scala.xml.{Elem, XML}
import com.intellij.execution.process.OSProcessHandler
import scala.util.control.Exception._

/**
 * @author Pavel Fatin
 */
object PluginRunner {
  private val JavaVM = (System.getProperty("java.home").toFile / "bin" / "java").canonicalPath
  private val LauncherDir = ((jarWith[this.type].toFile <<) <<) / "launcher"
  private val SbtLauncher = (LauncherDir / "sbt-launch.jar").canonicalPath
  private val SbtPlugin = (LauncherDir / "sbt-structure.jar").canonicalPath
  private val JavaOpts = Option(System.getenv("JAVA_OPTS")).getOrElse("")

  def read(directory: File)(listener: (String) => Unit): Either[String, Elem] = {
    val tempFile = File.createTempFile("sbt-structure", "xml")
    tempFile.deleteOnExit()

    val command = JavaVM + " -Djline.terminal=jline.UnsupportedTerminal -Dsbt.log.noformat=true " +
      JavaOpts + """ -jar """ + SbtLauncher +
      """ "; set artifactPath := new File(\"""" + canonicalPath(tempFile) +
      """\") ; apply -cp """ + SbtPlugin + """ org.jetbrains.sbt.Plugin""""

    catching(classOf[Exception])
      .either(Runtime.getRuntime.exec(command, null, directory))
      .left.map(_.getMessage)
      .right.flatMap { process =>

      val errors = handle(process, listener)

      catching(classOf[Exception])
        .andFinally(tempFile.delete())
        .opt(XML.load(tempFile.toURI.toURL))
        .toRight(errors)
    }
  }

  private def handle(process: Process, listener: (String) => Unit): String = {
    val errors = new StringBuilder()

    val processListener: (OutputType, String) => Unit = {
      case (OutputType.StdOut, text) =>
        if (text.contains("(q)uit")) {
          val writer = new PrintWriter(process.getOutputStream)
          writer.println("q")
          writer.close()
        } else {
          listener(text)
        }
      case (OutputType.StdErr, text) =>
        listener(text)
        errors.append(text)
    }

    val handler = new OSProcessHandler(process, null, null)
    handler.addProcessListener(new ListenerAdapter(processListener))
    handler.startNotify()

    handler.waitFor()

    errors.toString()
  }

  private def canonicalPath(file: File): String = file.getAbsolutePath.replace('\\', '/')
}