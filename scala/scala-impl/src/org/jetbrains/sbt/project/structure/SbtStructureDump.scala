package org.jetbrains.sbt.project.structure

import java.io.{BufferedWriter, File, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Collections, UUID}

import com.intellij.build.SyncViewManager
import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.build.events.{MessageEvent, MessageEventResult}
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.event._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.sbt.project.SbtProjectResolver.{ImportCancelledException, path}
import org.jetbrains.sbt.shell.SbtShellCommunication._
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SbtShellRunner}
import org.jetbrains.sbt.using

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import SbtStructureDump._
import org.jetbrains.sbt.shell.event.{SbtBuildEvent, SbtShellBuildError, SbtShellBuildWarning}

class SbtStructureDump {

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit = cancellationFlag.set(true)

  def dumpFromShell(taskId: ExternalSystemTaskId,
                    dir: File,
                    structureFilePath: String,
                    options: Seq[String],
                    notifications: ExternalSystemTaskNotificationListener,
                   ): Future[ImportMessages] = {

    notifications.onStart(taskId, dir.getCanonicalPath)

    val project = taskId.findProject() // assume responsibility of caller not to call dumpFromShell with null project
    val viewManager = ServiceManager.getService(project, classOf[SyncViewManager])
    val shell = SbtShellCommunication.forProject(project)

    val optString = options.mkString(" ")
    val setCmd = s"""set _root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions in Global := "$optString""""
    val cmd = s";reload; $setCmd ;*/*:dumpStructureTo $structureFilePath; session clear-all"

    val taskDescriptor =
      new TaskOperationDescriptorImpl("dump project structure from sbt shell", System.currentTimeMillis(), "project-structure-dump")
    val aggregator = dumpMessageAggregator(taskId, s"dump:${UUID.randomUUID()}", taskDescriptor, shell, notifications, viewManager)

    shell.command(cmd, ImportMessages.empty, aggregator, showShell = false)
  }

  def dumpFromProcess(directory: File,
                      structureFilePath: String,
                      options: Seq[String],
                      vmExecutable: File,
                      vmOptions: Seq[String],
                      environment: Map[String, String],
                      sbtLauncher: File,
                      sbtStructureJar: File,
                      taskId: ExternalSystemTaskId,
                      notifications: ExternalSystemTaskNotificationListener
                     ): Try[ImportMessages] = {

    val startTime = System.currentTimeMillis()
    val optString = options.mkString(", ")
    // assuming here that this method might still be called without valid project
    val viewManager = Option(taskId.findProject()).map(ServiceManager.getService(_, classOf[SyncViewManager]))

    val setCommands = Seq(
      s"""shellPrompt := { _ => "" }""",
      s"""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile") in _root_.sbt.Global := _root_.scala.Some(_root_.sbt.file("$structureFilePath"))""",
      s"""SettingKey[_root_.java.lang.String]("sbtStructureOptions") in _root_.sbt.Global := "$optString""""
    ).mkString("set _root_.scala.collection.Seq(", ",", ")")

    val sbtCommands = Seq(
      setCommands,
      s"""apply -cp "${path(sbtStructureJar)}" org.jetbrains.sbt.CreateTasks""",
      s"*/*:dumpStructure"
    ).mkString(";", ";", "")

    val processCommandsRaw =
      path(vmExecutable) +:
        "-Djline.terminal=jline.UnsupportedTerminal" +:
        "-Dsbt.log.noformat=true" +:
        "-Dfile.encoding=UTF-8" +:
        (vmOptions ++ SbtOpts.loadFrom(directory)) :+
        "-jar" :+
        path(sbtLauncher)

    val processCommands = processCommandsRaw.filterNot(_.isEmpty)

    val taskDescriptor =
      new TaskOperationDescriptorImpl("dump project structure from sbt", System.currentTimeMillis(), "project-structure-dump")
    val dumpTaskId = s"dump:${UUID.randomUUID()}"
    val startEvent = new ExternalSystemStartEventImpl[TaskOperationDescriptor](dumpTaskId, null, taskDescriptor)
    val taskStartEvent = new ExternalSystemTaskExecutionEvent(taskId, startEvent)
    notifications.onStatusChange(taskStartEvent)

    val result = Try {
      val processBuilder = new ProcessBuilder(processCommands.asJava)
      processBuilder.directory(directory)
      processBuilder.environment().putAll(environment.asJava)
      val process = processBuilder.start()
      val result = using(new PrintWriter(new BufferedWriter(new OutputStreamWriter(process.getOutputStream, "UTF-8")))) { writer =>
        writer.println(sbtCommands)
        // exit needs to be in a separate command, otherwise it will never execute when a previous command in the chain errors
        writer.println("exit")
        writer.flush()
        handle(process, taskId, dumpTaskId, taskDescriptor, viewManager, notifications)
      }
      result.getOrElse(ImportMessages.empty.addError("no output from sbt shell process available"))
    }.orElse(Failure(ImportCancelledException))

    val endTime = System.currentTimeMillis()
    val operationResult = result match {
      case Success(messages) =>
        if (messages.errors.isEmpty)
          new SuccessResultImpl(startTime, endTime, true)
        else {
          val fails = messages.errors.map(msg => new FailureImpl(msg, msg, Collections.emptyList()))
          new FailureResultImpl(startTime, endTime, fails.asJava)
        }
      case Failure(x) =>
        val fail = new FailureImpl(x.getMessage, x.getClass.getName, Collections.emptyList())
        new FailureResultImpl(startTime, endTime, Collections.singletonList(fail))
    }
    val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
      dumpTaskId, null, taskDescriptor, operationResult
    )
    val taskFinishEvent = new ExternalSystemTaskExecutionEvent(taskId, finishEvent)
    notifications.onStatusChange(taskFinishEvent)

