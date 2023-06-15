package org.jetbrains.plugins.scala.worksheet.actions.topmenu

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent}
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.keymap.{KeymapManager, KeymapUtil}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.{DumbService, IndexNotReadyException, Project}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.task.{ProjectTaskContext, ProjectTaskManager}
import org.jetbrains.annotations.{NonNls, TestOnly}
import org.jetbrains.plugins.scala.extensions.{LoggerExt, inWriteAction, invokeAndWait, invokeLater}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector
import org.jetbrains.plugins.scala.worksheet.actions.WorksheetFileHook
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompiler.WorksheetCompilerResult.WorksheetCompilerError
import org.jetbrains.plugins.scala.worksheet.processor.{WorksheetCompiler, WorksheetEvaluationErrorReporter}
import org.jetbrains.plugins.scala.worksheet.runconfiguration.WorksheetCache
import org.jetbrains.plugins.scala.worksheet.settings.WorksheetFileSettings
import org.jetbrains.plugins.scala.worksheet.{WorksheetBundle, WorksheetFile}

import javax.swing.Icon
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class RunWorksheetAction extends AnAction(
  WorksheetBundle.message("run.scala.worksheet.action.text"),
  WorksheetBundle.message("run.scala.worksheet.action.description"),
  AllIcons.Actions.Execute
) with TopComponentAction {

  override def genericText: String = WorksheetBundle.message("worksheet.execute.button")

  override def actionIcon: Icon = AllIcons.Actions.Execute

  override def shortcutId: Option[String] = Some(RunWorksheetAction.ShortcutId)

  override def actionPerformed(e: AnActionEvent): Unit = {
    for { (editor, psiFile) <- getCurrentScalaWorksheetEditorAndFile(e) } {
      RunWorksheetAction.runCompilerForEditor(editor, psiFile, auto = false)
    }
  }

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
    final case object Done extends RunWorksheetActionResult

    sealed trait Error extends RunWorksheetActionResult
    final case object AlreadyRunning extends Error
    final case class ProjectCompilationError(aborted: Boolean, errors: Boolean, context: ProjectTaskContext) extends Error
    final case class WorksheetRunError(error: WorksheetCompilerError) extends Error

    final case object NoModuleError extends Error
    final case object NoWorksheetFileError extends Error
    final case object NoWorksheetEditorError extends Error
    final case class IndexNotReady(ex: IndexNotReadyException) extends Error

    object IndexNotReady {
      def apply(): IndexNotReady = IndexNotReady(IndexNotReadyException.create())
    }
  }

  private final class RunImmediatelyExecutionContext extends ExecutionContext {
    override def execute(runnable: Runnable): Unit =
      runnable.run()
    override def reportFailure(cause: Throwable): Unit =
      Log.error(s"Fatal error occurred during execution in ${this.getClass.getSimpleName} ", cause)
  }

  def runCompilerForEditor(editor: Editor, psiFile: WorksheetFile, auto: Boolean): Future[RunWorksheetActionResult] = {
    // SCL-16786: do not allow to run worksheet in dumb mode
    // - it is required during resolve in WorksheetSourceProcessor.processDefault
    // - run could be triggered automatically in "Incremental mode" bypassing AnAction
    // - also in theory preprocess could be delayed when "Build project before run" setting is enabled
    val project = psiFile.getProject
    val future = if (DumbService.getInstance(project).isDumb)
       Future.successful(RunWorksheetActionResult.IndexNotReady())
    else {
      ScalaActionUsagesCollector.logRunWorksheet(project)
      runCompiler(editor, psiFile, auto)
    }

    future.onComplete {
      case Success(error: RunWorksheetActionResult.Error) => reportError(project, error)
      case Success(_)                                     =>
      case Failure(exception)                             => Log.error("Error occurred during worksheet evaluation", exception)
    }(new RunImmediatelyExecutionContext)
    future
  }

  private def reportError(project: Project, error: RunWorksheetActionResult.Error): Unit = {
    import RunWorksheetActionResult._
    error match {
      case NoModuleError                    => WorksheetEvaluationErrorReporter.showConfigErrorNotification(project, WorksheetBundle.message("worksheet.config.error.no.module.classpath.specified"))
      case AlreadyRunning                   => // skip, there already exists icon representation that WS is running
      case ProjectCompilationError(_, _, _) => // skip, already reported in Build tool window
      case WorksheetRunError(_)             => // skip, already reported in WorksheetEvaluationErrorReporter
      case NoWorksheetFileError             => // skip for now
      case NoWorksheetEditorError           => // skip for now
      case IndexNotReady(_)                 => // skip for now
    }
  }

  @TestOnly
  // should be private, but is used in tests
  def runCompiler(editor: Editor, psiFile: WorksheetFile, auto: Boolean): Future[RunWorksheetActionResult] = {
    val start = System.currentTimeMillis()
    Log.debugSafe(s"worksheet evaluation started")
    val promise = Promise[RunWorksheetActionResult]()
    try {
      doRunCompiler(editor, psiFile, auto)(promise)
    } catch {
      case NonFatal(ex) =>
        promise.failure(ex)
    }
    val future = promise.future
    future.onComplete(s => {
      val end = System.currentTimeMillis()
      Log.debugSafe(s"worksheet evaluation result (took ${end - start}ms): " + s.toString)
    })(new RunImmediatelyExecutionContext)
    future
  }

  private def doRunCompiler(editor: Editor, psiFile: WorksheetFile, auto: Boolean)
                           (promise: Promise[RunWorksheetActionResult]): Unit = {
    val module = psiFile.module
    module match {
      case Some(module) =>
        val fileSettings = WorksheetFileSettings(psiFile)
        doRunCompiler(module.getProject, editor, auto, psiFile.getVirtualFile, psiFile, fileSettings.isMakeBeforeRun, module)(promise)
      case None        =>
        promise.success(RunWorksheetActionResult.NoModuleError)
    }
  }

  private def doRunCompiler(
    project: Project,
    editor: Editor,
    auto: Boolean,
    vFile: VirtualFile,
    file: ScalaFile,
    makeBeforeRun: Boolean,
    module: Module
  )(
    promise: Promise[RunWorksheetActionResult]
  ): Unit = {
    Log.debugSafe(s"worksheet file: ${vFile.getPath}")

    val viewer = WorksheetCache.getInstance(project).getViewer(editor)

    if (viewer != null && !WorksheetFileSettings(file).isRepl) {
      invokeAndWait(ModalityState.any()) {
        inWriteAction {
          CleanWorksheetAction.resetScrollModel(viewer)
          if (!auto) {
            CleanWorksheetAction.cleanWorksheet(file.getVirtualFile, viewer, project)
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
          case error: WorksheetCompilerError =>
            val reporter = new WorksheetEvaluationErrorReporter(project, vFile, editor, Log)
            reporter.reportError(error)
            RunWorksheetActionResult.WorksheetRunError(error)
          case _ =>
            RunWorksheetActionResult.Done
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