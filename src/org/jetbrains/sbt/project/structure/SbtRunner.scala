package org.jetbrains.sbt
package project.structure

import java.io.{FileNotFoundException, _}
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil._
import org.jetbrains.sbt.project.structure.SbtRunner._
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, Output, TaskComplete, TaskStart}

import scala.collection.JavaConverters._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}
import scala.xml.{Elem, XML}

/**
 * @author Pavel Fatin
 */
class SbtRunner(vmExecutable: File, vmOptions: Seq[String], environment: Map[String, String],
                customLauncher: Option[File], customStructureJar: Option[File],
                id: ExternalSystemTaskId,
                listener: ExternalSystemTaskNotificationListener) {
  private val LauncherDir = getSbtLauncherDir
  private val SbtLauncher = customLauncher.getOrElse(getDefaultLauncher)
  private def sbtStructureJar(sbtVersion: String) = customStructureJar.getOrElse(LauncherDir / s"sbt-structure-$sbtVersion.jar")

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit =
    cancellationFlag.set(true)

  def read(directory: File, options: Seq[String], importFromShell: Boolean): Try[(Elem, String)] = {

    if (SbtLauncher.exists()) {

      val sbtVersion = Version(detectSbtVersion(directory, SbtLauncher))
      val majorSbtVersion = binaryVersion(sbtVersion)
      lazy val project = id.findProject()
      // if the project is being freshly imported, there is no project instance to get the shell component
      val useShellImport = importFromShell && shellImportSupported(sbtVersion) && project != null

      if (importSupported(sbtVersion)) usingTempFile("sbt-structure", Some(".xml")) { structureFile =>

        val structureFilePath = path(structureFile)

        val messageResult: Try[String] = {
          if (useShellImport) {
            val shell = SbtShellCommunication.forProject(project)
            dumpFromShell(shell, structureFilePath, options)
          }
          else dumpFromProcess(directory, structureFilePath, options: Seq[String], majorSbtVersion)
        }

        messageResult.flatMap { messages =>
          if (structureFile.length > 0) Try {
            val elem = XML.load(structureFile.toURI.toURL)
            (elem, messages)
          }
          else Failure(SbtException.fromSbtLog(messages))
        }

      } else {
        val message = s"SBT $sinceSbtVersion+ required. Please update project build.properties."
        Failure(new UnsupportedOperationException(message))
      }
    }
    else {
      val error = s"SBT launcher not found at ${SbtLauncher.getCanonicalPath}"
      Failure(new FileNotFoundException(error))
    }
  }

  private val statusUpdate = (message:String) =>
    listener.onStatusChange(new ExternalSystemTaskNotificationEvent(id, message.trim))

  private def dumpFromShell(shell: SbtShellCommunication, structureFilePath: String, options: Seq[String]): Try[String] = {

    val optString = options.mkString(" ")
    val setCmd = s"""set org.jetbrains.sbt.StructureKeys.sbtStructureOptions in Global := "$optString""""
    val cmd = s";reload; $setCmd ;*/*:dumpStructureTo $structureFilePath"
    val output =
      shell.command(cmd, new StringBuilder, messageAggregator(id, statusUpdate), showShell = true)

    Await.ready(output, Duration.Inf)
    output.value.get.map(_.toString())
  }

  /** Aggregates (messages, warnings) and updates external system listener. */
  private def messageAggregator(id: ExternalSystemTaskId, statusUpdate: String=>Unit): EventAggregator[StringBuilder] = {
    case (m,TaskStart) => m
    case (m,TaskComplete) => m
    case (messages, Output(message)) =>
      val text = message.trim
      if (text.nonEmpty) statusUpdate(text)
      messages.append("\n").append(text)
      messages
  }

  private def shellImportSupported(sbtVersion: Version): Boolean =
    sbtVersion >= sinceSbtVersionShell

  private def importSupported(sbtVersion: Version): Boolean =
    sbtVersion >= sinceSbtVersion

  private def dumpFromProcess(directory: File, structureFilePath: String, options: Seq[String], sbtVersion: Version): Try[String] = {

    val optString = options.mkString(", ")
    val pluginJar = sbtStructureJar(sbtVersion.major(2).presentation)

    val setCommands = Seq(
      s"""shellPrompt := { _ => "" }""",
      s"""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile") in _root_.sbt.Global := _root_.scala.Some(_root_.sbt.file("$structureFilePath"))""",
      s"""SettingKey[_root_.java.lang.String]("sbtStructureOptions") in _root_.sbt.Global := "$optString""""
    ).mkString("set _root_.scala.collection.Seq(", ",", ")")

    val sbtCommands = Seq(
      setCommands,
      s"""apply -cp "${path(pluginJar)}" org.jetbrains.sbt.CreateTasks""",
      s"*/*:dumpStructure"
    ).mkString(";",";","")

    val processCommandsRaw =
      path(vmExecutable) +:
        "-Djline.terminal=jline.UnsupportedTerminal" +:
        "-Dsbt.log.noformat=true" +:
        "-Dfile.encoding=UTF-8" +:
        (vmOptions ++ SbtOpts.loadFrom(directory)) :+
        "-jar" :+
        path(SbtLauncher)

    val processCommands = processCommandsRaw.filterNot(_.isEmpty)

    Try {
      val processBuilder = new ProcessBuilder(processCommands.asJava)
      processBuilder.directory(directory)
      processBuilder.environment().putAll(environment.asJava)
      val process = processBuilder.start()
      val result = using(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, "UTF-8")))) { writer =>
        writer.println(sbtCommands)
        // exit needs to be in a separate command, otherwise it will never execute when a previous command in the chain errors
        writer.println("exit")
        writer.flush()
        handle(process, statusUpdate)
      }
      result.getOrElse("no output from sbt shell process available")
    }.orElse(Failure(SbtRunner.ImportCancelledException))
  }

  private def handle(process: Process, statusUpdate: String=>Unit): Try[String] = {
    val output = StringBuilder.newBuilder

    def update(textRaw: String) = {
      val text = textRaw.trim
      output.append("\n").append(text)
      if (text.nonEmpty) statusUpdate(text)
    }

    val processListener: (OutputType, String) => Unit = {
      case (OutputType.StdOut, text) =>
        if (text.contains("(q)uit")) {
          val writer = new PrintWriter(process.getOutputStream)
          writer.println("q")
          writer.close()
        } else {
          update(text)
        }
      case (OutputType.StdErr, text) =>
        update(text)
    }

    Try {
      val handler = new OSProcessHandler(process, "SBT import", Charset.forName("UTF-8"))
      handler.addProcessListener(new ListenerAdapter(processListener))
      handler.startNotify()

      var processEnded = false
      while (!processEnded && !cancellationFlag.get())
        processEnded = handler.waitFor(SBT_PROCESS_CHECK_TIMEOUT_MSEC)

      if (!processEnded) {
        // task was cancelled
        handler.setShouldDestroyProcessRecursively(false)
        handler.destroyProcess()
        throw ImportCancelledException
      } else output.toString()
    }
  }

  private def path(file: File): String = file.getAbsolutePath.replace('\\', '/')
}

object SbtRunner {
  case object ImportCancelledException extends Exception

  val isInTest: Boolean = ApplicationManager.getApplication.isUnitTestMode

  val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  def getSbtLauncherDir: File = {
    val file: File = jarWith[this.type]
    val deep = if (file.getName == "classes") 1 else 2
    val playEnabled = Try(getClass.getClassLoader.loadClass("com.intellij.scala.play.Play2Bundle") != null).getOrElse(false)
    (file << deep) / "launcher" match {
      case res: File if !res.exists() && isInTest =>
        (for {
          scalaVer <- jarWith[this.type].parent
          target <- scalaVer.parent
          project <- if (playEnabled) Option(target << 3) else target.parent
        } yield project / "out" / "plugin" / "Scala" / "launcher").get
      case res => res
    }
  }

  def getDefaultLauncher: File = getSbtLauncherDir / "sbt-launch.jar"

  private val sinceSbtVersion = Version("0.12.4")
  val sinceSbtVersionShell = Version("0.13.5")

}
