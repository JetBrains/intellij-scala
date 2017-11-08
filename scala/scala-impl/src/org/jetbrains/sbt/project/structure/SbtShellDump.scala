package org.jetbrains.sbt.project.structure

import java.io.File
import java.util.{Collections, UUID}

import com.intellij.build.SyncViewManager
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.event._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, Output, TaskComplete, TaskStart}

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
    case (messages, Output(text)) =>
      if (text.contains("(i)gnore")) {
        // TODO check if this works correctly at all, or why not
        shell.send("i" + System.lineSeparator)
        val msg = "Error importing sbt project. Please check sbt shell output."
        val ex = new ExternalSystemException(msg)
        val failure = new FailureImpl(msg, "error during sbt import", Collections.emptyList())
        val timestamp = System.currentTimeMillis()
        val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
          dumpTaskId, null, taskDescriptor,
          new FailureResultImpl(taskDescriptor.getEventTime, timestamp, Collections.singletonList(failure))
        )
        val event = new ExternalSystemTaskExecutionEvent(id, finishEvent)
        notifications.onFailure(id, ex) // TODO redundant?
        notifications.onStatusChange(event)
      } else if (text.startsWith("[warn]")) {
        val messageEvent = new MessageEventImpl(dumpTaskId, MessageEvent.Kind.WARNING, "warnings", text.stripPrefix("[warn]"))
        viewManager.onEvent(messageEvent)
      } else if (text.startsWith("[error]")) {
        val messageEvent = new MessageEventImpl(dumpTaskId, MessageEvent.Kind.ERROR, "errors", text.stripPrefix("[error]"))
        viewManager.onEvent(messageEvent)
      } else {

        val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
          dumpTaskId, null, taskDescriptor, messages.size, -1, "lines"
        )
        val event = new ExternalSystemTaskExecutionEvent(id, progressEvent)

        notifications.onStatusChange(event)
        notifications.onTaskOutput(id, text, true)
      }
      messages.append(System.lineSeparator).append(text.trim)
  }

}
