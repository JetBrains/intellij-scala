package org.jetbrains.plugins.scala.console.actions

import com.intellij.execution._
import com.intellij.execution.configurations.{ConfigurationType, ConfigurationTypeUtil, RunConfiguration}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.console.configuration.ScalaConsoleConfigurationType
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.project.ProjectExt

import scala.collection.JavaConverters.collectionAsScalaIterableConverter

class RunConsoleAction extends AnAction(
  ScalaBundle.message("scalarepl.menu.action.text"),
  ScalaBundle.message("scalarepl.menu.action.description"),
  Icons.SCALA_CONSOLE
) with RunConsoleAction.RunActionBase[ScalaConsoleConfigurationType] {

  override protected def getNewSettingName: String = ScalaBundle.message("scala.console.actions.scala.repl")

  override def update(e: AnActionEvent): Unit = {
    if (e.getProject == null || e.getProject.isDisposed) return

    if(!e.getProject.hasScala) {
      ScalaActionUtil.disablePresentation(e)
    } else if(e.getPlace == ActionPlaces.PROJECT_VIEW_POPUP) {
      ScalaActionUtil.enableAndShowIfInScalaFile(e)
    } else {
      ScalaActionUtil.enablePresentation(e)
    }
  }

  override def actionPerformed(e: AnActionEvent): Unit =
    doRunAction(e)

  override protected def getMyConfigurationType: ScalaConsoleConfigurationType =
    ConfigurationTypeUtil.findConfigurationType(classOf[ScalaConsoleConfigurationType])
}

object RunConsoleAction {

  private def runFromSetting(setting: RunnerAndConfigurationSettings, runManagerEx: RunManagerEx, project: Project): Unit = {
    val configuration = setting.getConfiguration
    runManagerEx.setTemporaryConfiguration(setting)
    val runExecutor = DefaultRunExecutor.getRunExecutorInstance
    val runner = ProgramRunner.getRunner(runExecutor.getId, configuration)
    if (runner != null) {
      try {
        val builder: ExecutionEnvironmentBuilder = new ExecutionEnvironmentBuilder(project, runExecutor)
        builder.runnerAndSettings(runner, setting)
        runner.execute(builder.build())
      }
      catch {
        case e: ExecutionException =>
          //noinspection ReferencePassedToNls
          Messages.showErrorDialog(project, e.getMessage, ExecutionBundle.message("error.common.title"))
      }
    }
  }

  def runExisting(setting: RunnerAndConfigurationSettings, runManagerEx: RunManagerEx, project: Project): Unit =
    inReadAction {
      runFromSetting(setting, runManagerEx, project)
    }

  def createAndRun(configurationType: ConfigurationType, runManagerEx: RunManagerEx,
                   project: Project, name: String, handler: RunConfiguration => Unit): Unit =
    inReadAction {
      val factory  = configurationType.getConfigurationFactories.apply(0)
      val setting = RunManager.getInstance(project).createConfiguration(name, factory)
      handler(setting.getConfiguration)
      runFromSetting(setting, runManagerEx, project)
    }

  trait RunActionBase[T <: ConfigurationType] {
    protected def getMyConfigurationType: T

    protected def getNewSettingName: String

    protected def getRunConfigurationHandler: RunConfiguration => Unit = (_: RunConfiguration) => {}

    protected def checkFile(@Nullable psiFile: PsiFile): Boolean = true

    protected final def doRunAction(e: AnActionEvent): Unit = {
      val dataContext = e.getDataContext
      val file = CommonDataKeys.PSI_FILE.getData(dataContext)
      val project = CommonDataKeys.PROJECT.getData(dataContext)

      if (project == null || !checkFile(file)) return

      val runManagerEx = RunManagerEx.getInstanceEx(project)
      val configurationType = getMyConfigurationType
      val settings = runManagerEx.getConfigurationSettingsList(configurationType).asScala

      settings.headOption match {
        case Some(setting) =>
          RunConsoleAction.runExisting(setting, runManagerEx, project)
        case _ =>
          RunConsoleAction.createAndRun(configurationType, runManagerEx, project, getNewSettingName, getRunConfigurationHandler)
      }
    }
  }
}