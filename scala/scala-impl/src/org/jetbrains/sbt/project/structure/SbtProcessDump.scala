package org.jetbrains.sbt.project.structure

import java.io.{BufferedWriter, File, OutputStreamWriter, PrintWriter}
import java.nio.charset.Charset
import java.util.{Collections, UUID}
import java.util.concurrent.atomic.AtomicBoolean

import com.intellij.execution.process.OSProcessHandler
import com.intellij.openapi.externalSystem.model.task.event._
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener}
import org.jetbrains.sbt.project.SbtProjectResolver.{ImportCancelledException, path}
import org.jetbrains.sbt.project.structure.SbtProcessDump._
import org.jetbrains.sbt.using

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class SbtProcessDump {

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
                     ): Try[String] = {

    val startTime = System.currentTimeMillis()
    val optString = options.mkString(", ")

//    val statusUpdate = (message:String) =>
//      notifications.onStatusChange(new ExternalSystemTaskNotificationEvent(id, message.trim))

    val setCommands = Seq(
      s"""shellPrompt := { _ => "" }""",
      s"""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile") in _root_.sbt.Global := _root_.scala.Some(_root_.sbt.file("$structureFilePath"))""",
      s"""SettingKey[_root_.java.lang.String]("sbtStructureOptions") in _root_.sbt.Global := "$optString""""
    ).mkString("set _root_.scala.collection.Seq(", ",", ")")

    val sbtCommands = Seq(
      setCommands,
      s"""apply -cp "${path(sbtStructureJar)}" org.jetbrains.sbt.CreateTasks""",
      s"*/*:dumpStructure"
    ).mkString(";",";","")

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
        handle(process, taskId, dumpTaskId, taskDescriptor, notifications)
      }
      result.getOrElse("no output from sbt shell process available")
    }.orElse(Failure(ImportCancelledException))

    val endTime = System.currentTimeMillis()
    val operationResult = result match {
      case Success(_) =>
        new SuccessResultImpl(startTime, endTime, true)
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
                     taskDescriptor:TaskOperationDescriptor,
                     notifications: ExternalSystemTaskNotificationListener): Try[String] = {
    var lines = 0

    val output = StringBuilder.newBuilder

    def update(textRaw: String): Unit = {
      val text = textRaw.trim
      output.append(System.lineSeparator).append(text)
      if (text.nonEmpty) {
        lines += 1
        val progressEvent = new ExternalSystemStatusEventImpl[TaskOperationDescriptor](
          dumpTaskId, null, taskDescriptor, lines, -1, "lines"
        )
        val event = new ExternalSystemTaskExecutionEvent(taskId, progressEvent)

        notifications.onStatusChange(event)
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
      } else output.toString()
    }
  }

  private val cancellationFlag: AtomicBoolean = new AtomicBoolean(false)

  def cancel(): Unit = cancellationFlag.set(true)

}

object SbtProcessDump {

  private val SBT_PROCESS_CHECK_TIMEOUT_MSEC = 100

}
