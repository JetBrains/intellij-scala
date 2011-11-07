package org.jetbrains.plugins.scala.lang.scaladoc.generate

import com.intellij.openapi.project.Project
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.util.JDOMExternalizable
import com.intellij.execution.runners.{ExecutionEnvironment, ProgramRunner}
import com.intellij.execution.{ExecutionException, RunnerRegistry}
import com.intellij.execution.util.ExecutionErrorDialog
import com.intellij.CommonBundle
import javax.swing.JComponent
import com.intellij.analysis.{BaseAnalysisActionDialog, AnalysisScope, BaseAnalysisAction}
import com.intellij.ui.DocumentAdapter
import javax.swing.event.DocumentEvent


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
    var myConfig: ScaladocConfiguration = null
    try {
      configurationDialog.saveSettings();
      myConfig = new ScaladocConfiguration(configurationDialog, project, scope)
      try {
        val runner: ProgramRunner[_ <: JDOMExternalizable] =
          RunnerRegistry.getInstance.getRunner(DefaultRunExecutor.EXECUTOR_ID, myConfig)
          runner.execute(DefaultRunExecutor.getRunExecutorInstance,
            new ExecutionEnvironment(myConfig, project, null, null, null))
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