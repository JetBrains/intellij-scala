package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskManager
import javax.swing.Icon
import org.jetbrains.annotations.NonNls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{LoggerExt, inWriteAction, invokeAndWait, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.WorksheetCompilerError
import org.jetbrains.plugins.scala.worksheet.processor.{WorksheetCompiler, WorksheetCompilerErrorReporter}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal

class RunWorksheetAction extends AnAction(
  ScalaBundle.message("run.scala.worksheet.action.text"),
  ScalaBundle.message("run.scala.worksheet.action.description"),
  AllIcons.Actions.Execute
) with TopComponentAction {

  override def genericText: String = ScalaBundle.message("worksheet.execute.button")

  override def actionIcon: Icon = AllIcons.Actions.Execute

  override def shortcutId: Option[String] = Some(RunWorksheetAction.ShortcutId)

  override def actionPerformed(e: AnActionEvent): Unit =
    RunWorksheetAction.runCompilerForSelectedEditor(e, auto = false)

  override def update(e: AnActionEvent): Unit = {
    super.update(e)

    val shortcuts = KeymapManager.getInstance.getActiveKeymap.getShortcuts(RunWorksheetAction.ShortcutId)

    if (shortcuts.nonEmpty) {
      val shortcutText = " (" + KeymapUtil.getShortcutText(shortcuts(0)) + ")"
      //noinspection ReferencePassedToNls
      e.getPresentation.setText(genericText + shortcutText)
    }
  }
}

object RunWorksheetAction {

  private val Log: Logger = Logger.getInstance(getClass)
  @NonNls
  private val ShortcutId = "Scala.RunWorksheet"

  sealed trait RunWorksheetActionResult
  object RunWorksheetActionResult {
    case object Done extends RunWorksheetActionResult
    sealed trait Error extends RunWorksheetActionResult
    case object NoModuleError extends Error
    case object NoWorksheetFileError extends Error
    case object AlreadyRunning extends Error
    final case class ProjectCompilationError(aborted: Boolean, errors: Boolean, context: ProjectTaskContext) extends Error
    final case class WorksheetRunError(error: WorksheetCompilerError) extends Error
  }

  def runCompilerForSelectedEditor(e: AnActionEvent, auto: Boolean): Unit = {
    val project = e.getProject match {
      case null    => return
      case project => project
    }
    runCompilerForSelectedEditor(project, auto)
  }

  def runCompilerForSelectedEditor(project: Project, auto: Boolean): Unit = {
    if (DumbService.getInstance(project).isDumb) return

    Stats.trigger(FeatureKey.runWorksheet)

    val editor = FileEditorManager.getInstance(project).getSelectedTextEditor
    if (editor == null) return

    runCompiler(project, editor, auto)
  }

  def runCompiler(project: Project, editor: Editor, auto: Boolean): Future[RunWorksheetActionResult] = {
    val start = System.currentTimeMillis()
    Log.debugSafe(s"worksheet evaluation started")
    val promise = Promise[RunWorksheetActionResult]()
    try {
      doRunCompiler(project, editor, auto)(promise)
    } catch {
      case NonFatal(ex) =>
        promise.failure(ex)
    }
    val runImmediatelyExecutionContext = new ExecutionContext {
      override def execute(runnable: Runnable): Unit = runnable.run()

      override def reportFailure(cause: Throwable): Unit = Log.error(cause)
    }
    val future = promise.future
    future.onComplete(s => {
      val end = System.currentTimeMillis()
      Log.debugSafe(s"worksheet evaluation result (took ${end - start}ms): " + s.toString)
    })(runImmediatelyExecutionContext)
    future
  }

  private def doRunCompiler(project: Project, editor: Editor, auto: Boolean)
                           (promise: Promise[RunWorksheetActionResult]): Unit = {
    val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    val file: ScalaFile = psiFile match {
      case file: ScalaFile if file.isWorksheetFile => file
      case _ =>
        promise.success(RunWorksheetActionResult.NoWorksheetFileError)
        return
    }

    val fileSettings = WorksheetFileSettings(file)

    val module: Module = fileSettings.getModuleFor match {
      case m: Module => m
      case _ =>
        promise.success(RunWorksheetActionResult.NoModuleError)
        return
    }

    doRunCompiler(project, editor, auto, psiFile.getVirtualFile, file, fileSettings.isMakeBeforeRun, module)(promise)
  }

  private def doRunCompiler(project: Project, editor: Editor, auto: Boolean,
                            vFile: VirtualFile, file: ScalaFile, makeBeforeRun: Boolean, module: Module)
                           (promise: Promise[RunWorksheetActionResult]): Unit = {
    Log.debugSafe(s"worksheet file: ${vFile.getPath}")

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

    invokeAndWait {
      if (WorksheetFileHook.isRunning(vFile)) {
        promise.success(RunWorksheetActionResult.AlreadyRunning)
        return
      } else {
        WorksheetFileHook.disableRun(vFile, None)
      }
    }

    def runnable(): Unit = {
      val compiler = new WorksheetCompiler(module, file)
      compiler.compileAndRun(auto, editor) { result =>
        val resultTransformed = result match {
          case WorksheetCompilerResult.CompiledAndEvaluated =>
            RunWorksheetActionResult.Done
          case error: WorksheetCompilerError =>
            val reporter = new WorksheetCompilerErrorReporter(project, vFile, editor, Log)
            reporter.reportError(error)
            RunWorksheetActionResult.WorksheetRunError(error)
        }
        promise.success(resultTransformed)

        val hasErrors = resultTransformed != RunWorksheetActionResult.Done
        invokeLater {
          WorksheetFileHook.enableRun(vFile, hasErrors)
        }
      }
    }

    if (makeBeforeRun) {
      ProjectTaskManager.getInstance(project)
        .build(module)
        .`then`[Unit] { result: ProjectTaskManager.Result =>
          if (result.hasErrors || result.isAborted) {
            promise.success(RunWorksheetActionResult.ProjectCompilationError(result.isAborted, result.hasErrors, result.getContext))
            invokeLater {
              WorksheetFileHook.enableRun(vFile, result.hasErrors)
            }
          } else {
            runnable()
          }
        }
    } else {
      runnable()
    }
  }
}