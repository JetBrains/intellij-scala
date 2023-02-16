package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.{DaemonCodeAnalyzerEx, FileStatusMap}
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
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.caches.cached
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerIntegrationBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.ModuleExt
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicReference
import scala.collection.concurrent.TrieMap
import scala.collection.immutable.SortedSet
import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}
import scala.util.Try

@Service(Array(Service.Level.PROJECT))
private final class CompilerHighlightingService(project: Project) extends Disposable {

  import CompilerHighlightingService._

  private val executor: ScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService(getClass.getSimpleName, 1)

  private val indicatorExecutor: ScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService("CompilerHighlightingIndicator", 1)

  private val scheduledRequests: TrieMap[VirtualFile, ScheduledCompilationRequests] = TrieMap.empty

  private val progressIndicator: AtomicReference[ProgressIndicator] = new AtomicReference()

  private def debug(message: => String): Unit = {
    if (Log.isDebugEnabled) {
      Log.debug(s"[${project.getName}] $message")
    }
  }

  //noinspection SameParameterValue
  private def warn(message: => String): Unit = {
    Log.warn(s"[${project.getName}] $message")
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
    virtualFile: VirtualFile,
    module: Module,
    sourceScope: SourceScope,
    document: Document,
    psiFile: PsiFile,
    debugReason: String
  ): Unit = {
    if (platformAutomakeEnabled(project)) {
      //we need to save documents right away or automake won't happen
      TriggerCompilerHighlightingService.get(project).beforeIncrementalCompilation()
      BuildManager.getInstance().scheduleAutoMake()
      // clearOutputDirectories is invoked in AutomakeBuildManagerListener
    } else {
      schedule(virtualFile, CompilationRequest.IncrementalRequest(module, sourceScope, document, psiFile, debugReason))
    }
  }

  def triggerDocumentCompilation(
    virtualFile: VirtualFile,
    module: Module,
    sourceScope: SourceScope,
    document: Document,
    debugReason: String
  ): Unit =
    schedule(virtualFile, CompilationRequest.DocumentRequest(module, sourceScope, document, debugReason))

  def triggerWorksheetCompilation(
    virtualFile: VirtualFile,
    psiFile: ScalaFile,
    document: Document,
    isFirstTimeHighlighting: Boolean,
    debugReason: String
  ): Unit =
    schedule(virtualFile, CompilationRequest.WorksheetRequest(psiFile, document, isFirstTimeHighlighting, debugReason))

  private def schedule(virtualFile: VirtualFile, request: CompilationRequest): Unit = {
    val now = System.nanoTime()
    scheduledRequests.updateWith(virtualFile) {
      case Some(ScheduledCompilationRequests(_, reqs)) =>
        val newReqs = reqs.filter(_.priority < request.priority) + request
        Some(ScheduledCompilationRequests(now, newReqs))
      case None =>
        val reqs = SortedSet(request)
        Some(ScheduledCompilationRequests(now, reqs))
    }

    val Duration(length, unit) = ScalaHighlightingMode.compilationDelay
    executor.schedule(new DebouncedTask(virtualFile), length, unit)
  }

  private def execute(virtualFile: VirtualFile, request: CompilationRequest): Unit = {
    if (!hasAnnotatorErrors(project, request.document)) {
      request match {
        case wr: CompilationRequest.WorksheetRequest =>
          executeWorksheetCompilationRequest(wr)
        case ir: CompilationRequest.IncrementalRequest =>
          executeIncrementalCompilationRequest(virtualFile, ir)
        case dr: CompilationRequest.DocumentRequest =>
          executeDocumentCompilationRequest(virtualFile, dr)
      }
    }
  }

