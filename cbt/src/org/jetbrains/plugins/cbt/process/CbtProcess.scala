package org.jetbrains.plugins.cbt.process

import java.io.File

import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.cbt.project.settings.{CbtExecutionSettings, CbtSystemSettings}
import org.jetbrains.plugins.cbt.runner.internal.{CbtTaskConfigurationFactory, CbtTaskConfigurationType}
import org.jetbrains.plugins.cbt.runner.{CbtOutputFilter, CbtProcessListener, CbtTask}
import org.jetbrains.plugins.cbt.settings.CbtGlobalSettings

import scala.util.Try
import scala.xml.{Elem, XML}

object CbtProcess {
  def buildInfoXml(root: File,
                   settings: CbtExecutionSettings,
                   listener: CbtProcessListener,
                   project: Project): Try[Elem] = {
    val taskArguments = {
      val extraModulesStr = settings.extraModules.mkString(":")
      val needCbtLibsStr = settings.isCbt.unary_!.toString
      Seq("--extraModules", extraModulesStr, "--needCbtLibs", needCbtLibsStr)
    }

    val outputFilter = new CbtOutputFilter {
      override def filter(text: String, outputType: Key[_]): Boolean = outputType match {
        case ProcessOutputTypes.STDERR => true
        case ProcessOutputTypes.SYSTEM => true
        case _ => false
      }
    }

    runTask(
      CbtTask("buildInfoXml",
        settings.useDirect,
        project,
        taskArguments = taskArguments,
        listenerOpt = Some(listener),
        filterOpt = Some(outputFilter))
    ).flatMap(xml => Try(XML.loadString(xml)))
  }

  def runTask(task: CbtTask): Try[String] = {
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

    val configuration =
      new CbtTaskConfigurationFactory(task.appendListener(listener),
        CbtTaskConfigurationType.instance)
        .createTemplateConfiguration(task.project)
    val runnerSettings =
      new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(task.project), configuration)
    runnerSettings.setSingleton(true)
    val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance,
      DefaultJavaProgramRunner.getInstance, runnerSettings, task.project)
    ExecutionManager.getInstance(task.project).restartRunProfile(environment)
    finished.waitFor()
    Try(listener.textBuilder.mkString)
  }

  def generateGiter8Template(template: String, project: Project, root: File): Try[String] = {
    val task = new CbtTask("tools", true, project, taskArguments = Seq("g8", template)) {
      override def workingDir: String = root.getAbsolutePath
    }
    runTask(task)
  }

  def cbtExePath(project: Project): String = {
    val path = CbtSystemSettings.instance(project).cbtExePath
    if (path.trim.isEmpty) lastUsedCbtExePath
    else path
  }

  def lastUsedCbtExePath: String =
    CbtGlobalSettings.instance.lastUsedCbtExePath
}
