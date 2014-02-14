package org.jetbrains.plugins.scala
package worksheet.actions

import com.intellij.openapi.actionSystem.{AnActionEvent, AnAction}
import lang.psi.api.ScalaFile
import com.intellij.execution._
import com.intellij.execution.configurations.{RunProfileState, ConfigurationTypeUtil}
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.runners.{ExecutionEnvironmentBuilder, ExecutionEnvironment}
import com.intellij.openapi.ui.Messages
import com.intellij.util.ActionRunner
import org.jetbrains.plugins.scala.worksheet.runconfiguration.{WorksheetViewerInfo, WorksheetRunConfigurationFactory, WorksheetRunConfiguration, WorksheetConfigurationType}
import com.intellij.icons.AllIcons
import com.intellij.openapi.keymap.{KeymapUtil, KeymapManager}
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import com.intellij.openapi.application.{ModalityState, ApplicationManager}
import org.jetbrains.plugins.scala

/**
 * @author Ksenia.Sautina
 * @author Dmitry Naydanov        
 * @since 10/17/12
 */

class RunWorksheetAction extends AnAction {
  def actionPerformed(e: AnActionEvent) {
    val editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
    if (editor == null) return
    val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)
    psiFile match {
      case file: ScalaFile if file.isWorksheetFile =>
        val viewer = WorksheetViewerInfo getViewer editor
        val project = e.getProject

        if (viewer != null) {
          ApplicationManager.getApplication.invokeAndWait(new Runnable {
            override def run() {
              scala.extensions.inWriteAction {
                CleanWorksheetAction.cleanWorksheet(file.getNode, editor, viewer, project)
              }
            }
          }, ModalityState.any())
        }


        new WorksheetCompiler().compileAndRun(editor, file, (className: String, addToCp: String) => {
          ApplicationManager.getApplication invokeLater new Runnable {
            override def run() {
              executeWorksheet(file.getName, project, file.getContainingFile.getVirtualFile, className, addToCp)
            }
          }
        }, Option(editor))
      case _ =>
    }
  }

  def executeWorksheet(name: String, project: Project, virtualFile: VirtualFile, mainClassName: String, addToCp: String): Boolean = {
    val runManagerEx = RunManagerEx.getInstanceEx(project)
    val configurationType = ConfigurationTypeUtil.findConfigurationType(classOf[WorksheetConfigurationType])
    val settings = runManagerEx.getConfigurationSettings(configurationType)

    def execute(setting: RunnerAndConfigurationSettings) {
      val configuration = setting.getConfiguration.asInstanceOf[WorksheetRunConfiguration]
      configuration.worksheetField = virtualFile.getCanonicalPath
      configuration.classToRunField = mainClassName
      configuration.addCpField = addToCp
      configuration.setName("WS: " + name)
      runManagerEx.setSelectedConfiguration(setting)
      val runExecutor = DefaultRunExecutor.getRunExecutorInstance
      val runner: DefaultJavaProgramRunner = new DefaultJavaProgramRunner {
        override protected def doExecute(project: Project, state: RunProfileState,
                               contentToReuse: RunContentDescriptor, env: ExecutionEnvironment): RunContentDescriptor = {
          val descriptor = super.doExecute(project, state, contentToReuse, env)
          descriptor.setActivateToolWindowWhenAdded(false)
          descriptor
        }
      }
      
      if (runner != null) {
        try {
          val builder: ExecutionEnvironmentBuilder = new ExecutionEnvironmentBuilder(project, runExecutor)
          builder.setRunnerAndSettings(runner, setting)
          runner.execute(builder.build())
        }
        catch {
          case e: ExecutionException =>
            Messages.showErrorDialog(project, e.getMessage, ExecutionBundle.message("error.common.title"))
        }
      }
    }
    for (setting <- settings) {
      ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
        def run() {
          execute(setting)
        }
      })
      return true
    }
    ActionRunner.runInsideReadAction(new ActionRunner.InterruptibleRunnable {
      def run() {
        val factory: WorksheetRunConfigurationFactory =
          configurationType.getConfigurationFactories.apply(0).asInstanceOf[WorksheetRunConfigurationFactory]
        val setting = RunManagerEx.getInstanceEx(project).createConfiguration(name, factory)

        runManagerEx.setTemporaryConfiguration(setting)
        execute(setting)
      }
    })
    false
  }

  override def update(e: AnActionEvent) {
    val presentation = e.getPresentation
    presentation.setIcon(AllIcons.Actions.Execute)
    val shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts("Scala.RunWorksheet")
    if (shortcuts.length > 0) {
      val shortcutText = " (" + KeymapUtil.getShortcutText(shortcuts(0)) + ")"
      presentation.setText(ScalaBundle.message("worksheet.execute.button") + shortcutText)
    }

    def enable() {
      presentation.setEnabled(true)
      presentation.setVisible(true)
    }
    def disable() {
      presentation.setEnabled(false)
      presentation.setVisible(false)
    }

    try {
      val editor = FileEditorManager.getInstance(e.getProject).getSelectedTextEditor
      val psiFile: PsiFile = PsiDocumentManager.getInstance(e.getProject).getPsiFile(editor.getDocument)

      psiFile match {
        case sf: ScalaFile if sf.isWorksheetFile => enable()
        case _ =>  disable()
      }
    } catch {
      case e: Exception => disable()
    }
  }
  
  
}