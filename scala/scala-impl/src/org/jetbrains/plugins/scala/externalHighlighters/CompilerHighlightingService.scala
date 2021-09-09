package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{Document, EditorFactory}
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.util.ui.UIUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.externalHighlighters.CompilerHighlightingService.{Log, platformAutomakeEnabled}
import org.jetbrains.plugins.scala.externalHighlighters.compiler._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.util.RescheduledExecutor

import scala.annotation.nowarn
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import scala.util.Try

@Service
final class CompilerHighlightingService(project: Project)
  extends Disposable {

  private val incrementalExecutor = new RescheduledExecutor("IncrementalCompilerHighlighting", this)
  private val documentExecutor = new RescheduledExecutor("DocumentCompilerHighlighting", this)
  // TODO: unify/merge worksheet highlighting implementation with  DocumentCompiler and documentExecutor
  //  they basically use the same idea: use temp file to highlight
  private val worksheetExecutor = new RescheduledExecutor("WorksheetCompilerHighlighting", this)
  private val showIndicatorExecutor = new RescheduledExecutor("CompilerHighlightingIndicator", this)

  @volatile private var progressIndicator: Option[ProgressIndicator] = None

  private def debug(message: => String): Unit = {
    if (Log.isDebugEnabled) {
      Log.debug(s"[${project.getName}] ${message}")
    }
  }

  def triggerIncrementalCompilation(
    debugReason: String,
    delayedProgressShow: Boolean = true
  ): Unit = {
    debug(s"triggerIncrementalCompilation: $debugReason")
    if (platformAutomakeEnabled(project)) {
      //we need to save documents right away or automake won't happen
      TriggerCompilerHighlightingService.get(project).beforeIncrementalCompilation()

      BuildManager.getInstance().scheduleAutoMake()
      //afterIncrementalCompilation is invoked in AutomakeBuildManagerListener
    }
    else {
      incrementalExecutor.schedule(ScalaHighlightingMode.compilationDelay) {
        performCompilation(delayedProgressShow) { client =>
          TriggerCompilerHighlightingService.get(project).beforeIncrementalCompilation()
          try {
            IncrementalCompiler.compile(project, client)
          } finally {
            TriggerCompilerHighlightingService.get(project).afterIncrementalCompilation()
          }
        }
      }
    }
  }

  def triggerDocumentCompilation(
    debugReason: String,
    document: Document,
    afterCompilation: () => Unit = () => ()
  ): Unit = {
    debug(s"triggerDocumentCompilation: $debugReason")
    scheduleDocumentCompilation(documentExecutor, document) { client =>
      try {
        DocumentCompiler.get(project).compile(document, client)
      } finally {
        afterCompilation()
      }
    }
  }

  def triggerWorksheetCompilation(psiFile: ScalaFile,
                                  document: Document,
                                  afterCompilation: () => Unit = () => ()): Unit =
    scheduleDocumentCompilation(worksheetExecutor, document) { client =>
      try {
        WorksheetHighlightingCompiler.compile(psiFile, document, client)
      } finally {
        afterCompilation()
      }
    }

  def cancel(): Unit = {
    debug("cancel")
    progressIndicator.foreach(_.cancel())
  }

  def isCompiling: Boolean =
    progressIndicator.isDefined

  override def dispose(): Unit = {
    debug("dispose")
    progressIndicator = None
  }

  // SCL-17295
  @nowarn("msg=pure expression")
  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private def saveProjectOnce(): Unit =
    if (!project.isDisposed || project.isDefault) project.save()

  private def scheduleDocumentCompilation(executor: RescheduledExecutor, document: Document)
                                         (compile: Client => Unit): Unit = {
    val action = new RescheduledExecutor.Action {
      override def perform(): Unit =
        performCompilation(delayedProgressShow = true)(compile)

      override def condition: Boolean =
        EditorFactory.getInstance().editors(document, project).anyMatch { editor =>
          LookupManager.getActiveLookup(editor) == null &&
            TemplateManager.getInstance(project).getActiveTemplate(editor) == null
        }
    }
    executor.schedule(ScalaHighlightingMode.compilationDelay, action)
  }

  private def performCompilation(delayedProgressShow: Boolean)
                                (compile: Client => Unit): Unit = {
    saveProjectOnce()
    CompileServerLauncher.ensureServerRunning(project)
    val promise = Promise[Unit]()

    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task: Task.Backgroundable = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        progressIndicator = Some(indicator)
        val client = new CompilerEventGeneratingClient(project, indicator, Log)
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

  private val Log = Logger.getInstance(classOf[CompilerHighlightingService])

  def get(project: Project): CompilerHighlightingService =
    project.getService(classOf[CompilerHighlightingService])

  def platformAutomakeEnabled(project: Project): Boolean =
    CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE
}
