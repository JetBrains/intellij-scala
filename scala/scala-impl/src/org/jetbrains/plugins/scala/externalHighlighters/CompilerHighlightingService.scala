package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.{Service, ServiceManager}
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.psi.PsiFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.externalHighlighters.compiler.{DocumentCompiler, IncrementalCompiler, WorksheetCompiler}
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.util.RescheduledExecutor

import scala.annotation.nowarn
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import scala.util.Try

@Service
final class CompilerHighlightingService(project: Project)
  extends Disposable {

  private val incrementalExecutor = new RescheduledExecutor("IncrementalCompilerHighlighting", this)
  private val documentExecutor = new RescheduledExecutor("DocumentCompilerHighlighting", this)
  private val worksheetExecutor = new RescheduledExecutor("WorksheetCompilerHighlighting", this)
  private val showIndicatorExecutor = new RescheduledExecutor("CompilerHighlightingIndicator", this)

  private val modTracker = new SimpleModificationTracker

  @volatile private var progressIndicator: Option[ProgressIndicator] = None

  def triggerIncrementalCompilation(delayedProgressShow: Boolean = true,
                                    beforeCompilation: () => Unit = () => (),
                                    afterCompilation: () => Unit = () => ()): Unit =
    scheduleCompilation(incrementalExecutor, delayedProgressShow) {
      beforeCompilation()
      try {
        IncrementalCompiler.compile(project, _)
      } finally {
        afterCompilation()
      }
    }

  def triggerDocumentCompilation(document: Document,
                                 afterCompilation: () => Unit = () => ()): Unit =
    scheduleCompilation(documentExecutor, delayedProgressShow = true) {
      try {
        DocumentCompiler.compile(project, document, _)
      } finally {
        afterCompilation()
      }
    }

  def triggerWorksheetCompilation(psiFile: PsiFile,
                                  document: Document,
                                  afterCompilation: () => Unit = () => ()): Unit =
    scheduleCompilation(worksheetExecutor, delayedProgressShow = true) {
      try {
        WorksheetCompiler.compile(psiFile, document, _)
      } finally {
        afterCompilation()
      }
    }

  def cancel(): Unit =
    progressIndicator.foreach(_.cancel())

  def isCompiling: Boolean =
    progressIndicator.isDefined

  override def dispose(): Unit =
    progressIndicator = None

  // SCL-17295
  @nowarn("msg=pure expression")
  @Cached(modTracker, null)
  private def saveProjectOnce(): Unit =
    if (!project.isDisposed || project.isDefault) project.save()

  private def scheduleCompilation(executor: RescheduledExecutor, delayedProgressShow: Boolean)
                                 (compile: Client => Unit): Unit =
    executor.schedule(ScalaHighlightingMode.compilationDelay) {
      performCompilation(delayedProgressShow)(compile)
    }

  private def performCompilation(delayedProgressShow: Boolean)
                                (compile: Client => Unit): Unit = {
    saveProjectOnce()
    CompileServerLauncher.ensureServerRunning(project)
    val promise = Promise[Unit]

    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task: Task.Backgroundable = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        progressIndicator = Some(indicator)
        val client = new CompilerEventGeneratingClient(project, indicator)
        val result = Try(compile(client))
        progressIndicator = None
        showIndicatorExecutor.cancel()
        promise.complete(result)
      }
    }

    val indicator = new DeferredShowProgressIndicator(task)
    ProgressManager.getInstance.runProcessWithProgressAsynchronously(task, indicator)
    val showProgressDelay = if (delayedProgressShow)
      ScalaHighlightingMode.compilationTimeoutToShowProgress
    else
      Duration.Zero
    showIndicatorExecutor.schedule(showProgressDelay)(indicator.show())

    Await.result(promise.future, Duration.Inf)
  }

  private class DeferredShowProgressIndicator(task: Task.Backgroundable)
    extends ProgressIndicatorBase {

    setOwnerTask(task)

    /**
     * Shows the progress in the Status bar.
     * This method partially duplicates constructor of the
     * [[com.intellij.openapi.progress.impl.BackgroundableProcessIndicator]].
     */
    def show(): Unit =
      if (!project.isDisposed && !project.isDefault && !ApplicationManager.getApplication.isUnitTestMode)
        for {
          frameHelper <- WindowManagerEx.getInstanceEx.findFrameHelper(project).toOption
          statusBar <- frameHelper.getStatusBar.toOption
          statusBarEx <- statusBar.asOptionOf[StatusBarEx]
        } UIUtil.invokeLaterIfNeeded(() => statusBarEx.addProgress(this, task))
  }
}

object CompilerHighlightingService {

  def get(project: Project): CompilerHighlightingService =
    ServiceManager.getService(project, classOf[CompilerHighlightingService])
}
