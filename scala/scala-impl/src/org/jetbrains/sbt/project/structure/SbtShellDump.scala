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

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object SbtShellDump {

  def dumpFromShell(id: ExternalSystemTaskId,
                    dir: File,
                    structureFilePath: String,
                    options: Seq[String],
                    notifications: ExternalSystemTaskNotificationListener,
                   ): Try[String] = {

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
    val output = shell.command(cmd, new StringBuilder, aggregator, showShell = false)

    Await.ready(output, Duration.Inf) // TODO some kind of timeout / cancel mechanism

    output.value.get.map(_.toString())
  }

  private def messageAggregator(id: ExternalSystemTaskId,
                                dumpTaskId: String,
                                taskDescriptor: TaskOperationDescriptor,
                                shell: SbtShellCommunication,
                                notifications: ExternalSystemTaskNotificationListener,
                                viewManager: SyncViewManager
                               ): EventAggregator[StringBuilder] = {
    case (messages, TaskStart) =>
      val startEvent = new ExternalSystemStartEventImpl[TaskOperationDescriptor](dumpTaskId, null, taskDescriptor)
      val event = new ExternalSystemTaskExecutionEvent(id, startEvent)
      notifications.onStatusChange(event)
      messages

    case (messages, TaskComplete) =>
      val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
        dumpTaskId, null, taskDescriptor,
        new SuccessResultImpl(taskDescriptor.getEventTime, System.currentTimeMillis(), true)
      )
      val event = new ExternalSystemTaskExecutionEvent(id, finishEvent)
      notifications.onStatusChange(event)
      messages

    case (messages, ErrorWaitForInput) =>
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

      messages

    case (messages, Output(text)) =>
      // report warnings / errors
      buildEvent(text, dumpTaskId).foreach(viewManager.onEvent(_))

      val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
        dumpTaskId, null, taskDescriptor, messages.size, -1, "lines"
      )
      val event = new ExternalSystemTaskExecutionEvent(id, progressEvent)

      notifications.onStatusChange(event)
      notifications.onTaskOutput(id, text, true)

      messages.append(System.lineSeparator).append(text.trim)
  }

  def buildEvent(line: String, dumpTaskId: String): Option[MessageEvent] = {
    val text = line.trim
    if (text.startsWith("[warn]")) {
      Option(SbtBuildEvent(dumpTaskId, MessageEvent.Kind.WARNING, "warnings", text.stripPrefix("[warn]")))
    } else if (text.startsWith("[error]")) {
      Option(SbtBuildEvent(dumpTaskId, MessageEvent.Kind.ERROR, "errors", text.stripPrefix("[error]")))
    } else None
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
