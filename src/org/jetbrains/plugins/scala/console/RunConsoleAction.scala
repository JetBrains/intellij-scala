package org.jetbrains.plugins.scala
package console

import com.intellij.execution._
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.ui.Messages
import com.intellij.util.ActionRunner
import icons.Icons
import lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.03.2009
 */

class RunConsoleAction extends AnAction {
  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(Icons.SCALA_CONSOLE)
    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }
    try {
      val file = LangDataKeys.PSI_FILE.getData(e.getDataContext)
      file match {
        case _: ScalaFile => enable()
        case _ => disable()
      }
    }
    catch {
      case e: Exception => disable()
    }
  }

  def actionPerformed(e: AnActionEvent) {
    val dataContext = e.getDataContext
    val file = LangDataKeys.PSI_FILE.getData(dataContext)
    val project = PlatformDataKeys.PROJECT.getData(dataContext)
    file match {
      case file: ScalaFile => {
        val runManagerEx = RunManagerEx.getInstanceEx(file.getProject)
        val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[ScalaConsoleConfigurationType])
        val settings = runManagerEx.getConfigurationSettings(configurationType)

        def execute(setting: RunnerAndConfigurationSettings) {
          val configuration = setting.getConfiguration.asInstanceOf[ScalaConsoleRunConfiguration]
          runManagerEx.setSelectedConfiguration(setting)
          val runExecutor = DefaultRunExecutor.getRunExecutorInstance
          val runner = RunnerRegistry.getInstance().getRunner(runExecutor.getId, configuration)
          if (runner != null) {
            try {
              runner.execute(runExecutor, new ExecutionEnvironment(runner, setting, project))
            }
            catch {
              case e: ExecutionException =>
                Messages.showErrorDialog(file.getProject, e.getMessage, ExecutionBundle.message("error.common.title"))
            }
          }
        }
        for (setting <- settings) {
          ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
            def run() {
              execute(setting)
            }
          })
          return
        }
        ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
          def run() {
            val factory: ScalaConsoleRunConfigurationFactory =
              configurationType.getConfigurationFactories.apply(0).asInstanceOf[ScalaConsoleRunConfigurationFactory]
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