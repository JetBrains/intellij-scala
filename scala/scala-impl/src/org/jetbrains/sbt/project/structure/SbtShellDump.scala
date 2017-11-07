package org.jetbrains.sbt.project.structure

import java.io.File
import java.util.UUID

import com.intellij.openapi.externalSystem.model.ExternalSystemException
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.model.task.event._
import org.jetbrains.sbt.shell.SbtShellCommunication
import org.jetbrains.sbt.shell.SbtShellCommunication.{EventAggregator, Output, TaskComplete, TaskStart}

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Try

object SbtShellDump {

  def dumpFromShell(id: ExternalSystemTaskId, dir: File,
                    shell: SbtShellCommunication,
                    structureFilePath: String,
                    options: Seq[String],
                    notifications: ExternalSystemTaskNotificationListener): Try[String] = {

    notifications.onStart(id, dir.getCanonicalPath)

    val optString = options.mkString(" ")
    val setCmd = s"""set _root_.org.jetbrains.sbt.StructureKeys.sbtStructureOptions in Global := "$optString""""
    val cmd = s";reload; $setCmd ;*/*:dumpStructureTo $structureFilePath"

    val taskDescriptor =
      new TaskOperationDescriptorImpl("dump project structure from sbt shell", System.currentTimeMillis(), "project-structure-dump")
    val aggregator = messageAggregator(id, s"dump:${UUID.randomUUID()}", taskDescriptor, shell, notifications)
    val output = shell.command(cmd, new StringBuilder, aggregator, showShell = false)

    Await.ready(output, Duration.Inf) // TODO some kind of timeout / cancel mechanism

    output.value.get.map(_.toString())
  }

  private def messageAggregator(id: ExternalSystemTaskId,
                                dumpTaskId: String,
                                taskDescriptor: TaskOperationDescriptor,
                                shell: SbtShellCommunication,
                                notifications: ExternalSystemTaskNotificationListener): EventAggregator[StringBuilder] = {
    case (messages, TaskStart) =>
      val startEvent = new ExternalSystemStartEventImpl[TaskOperationDescriptor](dumpTaskId, null, taskDescriptor)
      val event = new ExternalSystemTaskExecutionEvent(id, startEvent)
      notifications.onStatusChange(event)
      messages
    case (messages, TaskComplete) =>
      val finishEvent = new ExternalSystemFinishEventImpl[TaskOperationDescriptor](
        dumpTaskId, null, taskDescriptor,
        new SuccessResultImpl(0, System.currentTimeMillis(), true)
      )
      val event = new ExternalSystemTaskExecutionEvent(id, finishEvent)
      notifications.onStatusChange(event)
      messages
    case (messages, Output(text)) =>
      if (text.contains("(i)gnore")) {
        shell.send("i" + System.lineSeparator)
        val ex = new ExternalSystemException("Error importing sbt project. Please check sbt shell output.")
        notifications.onFailure(id, ex)
        notifications.onFailure(id,ex)
        //      } else if (text.startsWith("[warn]")) {
        //        val messageEvent = new MessageEventImpl(dumpTaskId, MessageEvent.Kind.WARNING, null, text.stripPrefix("[warning]"))
        //        val event = new ExternalSystemTaskExecutionEvent(id, messageEvent)
        //        notifications.onStatusChange(event)
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
