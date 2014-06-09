package org.jetbrains.plugins.scala.lang.scaladoc.generate

import com.intellij.CommonBundle
import com.intellij.analysis.{AnalysisScope, BaseAnalysisAction, BaseAnalysisActionDialog}
import com.intellij.execution.configurations._
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.{ExecutionEnvironment, ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.util.ExecutionErrorDialog
import com.intellij.execution.{ExecutionException, Executor, RunnerRegistry}
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import org.jetbrains.plugins.scala.console.ScalaConsoleConfigurationType
import org.jetbrains.plugins.scala.lang.scaladoc.generate.ScaladocAction.ScaladocRunConfiguration


/**
 * User: Dmitry Naidanov
 * Date: 01.10.11
 */
class ScaladocAction extends BaseAnalysisAction("Generate Scaladoc", "Scaladoc") {
  private var configurationDialog: ScaladocConsoleRunConfigurationForm = null

  private def disposeForm() {
    configurationDialog = null
  }

  def analyze(project: Project, scope: AnalysisScope) {
    var config: ScaladocConfiguration = null
    try {
      configurationDialog.saveSettings()
      config = new ScaladocConfiguration(configurationDialog, project, scope)
      try {
        val runConfig = new ScaladocRunConfiguration(project, configurationDialog, config)

        val runner: ProgramRunner[_ <: RunnerSettings] =
          RunnerRegistry.getInstance.getRunner(DefaultRunExecutor.EXECUTOR_ID, config)
        val builder: ExecutionEnvironmentBuilder =
          new ExecutionEnvironmentBuilder(project, DefaultRunExecutor.getRunExecutorInstance)
        builder.setRunProfile(config)
        builder.setRunnerAndSettings(runner,
          new RunnerAndConfigurationSettingsImpl(new RunManagerImpl(project, PropertiesComponent.getInstance()), runConfig, false))
        runner.execute(builder.build())
      } catch {
        case e: ExecutionException => ExecutionErrorDialog.show(e, CommonBundle.getErrorTitle, project)
      }
    }
    finally {
      disposeForm()
    }
  }

  override def canceled() {
    super.canceled()
    disposeForm()
  }

  override def getAdditionalActionSettings(project: Project, dialog: BaseAnalysisActionDialog): JComponent = {
    configurationDialog = new ScaladocConsoleRunConfigurationForm(project)
    configurationDialog.getOutputDirChooser.getDocument.addDocumentListener(new DocumentAdapter() {
      def textChanged(e: DocumentEvent) {
        updateAvailability(dialog)
      }
    })
    updateAvailability(dialog)
    configurationDialog.createCenterPanel()
  }

  private def updateAvailability(dialog: BaseAnalysisActionDialog) {
    dialog.setOKActionEnabled(!configurationDialog.getOutputDir.isEmpty)
  }
}

object ScaladocAction {

  // just stub entities, will never be invoked
  object ScaladocRunConfigurationFactory extends ConfigurationFactory(new ScalaConsoleConfigurationType) {
    override def createTemplateConfiguration(project: Project): RunConfiguration = new ScaladocRunConfiguration(project, null, null)
  }

  class ScaladocRunConfiguration(project: Project,
                                 dialog: ScaladocConsoleRunConfigurationForm,
                                 config: ScaladocConfiguration)
    extends RunConfigurationBase(project, ScaladocRunConfigurationFactory, "Genarate Scaladoc") {
    override def checkConfiguration() {}

    override def getConfigurationEditor: SettingsEditor[_ <: ScaladocRunConfiguration] = new SettingsEditor[ScaladocRunConfiguration]() {
      override def createEditor(): JComponent = dialog.createCenterPanel()

      override def resetEditorFrom(s: ScaladocRunConfiguration) {}

      override def applyEditorTo(s: ScaladocRunConfiguration) {}
    }

    override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = config.getState(executor, env)
  }
}