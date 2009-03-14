package org.jetbrains.plugins.scala.script.console

import com.intellij.execution._
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.{ProgramRunner, ExecutionEnvironment}
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, DataConstants}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.util.ActionRunner
import icons.Icons
import lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.03.2009
 */

class RunConsoleAction extends AnAction {
  override def update(e: AnActionEvent): Unit = {
    val presentation = e.getPresentation
    presentation.setIcon(Icons.SCALA_CONSOLE)
    def enable {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val dataContext = e.getDataContext
      val file = dataContext.getData(DataConstants.PSI_FILE)
      file match {
        case _: ScalaFile => enable
        case _ => disable
      }
    }
    catch {
      case e: Exception => disable
    }

  }

  def actionPerformed(e: AnActionEvent): Unit = {
    val file = e.getDataContext.getData(DataConstants.PSI_FILE)
    file match {
      case file: ScalaFile => {
        val runManagerEx = RunManagerEx.getInstanceEx(file.getProject)
        val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[ScalaScriptConsoleConfigurationType])
        val settings = runManagerEx.getConfigurationSettings(configurationType)

        def execute(setting: RunnerAndConfigurationSettingsImpl) {
          val configuration = setting.getConfiguration.asInstanceOf[ScalaScriptConsoleRunConfiguration]
          runManagerEx.setActiveConfiguration(setting)
          val runExecutor = DefaultRunExecutor.getRunExecutorInstance
          val runner = RunnerRegistry.getInstance().getRunner(runExecutor.getId, configuration)
          if (runner != null) {
            try {
              runner.execute(runExecutor, new ExecutionEnvironment(runner, setting, e.getDataContext));
            }
            catch {
              case e: ExecutionException =>
                Messages.showErrorDialog(file.getProject, e.getMessage, ExecutionBundle.message("error.common.title"));
            }
          }
        }
        for (setting <- settings) {
          val conf = setting.getConfiguration.asInstanceOf[ScalaScriptConsoleRunConfiguration]
          ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
            def run: Unit = {
              execute(setting)
            }
          })
          return
        }
        ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
          def run: Unit = {
            val factory: ScalaScriptConsoleRunConfigurationFactory =
              configurationType.getConfigurationFactories.apply(0).asInstanceOf[ScalaScriptConsoleRunConfigurationFactory]
            val setting = RunManagerEx.getInstanceEx(file.getProject).createConfiguration("Scala Console", factory)

            runManagerEx.setTemporaryConfiguration(setting)
            execute(setting)
          }
        })
      }
      case _ =>
    }
  }
}