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
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalHighlighters.compiler._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import java.nio.file.Path
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.nowarn
import scala.collection.concurrent.TrieMap
import scala.collection.immutable.SortedSet
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Try

@Service
private final class CompilerHighlightingService(project: Project) extends Disposable {

  import CompilerHighlightingService._

  private val executor: ScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService(getClass.getSimpleName, 1)

  private val indicatorExecutor: ScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService("CompilerHighlightingIndicator", 1)

  private val scheduledRequests: TrieMap[Path, ScheduledCompilationRequests] = TrieMap.empty

  private val progressIndicator: AtomicReference[ProgressIndicator] = new AtomicReference()

  private def debug(message: => String): Unit = {
    if (Log.isDebugEnabled) {
      Log.debug(s"[${project.getName}] $message")
    }
  }

  def cancel(): Unit = {
    debug("cancel")
    val indicator = progressIndicator.get()
    if (indicator ne null) {
      indicator.cancel()
    }
  }

  def isCompiling: Boolean = {
    progressIndicator.get() ne null
  }

  override def dispose(): Unit = {
    debug("dispose")
    executor.shutdown()
    indicatorExecutor.shutdown()
    scheduledRequests.clear()
    val indicator = progressIndicator.getAndSet(null)
    if (indicator ne null) {
      indicator.cancel()
    }
  }

  def triggerIncrementalCompilation(
    path: Path,
    module: Module,
    sourceScope: SourceScope,
    debugReason: String
  ): Unit = {
    if (platformAutomakeEnabled(project)) {
      //we need to save documents right away or automake won't happen
      TriggerCompilerHighlightingService.get(project).beforeIncrementalCompilation()
      BuildManager.getInstance().scheduleAutoMake()
    } else {
      schedule(path, CompilationRequest.IncrementalRequest(module, sourceScope, debugReason))
    }
  }

  def triggerDocumentCompilation(
    path: Path,
    document: Document,
    debugReason: String
  ): Unit =
    schedule(path, CompilationRequest.DocumentRequest(document, debugReason))

  def triggerWorksheetCompilation(path: Path, psiFile: ScalaFile, document: Document, debugReason: String): Unit =
    schedule(path, CompilationRequest.WorksheetRequest(psiFile, document, debugReason))

  private def schedule(path: Path, request: CompilationRequest): Unit = {
    val now = System.nanoTime()
    scheduledRequests.updateWith(path) {
      case Some(ScheduledCompilationRequests(_, reqs)) =>
        val newReqs = reqs.filter(_.priority < request.priority) + request
        Some(ScheduledCompilationRequests(now, newReqs))
      case None =>
        val reqs = SortedSet(request)
        Some(ScheduledCompilationRequests(now, reqs))
    }

    val Duration(length, unit) = ScalaHighlightingMode.compilationDelay
    executor.schedule(new DebouncedTask(path), length, unit)
  }

  private def execute(path: Path, request: CompilationRequest): Unit = request match {
    case wr: CompilationRequest.WorksheetRequest =>
      executeWorksheetCompilationRequest(wr)
    case ir: CompilationRequest.IncrementalRequest =>
      executeIncrementalCompilationRequest(path, ir)
    case dr: CompilationRequest.DocumentRequest =>
      executeDocumentCompilationRequest(dr)
  }

  private def executeWorksheetCompilationRequest(request: CompilationRequest.WorksheetRequest): Unit = {
    val CompilationRequest.WorksheetRequest(file, document, debugReason) = request
    debug(s"worksheetCompilation: $debugReason")
    performCompilation(WorksheetHighlightingCompiler.compile(file, document, _))
  }

  private def executeIncrementalCompilationRequest(path: Path, request: CompilationRequest.IncrementalRequest): Unit = {
    val CompilationRequest.IncrementalRequest(module, sourceScope, debugReason) = request
    debug(s"incrementalCompilation: $debugReason")
    performCompilation { client =>
      val triggerService = TriggerCompilerHighlightingService.get(project)
      triggerService.beforeIncrementalCompilation()
      try IncrementalCompiler.compile(project, module, sourceScope, client)
      finally {
        triggerService.enableDocumentCompiler(path)
      }
    }
  }

