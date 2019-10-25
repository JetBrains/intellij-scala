package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import javax.swing.Icon
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{inWriteAction, invokeAndWait}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.WorksheetEvaluationError
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.server.WorksheetProcessManager
import org.jetbrains.plugins.scala.worksheet.settings.{WorksheetCommonSettings, WorksheetFileSettings}

import scala.concurrent.{ExecutionContext, Future, Promise}

class RunWorksheetAction extends AnAction with TopComponentAction {

  override def genericText: String = ScalaBundle.message("worksheet.execute.button")

  override def actionIcon: Icon = AllIcons.Actions.Execute

  override def shortcutId: Option[String] = Some("Scala.RunWorksheet")

  override def actionPerformed(e: AnActionEvent): Unit =
    RunWorksheetAction.runCompiler(e.getProject, auto = false)

  override def update(e: AnActionEvent): Unit = {
    super.update(e)

    val shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts("Scala.RunWorksheet")

    if (shortcuts.nonEmpty) {
      val shortcutText = s" (${KeymapUtil.getShortcutText(shortcuts(0))})"
      e.getPresentation.setText(ScalaBundle.message("worksheet.execute.button") + shortcutText)
    }
  }
}

object RunWorksheetAction {

  sealed trait RunWorksheetActionResult
  object RunWorksheetActionResult {
    object Done extends RunWorksheetActionResult
    sealed trait Error extends RunWorksheetActionResult
    object NoModuleError extends Error
    object NoWorksheetFileError extends Error
    final case class ProjectCompilationError(aborted: Boolean, errors: Int, warnings: Int, context: CompileContext) extends Error
    final case class WorksheetCompilerError(error: WorksheetEvaluationError) extends Error
  }

  def runCompiler(project: Project, auto: Boolean): Unit = {
    Stats.trigger(FeatureKey.runWorksheet)

    if (project == null) return

    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor

    if (editor == null) return

    runCompiler(project, editor, auto)
  }

  def runCompiler(@NotNull project: Project, @NotNull editor: Editor, auto: Boolean): Future[RunWorksheetActionResult] = {
    val promise = Promise[RunWorksheetActionResult]()
    try {
      doRunCompiler(project,editor, auto)(promise)
    } catch {
      case ex: Exception =>
        promise.failure(ex)
    }
    promise.future
  }

  private def doRunCompiler(@NotNull project: Project, @NotNull editor: Editor, auto: Boolean)
                           (promise: Promise[RunWorksheetActionResult]): Unit = {

    val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    WorksheetProcessManager.stop(psiFile.getVirtualFile)

    val file: ScalaFile = psiFile match {
      case file: ScalaFile if file.isWorksheetFile => file
      case _ =>
        promise.success(RunWorksheetActionResult.NoWorksheetFileError)
        return
    }

    val fileSettings = WorksheetCommonSettings(file)
    implicit val module: Module = fileSettings.getModuleFor
    if (module == null) {
      promise.success(RunWorksheetActionResult.NoModuleError)
      return
    }

    val viewer = WorksheetCache.getInstance(project).getViewer(editor)

    if (viewer != null && !WorksheetFileSettings.isRepl(file)) {
      invokeAndWait(ModalityState.any()) {
        inWriteAction {
          CleanWorksheetAction.resetScrollModel(viewer)
          if (!auto) {
            CleanWorksheetAction.cleanWorksheet(file.getNode, editor, viewer, project)
          }
        }
      }
    }

    def runnable(): Unit = {
      val callback: WorksheetCompilerResult => Unit = {
        case WorksheetCompilerResult.Done    => promise.success(RunWorksheetActionResult.Done)
        case error: WorksheetEvaluationError => promise.success(RunWorksheetActionResult.WorksheetCompilerError(error))
      }
      val compiler = new WorksheetCompiler(module, editor, file, callback, auto)
      compiler.compileAndRunFile()
    }

    if (fileSettings.isMakeBeforeRun) {
      val compilerNotification: CompileStatusNotification =
        (aborted: Boolean, errors: Int, warnings: Int, context: CompileContext) => {
          if (!aborted && errors == 0) {
            runnable()
          } else {
            promise.success(RunWorksheetActionResult.ProjectCompilationError(aborted, errors, warnings, context))
          }
        }
      CompilerManager.getInstance(project).make(module, compilerNotification)
    } else {
      runnable()
    }
  }
}