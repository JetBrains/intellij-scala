package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{CommandLineState, GeneralCommandLine}
import com.intellij.execution.process._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationSource}
import com.intellij.openapi.util.Key
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.cbt.process.{CbtOutputListener, CbtProcess}
import org.jetbrains.plugins.cbt.project.CbtProjectSystem

import scala.collection.JavaConverters._

class CbtCommandLineState(task: String,
                          useDirect: Boolean,
                          workingDir: String,
                          listener: CbtProcessListener,
                          environment: ExecutionEnvironment,
                          options: Seq[String] = Seq.empty)
  extends CommandLineState(environment) {

  override def startProcess(): ProcessHandler = {
    ExternalSystemNotificationManager.getInstance(environment.getProject)
      .clearNotifications(NotificationSource.TASK_EXECUTION, CbtProjectSystem.Id)
    val arguments = 
      Seq(CbtProcess.cbtExePath(environment.getProject)) ++
      options ++
      (if (useDirect) Seq("direct") else Seq.empty) ++
      task.split(' ').map(_.trim).toSeq
    val commandLine = new GeneralCommandLine(arguments.asJava)
      .withWorkDirectory(workingDir)
    val hanlder = new CbtProcessHandler(commandLine)
    hanlder
  }

  class CbtProcessHandler(commandLine: GeneralCommandLine)
    extends KillableProcessHandler(commandLine)
      with AnsiEscapeDecoder.ColoredTextAcceptor {
    setShouldKillProcessSoftly(false)

    private val myColoredTextListeners =
      ContainerUtil.newArrayList[AnsiEscapeDecoder.ColoredTextAcceptor]
    private val myAnsiEscapeDecoder =
      new AnsiEscapeDecoder
    private val cbtOutputListener =
      new CbtOutputListener(onOutput, Option(environment.getProject), NotificationSource.TASK_EXECUTION)

    private def onOutput(text: String, stderr: Boolean): Unit = {
      listener.onTextAvailable(text, stderr)
    }

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

    override def coloredTextAvailable(text: String, attributes: Key[_]): Unit = {
      textAvailable(text, attributes)
      notifyColoredListeners(text, attributes)
    }

    protected def notifyColoredListeners(text: String, attributes: Key[_]): Unit = {
      for (listener <- myColoredTextListeners.asScala) {
        listener.coloredTextAvailable(text, attributes)
      }
    }

    protected def textAvailable(text: String, attributes: Key[_]): Unit = {
      super.notifyTextAvailable(text, attributes)
    }

    def addColoredTextListener(listener: AnsiEscapeDecoder.ColoredTextAcceptor): Unit = {
      myColoredTextListeners.add(listener)
    }

    def removeColoredTextListener(listener: AnsiEscapeDecoder.ColoredTextAcceptor): Unit = {
      myColoredTextListeners.remove(listener)
    }
  }

}
