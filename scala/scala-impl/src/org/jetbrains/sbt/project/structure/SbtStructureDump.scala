package org.jetbrains.sbt.project.structure

import java.io.{BufferedWriter, File, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Collections, UUID}

import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.MessageEvent.Kind._
import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.event._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.sbt.project.SbtProjectResolver.{ImportCancelledException, path}
import org.jetbrains.sbt.project.structure.SbtStructureDump._
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication._
import org.jetbrains.sbt.shell.event.{SbtBuildEvent, SbtShellBuildError}
import org.jetbrains.sbt.using

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

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
        "-Didea.managed=true" +:
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
    }
    .recoverWith {
      case fail => Failure(ImportCancelledException(fail))
    }

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

    var messages = ImportMessages.empty

    def update(textRaw: String): Unit = {
      val text = textRaw.trim
      messages = messages.appendMessage(text)

      if (text.nonEmpty) {
        val buildEv = buildEvent(text, SbtProcessBuildWarning(taskId, _), SbtProcessBuildError(taskId, _))
        for {
          event <- buildEv
          vm <- viewManager
        } {
          messages = reportEvent(messages, vm, event)
        }

        val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
          dumpTaskId, null, taskDescriptor, 1, -1, "events")
        val statusEvent = new ExternalSystemTaskExecutionEvent(taskId, progressEvent)

        notifications.onStatusChange(statusEvent)
        notifications.onTaskOutput(taskId, text + System.lineSeparator, true)
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
        throw ImportCancelledException(new Exception("task canceled"))
      } else messages
    }
  }
}

object SbtStructureDump {

  private val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

  private val WARN_PREFIX = "[warn]"
  private val ERROR_PREFIX = "[error]"

  private def buildEvent(text: String, toWarning: String => MessageEvent, toError: String => MessageEvent): Option[MessageEvent] =
    if (text.startsWith("[warn]")) {
      val strippedText = text.stripPrefix(WARN_PREFIX).trim
      Option(toWarning(strippedText))
    } else if (text.startsWith("[error]")) {
      val strippedText = text.stripPrefix(ERROR_PREFIX).trim
      Option(toError(strippedText))
    } else None

  private def reportEvent(messages: ImportMessages, viewManager:SyncViewManager, event: MessageEvent): ImportMessages = {
    val taskId = event.getId
    event.getKind match {
      case WARNING =>
        // report only that there is an error, until we can parse output more precisely
        if (messages.warnings.isEmpty) {
          val reportedWarning = SbtProcessBuildWarning(taskId, "error during import")
          viewManager.onEvent(reportedWarning)
        }
        messages.addWarning(event.getMessage)
      case ERROR =>
        // report only that there is an error, until we can parse output more precisely
        if (messages.errors.isEmpty) {
          val reportedError = SbtProcessBuildError(taskId, "error during import")
          viewManager.onEvent(reportedError)
        }
        messages.addError(event.getMessage)
      case INFO | SIMPLE | STATISTICS =>
        messages
    }
  }

  private def dumpMessageAggregator(id: ExternalSystemTaskId,
                                    dumpTaskId: String,
                                    taskDescriptor: TaskOperationDescriptor,
                                    shell: SbtShellCommunication,
                                    notifications: ExternalSystemTaskNotificationListener,
                                    viewManager: SyncViewManager
                                   ): EventAggregator[ImportMessages] = {
    case (messages, TaskStart) =>
      val startEvent = new ExternalSystemStartEventImpl[TaskOperationDescriptor](dumpTaskId, null, taskDescriptor)
      val statusEvent = new ExternalSystemTaskExecutionEvent(id, startEvent)
      notifications.onStatusChange(statusEvent)
      messages

    case (messages, TaskComplete) =>
      val result =
        if (messages.errors.isEmpty)
          new SuccessResultImpl(taskDescriptor.getEventTime, System.currentTimeMillis(), true)
        else {
          val fails = messages.errors.map { msg =>
            new FailureImpl(msg, msg, Collections.emptyList())
          }
          new FailureResultImpl(taskDescriptor.getEventTime, System.currentTimeMillis(), fails.asJava)
        }

      val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](dumpTaskId, null, taskDescriptor, result)
      val statusEvent = new ExternalSystemTaskExecutionEvent(id, finishEvent)
      notifications.onStatusChange(statusEvent)
      messages

    case (messages, ErrorWaitForInput) =>
      val msg = "import errors, project reload aborted"
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

      messages.addError(msg)

    case (messages, Output(raw)) =>
      val text = raw.trim
      // report warnings / errors
      val buildEv = buildEvent(text, SbtProcessBuildWarning(dumpTaskId, _), SbtShellBuildError(dumpTaskId, _))
      buildEv.foreach(reportEvent(messages, viewManager, _))

      val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
        dumpTaskId, null, taskDescriptor, 1, -1, "events"
      )
      val event = new ExternalSystemTaskExecutionEvent(id, progressEvent)

      notifications.onStatusChange(event)
      notifications.onTaskOutput(id, text + System.lineSeparator, true)

      messages.appendMessage(text)
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
