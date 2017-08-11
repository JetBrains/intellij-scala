package org.jetbrains.plugins.scala
package console

import com.intellij.execution._
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.actionSystem._
import com.intellij.openapi.ui.Messages
import com.intellij.util.ActionRunner
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import scala.collection.JavaConverters._

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
      val file = CommonDataKeys.PSI_FILE.getData(e.getDataContext)
      file match {
        case _: ScalaFile => enable()
        case _ => disable()
      }
    }
    catch {
      case _: Exception => disable()
    }
  }

  def actionPerformed(e: AnActionEvent) {
    val dataContext = e.getDataContext
    val file = CommonDataKeys.PSI_FILE.getData(dataContext)
    val project = CommonDataKeys.PROJECT.getData(dataContext)
    file match {
      case file: ScalaFile =>
        val runManagerEx = RunManagerEx.getInstanceEx(file.getProject)
        val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[ScalaConsoleConfigurationType])
        val settings = runManagerEx.getConfigurationSettingsList(configurationType).asScala

        def execute(setting: RunnerAndConfigurationSettings) {
          val configuration = setting.getConfiguration.asInstanceOf[ScalaConsoleRunConfiguration]
          runManagerEx.setSelectedConfiguration(setting)
          val runExecutor = DefaultRunExecutor.getRunExecutorInstance
          val runner = RunnerRegistry.getInstance().getRunner(runExecutor.getId, configuration)
          if (runner != null) {
            try {
              val builder: ExecutionEnvironmentBuilder = new ExecutionEnvironmentBuilder(project, runExecutor)
              builder.runnerAndSettings(runner, setting)
              runner.execute(builder.build())
            }
            catch {
              case e: ExecutionException =>
                Messages.showErrorDialog(file.getProject, e.getMessage, ExecutionBundle.message("error.common.title"))
            }
          }
        }

        for (setting <- settings) {
          ActionRunner.runInsideReadAction(() => {
            execute(setting)
          })
          return
        }
        ActionRunner.runInsideReadAction(() => {
          val factory: ScalaConsoleRunConfigurationFactory =
            configurationType.getConfigurationFactories.apply(0).asInstanceOf[ScalaConsoleRunConfigurationFactory]
          val setting = RunManager.getInstance(project).createRunConfiguration("Scala Console", factory)

          runManagerEx.setTemporaryConfiguration(setting)
          execute(setting)
        })
      case _ =>
    }
  }
}
