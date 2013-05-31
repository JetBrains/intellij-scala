package org.jetbrains.sbt
package project.model

import java.io.{FileNotFoundException, PrintWriter, File}
import scala.xml.{Elem, XML}
import com.intellij.execution.process.OSProcessHandler
import project.SbtException

/**
 * @author Pavel Fatin
 */
object PluginRunner {
  private val JavaHome = new File(System.getProperty("java.home"))
  private val JavaVM = JavaHome / "bin" / "java"
  private val LauncherDir = (jarWith[this.type] << 2) / "launcher"
  private val SbtLauncher = LauncherDir / "sbt-launch.jar"
  private val SbtPlugin = LauncherDir / "sbt-structure.jar"
  private val JavaOpts = Option(System.getenv("JAVA_OPTS")).map(_.split("\\s+")).toSeq.flatten
  private val OS = System.getProperty("os.name")

  def read(directory: File)(listener: (String) => Unit): Either[Exception, Elem] = {
    val problem = Stream(JavaHome, SbtLauncher, SbtPlugin).map(check).flatten.headOption
    problem.map(it => Left(new FileNotFoundException(it))).getOrElse(read0(directory, listener))
  }

  private def check(file: File) = (!file.exists()).option(s"File does not exist: $file")

  private def read0(directory: File, listener: (String) => Unit) = {
    val tempFile = File.createTempFile("sbt-structure", ".xml")
    tempFile.deleteOnExit()

    val commands = {
      val vmOptions = "-Djline.terminal=jline.UnsupportedTerminal" +: "-Dsbt.log.noformat=true" +: JavaOpts
      val tempPath =  canonicalPath(tempFile)
      val quote = if (OS.startsWith("Windows")) "\\\"" else "\""

      JavaVM.getPath +: vmOptions :+ "-jar" :+ SbtLauncher.getPath :+
        s"; set artifactPath := new File($quote$tempPath$quote) ; apply -cp $SbtPlugin org.jetbrains.sbt.Plugin"
    }

    try {
      val process = Runtime.getRuntime.exec(commands.toArray, null, directory)
      val errors = handle(process, listener).map(new SbtException(_))
      errors.toLeft(XML.load(tempFile.toURI.toURL))
    } catch {
      case e: Exception => Left(e)
    } finally {
      tempFile.delete()
    }
  }

  private def handle(process: Process, listener: (String) => Unit): Option[String] = {
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
        if (text.startsWith("[error]")) {
          errors.append(text)
        }
      case (OutputType.StdErr, text) =>
        listener(text)
        errors.append(text)
    }

    val handler = new OSProcessHandler(process, null, null)
    handler.addProcessListener(new ListenerAdapter(processListener))
    handler.startNotify()

    handler.waitFor()

    if (errors.isEmpty) None else Some(errors.toString())
  }

  private def canonicalPath(file: File): String = file.getAbsolutePath.replace('\\', '/')
}