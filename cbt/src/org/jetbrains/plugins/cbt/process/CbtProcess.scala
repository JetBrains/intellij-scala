package org.jetbrains.plugins.cbt.process

import java.io.File

import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationEvent, ExternalSystemTaskNotificationListener}
import com.intellij.openapi.externalSystem.service.notification.{ExternalSystemNotificationManager, NotificationSource}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.cbt.project.CbtProjectSystem
import org.jetbrains.plugins.cbt.project.settings.{CbtExecutionSettings, CbtSystemSettings}
import org.jetbrains.plugins.cbt.project.structure.CbtProjectImporingException
import org.jetbrains.plugins.cbt.runner.internal.{CbtImportConfigurationFactory, CbtImportConfigurationType}
import org.jetbrains.plugins.cbt.runner.{CbtOutputFilter, CbtProcessListener}
import org.jetbrains.plugins.cbt.settings.CbtGlobalSettings

import scala.sys.process.{Process, ProcessLogger}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

object CbtProcess {
  def buildInfoXml(root: File,
                   settings: CbtExecutionSettings,
                   project: Project,
                   taskListener: Option[(ExternalSystemTaskId,
                     ExternalSystemTaskNotificationListener)]): Try[Elem] = {
    val buildParams = {
      val extraModulesStr = settings.extraModules.mkString(":")
      val needCbtLibsStr = settings.isCbt.unary_!.toString
      Seq("--extraModules", extraModulesStr, "--needCbtLibs", needCbtLibsStr)
    }
    val finished = new Semaphore
    finished.down()

    val listener = new CbtProcessListener {
      val textBuilder = new StringBuilder

      override def onComplete(exitCode: Int): Unit = {
        Thread.sleep(500)
        finished.up()
      }

      override def onTextAvailable(text: String, stderr: Boolean): Unit = {
        if (!stderr)
          textBuilder.append(text)
      }
    }

    val outputFilter = new CbtOutputFilter {
      override def filter(text: String, outputType: Key[_]): Boolean = outputType match {
        case ProcessOutputTypes.STDERR => true
        case _ => false
      }
    }

    val configuration = new CbtImportConfigurationFactory(settings.useDirect,
      CbtImportConfigurationType.instance,
      listener, Some(outputFilter))
      .createTemplateConfiguration(project)
    val runnerSettings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
    runnerSettings.setSingleton(true)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      DefaultJavaProgramRunner.getInstance, runnerSettings, project)
    ExecutionManager.getInstance(project).restartRunProfile(environment)
    finished.waitFor()
    Try(XML.loadString(listener.textBuilder.mkString))
  }

  def generateGiter8Template(template: String, project: Project, root: File): Try[String] =
    runAction(Seq("tools", "g8", template), useDirect = true, root, Option(project), None)

  def runAction(action: Seq[String],
                useDirect: Boolean,
                root: File,
                projectOpt: Option[Project],
                taskListener: Option[(ExternalSystemTaskId,
                  ExternalSystemTaskNotificationListener)]): Try[String] = {
    projectOpt.foreach { project =>
      ExternalSystemNotificationManager.getInstance(project)
        .clearNotifications(NotificationSource.PROJECT_SYNC, CbtProjectSystem.Id)
    }
    val onOutput = (text: String, stderr: Boolean) => {
      if (stderr) {
        taskListener.foreach {
          case (id, l) =>
            l.onStatusChange(new ExternalSystemTaskNotificationEvent(id, text))
        }
      }
    }

    val outputHandler =
      new CbtOutputListener(onOutput, projectOpt, NotificationSource.PROJECT_SYNC)
    val logger = ProcessLogger(
      text => outputHandler.parseLine(text, stderr = false),
      text => outputHandler.parseLine(text + '\n', stderr = true))

    val cbtExecutable =
      projectOpt
        .map(cbtExePath)
        .getOrElse(lastUsedCbtExePath)

    val task = Seq(cbtExecutable) ++ (if (useDirect) Seq("direct") else Seq.empty) ++ action
    val exitCode = Process(task, root) ! logger
    exitCode match {
      case 0 =>
        Success(outputHandler.stdOut)
      case _ =>
        Failure(new CbtProjectImporingException(outputHandler.stdErr.mkString("\n")))
    }
  }

  def cbtExePath(project: Project): String = {
    val path = CbtSystemSettings.instance(project).cbtExePath
    if (path.trim.isEmpty) lastUsedCbtExePath
    else path
  }

  def lastUsedCbtExePath: String =
    CbtGlobalSettings.instance.lastUsedCbtExePath
}