  private def executeWorksheetCompilationRequest(request: CompilationRequest.WorksheetRequest): Unit = {
    val CompilationRequest.WorksheetRequest(file, document, isFirstTimeHighlighting, debugReason) = request
    debug(s"worksheetCompilation: $debugReason (isFirstTimeHighlighting: ${isFirstTimeHighlighting})")

    //Note, we don't need to invoke `findRepresentativeModuleForSharedSourceModuleOrSelf`
    //because it's already called for all worksheets in WorksheetSyntheticModuleService
    val module = file.module match {
      case Some(m) => m
      case None =>
        warn(s"can't find module for worksheet ${file.name}")
        return
    }

    if (isFirstTimeHighlighting) {
      //If we have just opened worksheet we need to invoke incremental compilation to ensure that worksheet module is compiled to avoid red code
      //Otherwise if you open non-compiled project and open worksheet it will contain red code
      val virtualFile = file.getVirtualFile
      val sourceScope = if (TestSourcesFilter.isTestSources(virtualFile, project)) SourceScope.Test else SourceScope.Production
      val incrementalRequest = CompilationRequest.IncrementalRequest(module, sourceScope, document, file, debugReason)
      executeIncrementalCompilationRequest(virtualFile, incrementalRequest)
    }

    performCompilation(delayIndicator = true)(WorksheetHighlightingCompiler.compile(file, document, module, _))
  }

  private def executeIncrementalCompilationRequest(
    virtualFile: VirtualFile,
    request: CompilationRequest.IncrementalRequest
  ): Unit = {
    val CompilationRequest.IncrementalRequest(module, sourceScope, _, psiFile, debugReason) = request
    debug(s"incrementalCompilation: $debugReason")
    performCompilation(delayIndicator = false) { client =>
      val triggerService = TriggerCompilerHighlightingService.get(project)
      triggerService.beforeIncrementalCompilation()
      try IncrementalCompiler.compile(project, module.findRepresentativeModuleForSharedSourceModuleOrSelf, sourceScope, client)
      finally {
        if (psiFile.is[ScalaFile]) {
          triggerService.enableDocumentCompiler(virtualFile)
        }
      }
    }
  }

  private def executeDocumentCompilationRequest(virtualFile: VirtualFile, request: CompilationRequest.DocumentRequest): Unit = {
    val CompilationRequest.DocumentRequest(module, sourceScope, document, debugReason) = request
    debug(s"documentCompilation: $debugReason")
    performCompilation(delayIndicator = true)(DocumentCompiler.get(project).compile(module.findRepresentativeModuleForSharedSourceModuleOrSelf, sourceScope, document, virtualFile, _))
  }

  private def performCompilation(delayIndicator: Boolean)(compile: Client => Unit): Unit = {
    saveProjectOnce()
    CompileServerLauncher.ensureServerRunning(project)
    val promise = Promise[Unit]()

    val taskMsg = CompilerIntegrationBundle.message("highlighting.compilation")
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
    val Duration(length, unit) =
      if (delayIndicator) ScalaHighlightingMode.compilationTimeoutToShowProgress else 1.second
    val runnable: Runnable = () => indicator.show()
    indicatorExecutor.schedule(runnable, length, unit)

    Await.result(promise.future, Duration.Inf)
  }

  // SCL-17295
  private val saveProjectOnce = cached("CompilerHighlightingService.saveProjectOnce", ModificationTracker.NEVER_CHANGED, () => {
    if (!project.isDisposed || project.isDefault) project.save()
  })

  private def isReadyForExecution(request: CompilationRequest): Boolean = request match {
    case CompilationRequest.WorksheetRequest(_, document, _, _) => isDocumentReadyForCompilation(document)
    case CompilationRequest.IncrementalRequest(_, _, _, _, _) => true
    case CompilationRequest.DocumentRequest(_, _, document, _) => isDocumentReadyForCompilation(document)
  }

  private def isDocumentReadyForCompilation(document: Document): Boolean =
    EditorFactory.getInstance().editors(document, project).anyMatch { editor =>
      LookupManager.getActiveLookup(editor) == null &&
        TemplateManager.getInstance(project).getActiveTemplate(editor) == null
    }

  private final class DebouncedTask(virtualFile: VirtualFile) extends Runnable { self =>
    private var requestToRun: CompilationRequest = _
    private var delay: FiniteDuration = _

    override def run(): Unit = {
      scheduledRequests.updateWith(virtualFile) {
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
        execute(virtualFile, requestToRun)
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

  private def hasAnnotatorErrors(project: Project, document: Document): Boolean = {
    val fileStatusMap = DaemonCodeAnalyzerEx.getInstanceEx(project).getFileStatusMap
    wasErrorFoundMethod.invoke(fileStatusMap, document).asInstanceOf[Boolean]
  }

  private final val wasErrorFoundMethod: java.lang.reflect.Method =
    classOf[FileStatusMap].getDeclaredMethod("wasErrorFound", classOf[Document])

  wasErrorFoundMethod.setAccessible(true)
}
