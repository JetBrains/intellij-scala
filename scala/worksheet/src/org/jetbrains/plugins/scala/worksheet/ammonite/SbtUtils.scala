package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.execution.process.{OSProcessHandler, ProcessAdapter, ProcessEvent}
import com.intellij.openapi.util.Key
import com.intellij.util.PathUtil
import org.jetbrains.plugins.scala.project.template._
import org.jetbrains.sbt.SbtUtil

import java.io.{File, FileNotFoundException}
import scala.collection.{immutable, mutable}

// moved from org.jetbrains.plugins.scala.project.template
private object SbtUtils {

  private val DefaultCommands = Array(
    "java",
    "-Djline.terminal=jline.UnsupportedTerminal",
    "-Dsbt.log.noformat=true"
  )

  def createTempSbtProject(
    version: String,
    preUpdateCommands: Seq[String] = Seq.empty,
    postUpdateCommands: Seq[String] = Seq.empty
  )(lineProcessor: String => Unit): Unit =
    usingTempFile("sbt-commands") { file =>
      writeLinesTo(file)(
        (s"""set scalaVersion := "$version"""" +: preUpdateCommands :+ "updateClassifiers") ++
          postUpdateCommands: _*
      )

      usingTempDirectory("sbt-project") { dir =>
        val process = Runtime.getRuntime.exec(
          DefaultCommands ++ vmOptions ++ launcherOptions(file.getAbsolutePath),
          null,
          dir
        )

        val listener: SBTProcessListener = lineProcessor(_)

        val handler = new OSProcessHandler(process, "sbt-based downloader", null)
        handler.addProcessListener(listener)
        handler.startNotify()
        handler.waitFor()

        val text = listener.text
        val rc = process.exitValue
        if (rc != 0) {
          throw new RuntimeException(s"sbt process exited with error code: $rc, process output:\n$text")
        }
      }
    }

  private abstract class SBTProcessListener extends ProcessAdapter {

    private val buffer = mutable.ArrayBuffer.empty[String]

    final def text: String = buffer.mkString("\n") // looks like text in `onTextAvailable` doesn't contain line breaks

    protected def onText(text: String): Unit

    override final def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
      val text = event.getText.trim
      onText(text)
      buffer += text
    }
  }

  private def launcherOptions(path: String): Seq[String] = {
    val scalaPluginJar = PathUtil.getJarPathForClass(getClass)
    val pluginLibFolder = new File(scalaPluginJar).getParentFile
    val pluginRootFolder = pluginLibFolder.getParentFile
    val launcher = pluginRootFolder / "launcher" / "sbt-launch.jar"
    if (launcher.exists())
      Seq("-jar", launcher.getAbsolutePath, "< " + path)
    else
      throw new FileNotFoundException("Jar file not found for class: " + launcher.getPath)
  }

  private def vmOptions: immutable.Iterable[String] = {
    val proxyOpts = SbtUtil.getStaticProxyConfigurationJvmOptions
    val javaOpts = Seq(sysprop("java.io.tmpdir")).flatten
    val options = proxyOpts ++ javaOpts
    options.map { case (name, value) => s"-D$name=$value" }
  }

  private def sysprop(key: String): Option[(String, String)] =
    Option(System.getProperty(key)).map((key, _))
}