    result
  }

  private def handle(process: Process,
                     taskId: ExternalSystemTaskId,
                     dumpTaskId: String,
                     taskDescriptor: TaskOperationDescriptor,
                     viewManager: Option[SyncViewManager],
                     notifications: ExternalSystemTaskNotificationListener): Try[ImportMessages] = {


    var lines = 0
    var messages = ImportMessages.empty

    def update(textRaw: String): Unit = {
      val text = textRaw.trim
      messages = messages.appendMessage(text)

      if (text.nonEmpty) {
        val buildEv = buildEvent(text, dumpTaskId, ProcessImport)
        for {
          event <- buildEv
          vm <- viewManager
        } {
          import MessageEvent.Kind._
          vm.onEvent(event)
          event.getKind match {
            case WARNING => messages = messages.addWarning(text)
            case ERROR => messages = messages.addError(text)
            case INFO | SIMPLE | STATISTICS => //ignore
          }
        }

        lines += 1
        val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
          dumpTaskId, null, taskDescriptor, lines, -1, "lines")
        val statusEvent = new ExternalSystemTaskExecutionEvent(taskId, progressEvent)

        notifications.onStatusChange(statusEvent)
      }
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
      val handler = new OSProcessHandler(process, "sbt import", Charset.forName("UTF-8"))
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
      } else messages
    }
  }
}

object SbtStructureDump {

  private val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  private val WARN_PREFIX = "[warn]"
  private val ERROR_PREFIX = "[error]"

  private def buildEvent(line: String, dumpTaskId: String, importType: ImportType): Option[MessageEvent] = {
    val text = line.trim
    if (text.startsWith("[warn]")) {
      val strippedText = text.stripPrefix(WARN_PREFIX).trim
      importType match {
        case ShellImport => Option(SbtShellBuildWarning(dumpTaskId, strippedText))
        case ProcessImport => Option(SbtProcessBuildWarning(dumpTaskId, strippedText))
      }
    } else if (text.startsWith("[error]")) {
      val strippedText = text.stripPrefix(ERROR_PREFIX).trim
      importType match {
        case ShellImport => Option(SbtShellBuildError(dumpTaskId, strippedText))
        case ProcessImport => Option(SbtProcessBuildError(dumpTaskId, strippedText))
      }
    } else None
    // okay, that wasn't very nice.
  }

