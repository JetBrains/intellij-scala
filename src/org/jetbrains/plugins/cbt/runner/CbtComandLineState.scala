package org.jetbrains.plugins.cbt.runner

import com.intellij.execution.configurations.{CommandLineState, GeneralCommandLine}
import com.intellij.execution.process._
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationSource}
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.cbt.CbtOutputListener
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings

import scala.collection.JavaConverters._

class CbtComandLineState(task: String,
                         useDirect: Boolean,
                         workingDir: String,
                         callback: Option[() => Unit],
                         environment: ExecutionEnvironment)
  extends CommandLineState(environment) {

  override def startProcess(): ProcessHandler = {
    val factory = ProcessHandlerFactory.getInstance
    val arguments = Seq("cbt") ++
      (if (useDirect) Seq("direct") else Seq.empty) ++
      task.split(' ').map(_.trim).toSeq
    val commandLine = new GeneralCommandLine(arguments.asJava)
      .withWorkDirectory(workingDir)
    val hanlder = factory.createColoredProcessHandler(commandLine)
    hanlder.addProcessListener(new CbtProcessProcessListener)
    hanlder
  }

  private class CbtProcessProcessListener extends ProcessListener {

    val cbtOutputListener =
      new CbtOutputListener(onOutput, Option(environment.getProject), NotificationSource.TASK_EXECUTION)

    def onOutput(text: String, stderr: Boolean): Unit = {}

    override def processWillTerminate(event: ProcessEvent, willBeDestroyed: Boolean): Unit = {}

    override def startNotified(event: ProcessEvent): Unit = {
      ExternalSystemNotificationManager.getInstance(environment.getProject)
        .clearNotifications(NotificationSource.TASK_EXECUTION, CbtProjectSystem.Id)
    }

    override def processTerminated(event: ProcessEvent): Unit = {
      callback.foreach(_.apply)
    }

    override def onTextAvailable(event: ProcessEvent, outputType: Key[_]): Unit = {
      outputType match {
        case ProcessOutputTypes.STDERR =>
          cbtOutputListener.parseLine(event.getText, stderr = true)
        case ProcessOutputTypes.STDOUT =>
          cbtOutputListener.parseLine(event.getText, stderr = false)
        case _ =>
      }
    }
  }

}
