package org.jetbrains.plugins.scala.lang.scaladoc.generate

import com.intellij.CommonBundle
import com.intellij.analysis.{AnalysisScope, BaseAnalysisAction, BaseAnalysisActionDialog}
import com.intellij.execution.configurations._
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.{ExecutionEnvironment, ExecutionEnvironmentBuilder, ProgramRunner}
import com.intellij.execution.util.ExecutionErrorDialog
import com.intellij.execution.{ExecutionException, Executor}
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.console.configuration.ScalaConsoleConfigurationType
import org.jetbrains.plugins.scala.lang.scaladoc.generate.ScaladocAction.ScaladocRunConfiguration

import javax.swing.JComponent
import javax.swing.event.DocumentEvent

class ScaladocAction extends BaseAnalysisAction(ScalaBundle.message("generate.scaladoc"), ScalaBundle.message("scaladoc.noon")) {
  private var configurationDialog: ScaladocConsoleRunConfigurationForm = _

  locally {
    val presentation = getTemplatePresentation
    presentation.setText(ScalaBundle.message("generate.scaladoc.action.text"))
    presentation.setDescription(ScalaBundle.message("generate.scaladoc.action.description"))
  }

  private def disposeForm(): Unit = {
    configurationDialog = null
  }

  override def analyze(project: Project, scope: AnalysisScope): Unit = {
    var config: ScaladocConfiguration = null
    try {
      configurationDialog.saveSettings()
      config = new ScaladocConfiguration(configurationDialog, project, scope)
      try {
        val runConfig = new ScaladocRunConfiguration(project, configurationDialog, config)

        val runner: ProgramRunner[_ <: RunnerSettings] =
          ProgramRunner.getRunner(DefaultRunExecutor.EXECUTOR_ID, config)
        val builder: ExecutionEnvironmentBuilder =
          new ExecutionEnvironmentBuilder(project, DefaultRunExecutor.getRunExecutorInstance)
        builder.runProfile(config)
        builder.runnerAndSettings(runner,
          new RunnerAndConfigurationSettingsImpl(new RunManagerImpl(project), runConfig, false))
        runner.execute(builder.build())
      } catch {
        case e: ExecutionException => ExecutionErrorDialog.show(e, CommonBundle.getErrorTitle, project)
      }
    }
    finally {
      disposeForm()
    }
  }

  override def canceled(): Unit = {
    super.canceled()
    disposeForm()
  }

  override def getAdditionalActionSettings(project: Project, dialog: BaseAnalysisActionDialog): JComponent = {
    configurationDialog = new ScaladocConsoleRunConfigurationForm(project)
    configurationDialog.getOutputDirChooser.getDocument.addDocumentListener(new DocumentAdapter() {
      override def textChanged(e: DocumentEvent): Unit = {
        updateAvailability(dialog)
      }
    })
    updateAvailability(dialog)
    configurationDialog.createCenterPanel()
  }

  private def updateAvailability(dialog: BaseAnalysisActionDialog): Unit = {
    dialog.setOKActionEnabled(!configurationDialog.getOutputDir.isEmpty)
  }
}

object ScaladocAction {

  // just stub entities, will never be invoked
  object ScaladocRunConfigurationFactory extends ConfigurationFactory(new ScalaConsoleConfigurationType) {
    override def createTemplateConfiguration(project: Project): RunConfiguration = new ScaladocRunConfiguration(project, null, null)
    override def getId: String = "ScaladocRunConfigurationFactory"
  }

  class ScaladocRunConfiguration(project: Project,
                                 dialog: ScaladocConsoleRunConfigurationForm,
                                 config: ScaladocConfiguration)
    extends RunConfigurationBase[Unit](project, ScaladocRunConfigurationFactory, "Generate Scaladoc") {
    override def checkConfiguration(): Unit = {}

    override def getConfigurationEditor: SettingsEditor[_ <: ScaladocRunConfiguration] = new SettingsEditor[ScaladocRunConfiguration]() {
      override def createEditor(): JComponent = dialog.createCenterPanel()

      override def resetEditorFrom(s: ScaladocRunConfiguration): Unit = {}

      override def applyEditorTo(s: ScaladocRunConfiguration): Unit = {}
    }

    override def getState(executor: Executor, env: ExecutionEnvironment): RunProfileState = config.getState(executor, env)
  }
}