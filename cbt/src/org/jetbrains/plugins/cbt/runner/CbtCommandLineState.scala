package org.jetbrains.plugins.cbt.runner

import java.io.OutputStream

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.{CommandLineState, GeneralCommandLine}
import com.intellij.execution.process._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationSource}
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.cbt.process.{CbtOutputListener, CbtProcess}
import org.jetbrains.plugins.cbt.project.CbtProjectSystem

import scala.collection.JavaConverters._


class CbtCommandLineState(taskData: CbtTask, environment: ExecutionEnvironment)
  extends CommandLineState(environment) {

  import taskData._

  override def startProcess(): ProcessHandler = {
    val isGlobalTask = taskData.moduleOpt.isEmpty
    val canRun =
      if (isGlobalTask)
        Option(environment.getUserData(CbtProcess.firstRunKey)).getOrElse(false)
      else true
    if (isGlobalTask)
      environment.putUserData(CbtProcess.firstRunKey, false)
    if (canRun) {
      ExternalSystemNotificationManager.getInstance(environment.getProject)
        .clearNotifications(NotificationSource.TASK_EXECUTION, CbtProjectSystem.Id)
      val arguments =
        Seq(CbtProcess.cbtExePath(environment.getProject)) ++
          cbtOptions ++
          (if (useDirect && !cbtOptions.contains("direct")) Seq("direct") else Seq.empty) ++
          Seq(task) ++
          taskArguments
      val commandLine = new GeneralCommandLine(arguments.asJava)
        .withWorkDirectory(workingDir)
      val hanlder = new CbtProcessHandler(commandLine)
      hanlder
    } else
      throw new ExecutionException(
        """The action can not be performed.
          | To reimport yout project, please use CBT project panel""".stripMargin)
  }

  class CbtProcessHandler(commandLine: GeneralCommandLine)
    extends KillableProcessHandler(commandLine)
      with AnsiEscapeDecoder.ColoredTextAcceptor {

    setShouldKillProcessSoftly(false)
    addProcessListener(new ProcessListener {
      override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {}

      override def startNotified(event: ProcessEvent): Unit = {}

      override def processTerminated(event: ProcessEvent): Unit =
        listenerOpt.foreach(_.onComplete(event.getExitCode))

      override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {}
    })

    private val myAnsiEscapeDecoder =
      new AnsiEscapeDecoder
    private val cbtOutputListener =
      new CbtOutputListener(onOutput, Option(environment.getProject), NotificationSource.TASK_EXECUTION)

    override def notifyTextAvailable(text: String, outputType: Key[_]): Unit = {
      outputType match {
        case ProcessOutputTypes.STDERR =>
          cbtOutputListener.parseLine(text, stderr = true)
        case ProcessOutputTypes.STDOUT =>
          cbtOutputListener.parseLine(text, stderr = false)
        case _ =>
      }
      myAnsiEscapeDecoder.escapeText(text, outputType, this)
    }

    override def coloredTextAvailable(text: String, outputType: Key[_]): Unit = {
      val printText =
        filterOpt.forall(_.filter(text, outputType))
      if (printText)
        textAvailable(text, outputType)
    }

    protected def textAvailable(text: String, attributes: Key[_]): Unit = {
      super.notifyTextAvailable(text, attributes)
    }

    private def onOutput(text: String, stderr: Boolean): Unit = {
      listenerOpt.foreach(_.onTextAvailable(text, stderr))
    }

  }

}
