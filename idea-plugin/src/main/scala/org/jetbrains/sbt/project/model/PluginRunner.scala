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
  private val LauncherDir = (jarWith[this.type] <<) / "launcher"
  private val SbtLauncher = LauncherDir / "sbt-launch.jar"
  private val SbtPlugin = LauncherDir / "sbt-structure.jar"
  private val JavaOpts = Option(System.getenv("JAVA_OPTS")).map(_.split("\\s+")).toSeq.flatten

  def read(directory: File, download: Boolean)(listener: (String) => Unit): Either[Exception, Elem] = {
    val problem = Stream(JavaHome, SbtLauncher, SbtPlugin).map(check).flatten.headOption
    problem.map(it => Left(new FileNotFoundException(it))).getOrElse(read0(directory, download, listener))
  }

  private def check(file: File) = (!file.exists()).option(s"File does not exist: $file")

  /* It's rather tricky to launch the SBT plugin from a command line.

     There're various OS/JRE-dependent issues with quotes
     see (https://www.google.ru/search?q=processbuilder+quotes),
     so we have to use a temporary files to pass commands to SBT.

     Another problem is that the SBT's "apply" command is unable
     to correctly parse classpath entries with spaces,
     so we have to use a "safe" copy of the plugin JAR. */
  private def read0(directory: File, download: Boolean, listener: (String) => Unit) = {
    usingTempFile("sbt-structure", ".xml") { structureFile =>
      usingTempFile("sbt-commands", ".lst") { commandsFile =>
        usingSafeCopyOf(SbtPlugin) { pluginFile =>

          val className = if (download) "ReadProjectAndRepository" else "ReadProject"

          writeLinesTo(commandsFile,
            s"set artifactPath := file(\042${path(structureFile)}\042)",
            s"apply -cp ${path(pluginFile)} org.jetbrains.sbt.$className")

          val processCommands =
            path(JavaVM) +:
//              "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005" +:
              "-Djline.terminal=jline.UnsupportedTerminal" +:
              "-Dsbt.log.noformat=true" +:
              JavaOpts :+
              "-jar" :+
              path(SbtLauncher) :+
              s"< ${path(commandsFile)}"

          try {
            val process = Runtime.getRuntime.exec(processCommands.toArray, null, directory)
            val errors = handle(process, listener).map(new SbtException(_))
            errors.toLeft(XML.load(structureFile.toURI.toURL))
          } catch {
            case e: Exception => Left(e)
          }
        }
      }
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

  private def path(file: File): String = file.getAbsolutePath.replace('\\', '/')
}