  private def dumpMessageAggregator(id: ExternalSystemTaskId,
                                    dumpTaskId: String,
                                    taskDescriptor: TaskOperationDescriptor,
                                    shell: SbtShellCommunication,
                                    notifications: ExternalSystemTaskNotificationListener,
                                    viewManager: SyncViewManager
                                   ): EventAggregator[ImportMessages] = {
    case (stats, TaskStart) =>
      val startEvent = new ExternalSystemStartEventImpl[TaskOperationDescriptor](dumpTaskId, null, taskDescriptor)
      val statusEvent = new ExternalSystemTaskExecutionEvent(id, startEvent)
      notifications.onStatusChange(statusEvent)
      stats

    case (stats, TaskComplete) =>
      val result =
        if (stats.errors.isEmpty)
          new SuccessResultImpl(taskDescriptor.getEventTime, System.currentTimeMillis(), true)
        else {
          val fails = stats.errors.map { msg =>
            new FailureImpl(msg, msg, Collections.emptyList())
          }
          new FailureResultImpl(taskDescriptor.getEventTime, System.currentTimeMillis(), fails.asJava)
        }

      val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](dumpTaskId, null, taskDescriptor, result)
      val statusEvent = new ExternalSystemTaskExecutionEvent(id, finishEvent)
      notifications.onStatusChange(statusEvent)
      stats

    case (stats, ErrorWaitForInput) =>
      val msg = "Error importing sbt project. Please check sbt shell output."
      val ex = new ExternalSystemException(msg)
      val failure = new FailureImpl(msg, "error during sbt import", Collections.emptyList())
      val timestamp = System.currentTimeMillis()
      val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
        dumpTaskId, null, taskDescriptor,
        new FailureResultImpl(taskDescriptor.getEventTime, timestamp, Collections.singletonList(failure))
      )
      val statusEvent = new ExternalSystemTaskExecutionEvent(id, finishEvent)
      val buildEvent = SbtShellBuildError(dumpTaskId, msg)

      notifications.onStatusChange(statusEvent)
      notifications.onFailure(id, ex) // TODO redundant?
      viewManager.onEvent(buildEvent)

      shell.send("i" + System.lineSeparator)

      stats.addError(msg)

    case (stats, Output(text)) =>
      // report warnings / errors
      val buildEv = buildEvent(text, dumpTaskId, ShellImport)

      buildEv.foreach { ev =>
        viewManager.onEvent(ev)
      }

      val msgsize = stats.log.length // TODO some more informative metric?
    val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
      dumpTaskId, null, taskDescriptor, msgsize, -1, "chars"
    )
      val event = new ExternalSystemTaskExecutionEvent(id, progressEvent)

      notifications.onStatusChange(event)
      notifications.onTaskOutput(id, text, true)

      stats.appendMessage(text)
  }


  sealed trait ImportType
  case object ShellImport extends ImportType
  case object ProcessImport extends ImportType

  case class ImportMessages(warnings: Seq[String], errors: Seq[String], log: Seq[String]) {

    def appendMessage(text: String): ImportMessages = copy(log = log :+ text.trim)

    def addError(msg: String): ImportMessages = copy(errors = errors :+ msg)

    def addWarning(msg: String): ImportMessages = copy(warnings = warnings :+ msg)
  }

  case object ImportMessages {
    def empty = ImportMessages(Vector.empty, Vector.empty, Vector.empty)
  }

  trait SbtProcessBuildEvent extends MessageEvent {
    // TODO open log or something?
    override def getNavigatable(project: Project): Navigatable = null
  }


  case class SbtProcessBuildWarning(parentId: Any, message: String)
    extends SbtBuildEvent(parentId, MessageEvent.Kind.WARNING, "warnings", message) with SbtProcessBuildEvent

  case class SbtProcessBuildError(parentId: Any, message: String)
    extends SbtBuildEvent(parentId, MessageEvent.Kind.ERROR, "errors", message) with SbtProcessBuildEvent

}