  private def executeDocumentCompilationRequest(request: CompilationRequest.DocumentRequest): Unit = {
    val CompilationRequest.DocumentRequest(document, debugReason) = request
    debug(s"documentCompilation: $debugReason")
    performCompilation(DocumentCompiler.get(project).compile(document, _))
  }

  private def performCompilation(compile: Client => Unit): Unit = {
    saveProjectOnce()
    CompileServerLauncher.ensureServerRunning(project)
    val promise = Promise[Unit]()

    val taskMsg = ScalaBundle.message("highlighting.compilation")
    val task = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = CompilerLock.get(project).withLock {
        progressIndicator.set(indicator)
        val client = new CompilerEventGeneratingClient(project, indicator, Log)
        val result = Try(compile(client))
        progressIndicator.set(null)
        promise.complete(result)
      }
    }

    val indicator = new DeferredShowProgressIndicator(task)
    ProgressManager.getInstance.runProcessWithProgressAsynchronously(task, indicator)
    val Duration(length, unit) = 0.seconds
    val runnable: Runnable = () => indicator.show()
    indicatorExecutor.schedule(runnable, length, unit)

    Await.result(promise.future, Duration.Inf)
  }

  // SCL-17295
  @nowarn("msg=pure expression")
  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private def saveProjectOnce(): Unit =
    if (!project.isDisposed || project.isDefault) project.save()

  private def isReadyForExecution(request: CompilationRequest): Boolean = request match {
    case CompilationRequest.WorksheetRequest(_, document, _) => isDocumentReadyForCompilation(document)
    case CompilationRequest.IncrementalRequest(_, _, _) => true
    case CompilationRequest.DocumentRequest(document, _) => isDocumentReadyForCompilation(document)
  }

  private def isDocumentReadyForCompilation(document: Document): Boolean =
    EditorFactory.getInstance().editors(document, project).anyMatch { editor =>
      LookupManager.getActiveLookup(editor) == null &&
        TemplateManager.getInstance(project).getActiveTemplate(editor) == null
    }

  private final class DebouncedTask(path: Path) extends Runnable { self =>
    private var requestToRun: CompilationRequest = _
    private var delay: FiniteDuration = _

    override def run(): Unit = {
      scheduledRequests.updateWith(path) {
        case Some(scheduled @ ScheduledCompilationRequests(scheduledTimestamp, requests)) =>
          val now = System.nanoTime()
          val elapsed = (now - scheduledTimestamp).nanoseconds
          val timeout = ScalaHighlightingMode.compilationDelay
          if (elapsed >= timeout) {
            val head = requests.head
            if (isReadyForExecution(head)) {
              requestToRun = head
              val tail = requests.tail
              if (tail.isEmpty) None
              else {
                delay = timeout
                Some(ScheduledCompilationRequests(now, tail))
              }
            } else {
              delay = timeout
              Some(scheduled)
            }
          } else {
            val remaining = timeout - elapsed
            delay = remaining
            Some(scheduled)
          }

        case None => None
      }

      if (requestToRun ne null) {
        execute(path, requestToRun)
        requestToRun = null
      }

      if (delay ne null) {
        val Duration(length, unit) = delay
        executor.schedule(self, length, unit)
        delay = null
      }
    }
  }

  private final class DeferredShowProgressIndicator(task: Task.Backgroundable)
    extends ProgressIndicatorBase {

    setOwnerTask(task)

    /**
     * Shows the progress in the Status bar.
     * This method partially duplicates constructor of the
     * [[com.intellij.openapi.progress.impl.BackgroundableProcessIndicator]].
     */
    //noinspection ApiStatus
    def show(): Unit =
      if (!project.isDisposed && !project.isDefault && !ApplicationManager.getApplication.isUnitTestMode)
        for {
          frameHelper <- WindowManagerEx.getInstanceEx.findFrameHelper(project).toOption
          statusBar <- frameHelper.getStatusBar.toOption
          statusBarEx <- statusBar.asOptionOf[StatusBarEx]
        } UIUtil.invokeLaterIfNeeded(() => statusBarEx.addProgress(this, task))
  }
}

private object CompilerHighlightingService {
  private val Log = Logger.getInstance(classOf[CompilerHighlightingService])

  def get(project: Project): CompilerHighlightingService =
    project.getService(classOf[CompilerHighlightingService])

  def platformAutomakeEnabled(project: Project): Boolean =
    CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE
}
