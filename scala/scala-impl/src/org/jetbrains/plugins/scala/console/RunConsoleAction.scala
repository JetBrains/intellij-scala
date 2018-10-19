package org.jetbrains.plugins.scala
package console

import com.intellij.execution._
import com.intellij.execution.configurations.{ConfigurationType, ConfigurationTypeUtil, RunConfiguration}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.03.2009
 */

class RunConsoleAction extends AnAction with RunConsoleAction.RunActionBase[ScalaConsoleConfigurationType] {
  override def update(e: AnActionEvent) {
    e.getPresentation.setIcon(Icons.SCALA_CONSOLE)
    ScalaActionUtil.enableAndShowIfInScalaFile(e)
  }

  def actionPerformed(e: AnActionEvent) {
    doRunAction(e)
  }

  override protected def getMyConfigurationType: ScalaConsoleConfigurationType = 
    ConfigurationTypeUtil.findConfigurationType(classOf[ScalaConsoleConfigurationType])

  override protected def getNewSettingName: String = "Scala Console"

  override protected def checkFile(psiFile: PsiFile): Boolean = psiFile.isInstanceOf[ScalaFile]
}

object RunConsoleAction {
  private def runFromSetting(setting: RunnerAndConfigurationSettings, runManagerEx: RunManagerEx, project: Project) {
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
          Messages.showErrorDialog(project, e.getMessage, ExecutionBundle.message("error.common.title"))
      }
    }
  }
  
  def runExisting(setting: RunnerAndConfigurationSettings, runManagerEx: RunManagerEx, project: Project) {
    extensions.inReadAction {
      runFromSetting(setting, runManagerEx, project)
    }
  }
  
  def createAndRun(configurationType: ConfigurationType, runManagerEx: RunManagerEx, 
                   project: Project, name: String, handler: RunConfiguration => Unit) {
    extensions.inReadAction {
      val factory  = configurationType.getConfigurationFactories.apply(0)
      val setting = RunManager.getInstance(project).createConfiguration(name, factory)
      handler(setting.getConfiguration)
      runFromSetting(setting, runManagerEx, project)
    }
  }
  
  trait RunActionBase[T <: ConfigurationType] {
    protected def getMyConfigurationType: T

    protected def getNewSettingName: String

    protected def getRunConfigurationHandler: RunConfiguration => Unit = (_: RunConfiguration) => {}

    protected def checkFile(psiFile: PsiFile): Boolean
    
    def doRunAction(e: AnActionEvent) {
      val dataContext = e.getDataContext
      val file = CommonDataKeys.PSI_FILE.getData(dataContext)
      val project = CommonDataKeys.PROJECT.getData(dataContext)
      
      if (file == null || project == null || !checkFile(file)) return

      val runManagerEx = RunManagerEx.getInstanceEx(project)
      val configurationType = getMyConfigurationType
      val settings = runManagerEx.getConfigurationSettingsList(configurationType)

      settings.forEach {
        setting =>
          RunConsoleAction.runExisting(setting, runManagerEx, project)
          return
      }

      RunConsoleAction.createAndRun(configurationType, runManagerEx, project, getNewSettingName, getRunConfigurationHandler)
    }
  }
}