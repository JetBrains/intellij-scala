package org.jetbrains.sbt.project.structure

import java.io.File
import java.util.{Collections, UUID}

import com.intellij.build.SyncViewManager
import com.intellij.build.events.impl.AbstractBuildEvent
import com.intellij.build.events.{MessageEvent, MessageEventResult}
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.event._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import org.jetbrains.sbt.shell.SbtShellCommunication._
import org.jetbrains.sbt.shell.{SbtProcessManager, SbtShellCommunication, SbtShellRunner}

import scala.collection.JavaConverters._
import scala.concurrent.Future

object SbtShellDump {

  def dumpFromShell(id: ExternalSystemTaskId,
                    dir: File,
                    structureFilePath: String,
                    options: Seq[String],
                    notifications: ExternalSystemTaskNotificationListener,
                   ): Future[ImportMessages] = {

    notifications.onStart(id, dir.getCanonicalPath)

    val project = id.findProject() // assume responsibility of caller not to call dumpFromShell with null project
    val viewManager = ServiceManager.getService(project, classOf[SyncViewManager])
    val shell = SbtShellCommunication.forProject(project)

    val optString = options.mkString(" ")
    val setCmd = s"""set _root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions in Global := "$optString""""
    val cmd = s";reload; $setCmd ;*/*:dumpStructureTo $structureFilePath; session clear-all"

    val taskDescriptor =
      new TaskOperationDescriptorImpl("dump project structure from sbt shell", System.currentTimeMillis(), "project-structure-dump")
    val aggregator = messageAggregator(id, s"dump:${UUID.randomUUID()}", taskDescriptor, shell, notifications, viewManager)

    shell.command(cmd, ImportMessages(Seq.empty, Seq.empty, ""), aggregator, showShell = false)
  }

  private def messageAggregator(id: ExternalSystemTaskId,
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
      val buildEvent = SbtBuildEvent(dumpTaskId, MessageEvent.Kind.ERROR, "errors", msg)

      notifications.onStatusChange(statusEvent)
      notifications.onFailure(id, ex) // TODO redundant?
      viewManager.onEvent(buildEvent)

      // TODO check if this works correctly at all, or why not
      shell.send("i" + System.lineSeparator)

      stats.addError(msg)

    case (stats, Output(text)) =>
      // report warnings / errors
      val buildEv = buildEvent(text, dumpTaskId)

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

  def buildEvent(line: String, dumpTaskId: String): Option[MessageEvent] = {
    val text = line.trim
    if (text.startsWith("[warn]")) {
      Option(SbtBuildEvent(dumpTaskId, MessageEvent.Kind.WARNING, "warnings", text.stripPrefix("[warn]").trim))
    } else if (text.startsWith("[error]")) {
      Option(SbtBuildEvent(dumpTaskId, MessageEvent.Kind.ERROR, "errors", text.stripPrefix("[error]").trim))
    } else None
  }

  case class ImportMessages(warnings: Seq[String], errors: Seq[String], log: String) {

    def appendMessage(text: String): ImportMessages = copy(
        log = log + System.lineSeparator + text.trim
      )

    def addError(msg: String): ImportMessages = copy(errors = errors :+ msg)

    def addWarning(msg: String): ImportMessages = copy(warnings = warnings :+ msg)
  }

  case class SbtBuildEvent(parentId: Any, kind: MessageEvent.Kind, group: String, message: String)
    extends AbstractBuildEvent(new Object, parentId, System.currentTimeMillis(), message) with MessageEvent {
    override def getKind: MessageEvent.Kind = kind
    override def getGroup: String = group

    override def getResult: MessageEventResult =
      new MessageEventResult() {
        override def getKind: MessageEvent.Kind = kind
      }

    override def getNavigatable(project: Project): Navigatable = {
      val shell = SbtProcessManager.forProject(project).acquireShellRunner
      SbtShellNavigatable(shell) // TODO pass some kind of position info
    }
  }

  case class SbtShellNavigatable(shell: SbtShellRunner) extends Navigatable {

    override def navigate(requestFocus: Boolean): Unit =
      if (canNavigate) {
        shell.openShell(requestFocus)
      }

    override def canNavigate: Boolean = true

    override def canNavigateToSource: Boolean = true
  }

}
