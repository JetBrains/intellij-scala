package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.server.BuildManager
import com.intellij.configurationStore.StoreUtilKt
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.{ApplicationManager, ReadAction}
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.{Document, EditorFactory}
import com.intellij.openapi.fileEditor.{FileDocumentManager, FileEditorManager}
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProcessCanceledException, ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.roots.{ProjectRootManager, TestSourcesFilter}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.psi.{PsiFile, PsiManager}
import com.intellij.task.{ProjectTaskContext, ProjectTaskManager}
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.{BuildersKt, CoroutineScope}
import org.jetbrains.bsp.BspUtil
import org.jetbrains.bsp.project.{BspProjectTaskRunner, CustomTaskArguments}
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.build.CompilerEventReporter
import org.jetbrains.plugins.scala.compiler.{CompileServerLauncher, CompilerIntegrationBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel}
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.CompilationId

import java.io.EOFException
import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import java.util.concurrent.{ConcurrentSkipListSet, ScheduledExecutorService, ScheduledFuture, TimeUnit}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.control.NonFatal

@Service(Array(Service.Level.PROJECT))
private final class CompilerHighlightingService(project: Project, coroutineScope: CoroutineScope) extends Disposable {

  import CompilerHighlightingService._

  private val executor: ScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService(getClass.getSimpleName, 1)

  private val indicatorExecutor: ScheduledExecutorService =
    AppExecutorUtil.createBoundedScheduledExecutorService("CompilerHighlightingIndicator", 1)

  private val priorityQueue: ConcurrentSkipListSet[CompilationRequest] =
    new ConcurrentSkipListSet(CompilationRequest.compilationRequestOrdering)

  private val compilationTask: AtomicReference[ScheduledFuture[_]] = new AtomicReference()

  private val progressIndicator: AtomicReference[ProgressIndicator] = new AtomicReference()

  private val projectSaveTracker: AtomicBoolean = new AtomicBoolean(false)

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
    priorityQueue.clear()
    val task = compilationTask.getAndSet(null)
    if (task ne null) {
      task.cancel(true)
    }
    val indicator = progressIndicator.getAndSet(null)
    if (indicator ne null) {
      indicator.cancel()
    }
  }

  def triggerIncrementalCompilation(
    virtualFile: VirtualFile,
    module: Module,
    document: Document,
    psiFile: PsiFile,
    debugReason: String
  ): Unit = {
    if (platformAutomakeEnabled(project)) {
      invokeLater {
        //we need to save documents right away or automake won't happen
        TriggerCompilerHighlightingService.get(project).beforeIncrementalCompilation()
        BuildManager.getInstance().scheduleAutoMake()
      }
    } else {
      val sourceScope = calculateSourceScope(virtualFile)
      schedule(CompilationRequest.IncrementalRequest(module, sourceScope, virtualFile, document, psiFile, debugReason))
    }
  }

  def triggerDocumentCompilation(
    virtualFile: VirtualFile,
    module: Module,
    document: Document,
    debugReason: String
  ): Unit = {
    val sourceScope = calculateSourceScope(virtualFile)
    schedule(CompilationRequest.DocumentRequest(module, sourceScope, virtualFile, document, debugReason))
  }

  def triggerWorksheetCompilation(
    virtualFile: VirtualFile,
    psiFile: ScalaFile,
    document: Document,
    isFirstTimeHighlighting: Boolean,
    debugReason: String
  ): Unit =
    schedule(CompilationRequest.WorksheetRequest(psiFile, virtualFile, document, isFirstTimeHighlighting, debugReason))

  private[highlighting] def saveProjectOnNextCompilation(): Unit = {
    projectSaveTracker.set(true)
  }

  private def calculateSourceScope(virtualFile: VirtualFile): SourceScope = {
    def sourceScope: SourceScope =
      if (TestSourcesFilter.isTestSources(virtualFile, project)) SourceScope.Test
      else SourceScope.Production

    ReadAction
      .nonBlocking(() => sourceScope)
      .expireWith(this) // Cancel when this service is disposed.
      .expireWhen(() => project.isDisposed) // Cancel when this project is disposed.
      .inSmartMode(project) // TestSourcesFilter.isTestSources can access file indices, so smart mode is required.
      .executeSynchronously() // Already running in a background thread, execute in place.
  }

  private def schedule(request: CompilationRequest): Unit = {
    priorityQueue.add(request)
    scheduleCompilationTask(request.compilationDelay)
  }

  private def execute(request: CompilationRequest): Unit = request match {
    case wr: CompilationRequest.WorksheetRequest =>
      executeWorksheetCompilationRequest(wr)
    case ir: CompilationRequest.IncrementalRequest =>
      executeIncrementalCompilationRequest(ir, runDocumentCompiler = true)
    case dr: CompilationRequest.DocumentRequest =>
      executeDocumentCompilationRequest(dr)
  }

  private def executeWorksheetCompilationRequest(request: CompilationRequest.WorksheetRequest): Unit = {
    val CompilationRequest.WorksheetRequest(file, virtualFile, document, isFirstTimeHighlighting, debugReason) = request
    debug(s"worksheetCompilation: $debugReason (isFirstTimeHighlighting: $isFirstTimeHighlighting)")

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
      val realModule = module match {
        case synthetic: SyntheticModule =>
          // We need to compile the real module, not the synthetic one for the worksheet. Otherwise, bytecode for
          // classes from the real module will not be produced.
          synthetic.underlying
        case m => m
      }

      val sourceScope = calculateSourceScope(virtualFile)
      val incrementalRequest = CompilationRequest.IncrementalRequest(realModule, sourceScope, virtualFile, document, file, debugReason)
      executeIncrementalCompilationRequest(incrementalRequest, runDocumentCompiler = false)
    }

    prepareCompilation(await = true) {
      performCompilation(delayIndicator = true, refreshVfs = false) { client =>
        WorksheetHighlightingCompiler.compile(file, document, module, client)
      }
    }
  }

  private def executeIncrementalCompilationRequest(request: CompilationRequest.IncrementalRequest, runDocumentCompiler: Boolean): Unit = {
    debug(s"incrementalCompilation: ${request.debugReason}")
    prepareCompilation(await = true) {
      val promise = Promise[Unit]()
      // Documents must be saved on the UI thread, so a thread shift is mandatory in this case.
      invokeLater {
        val future =
          if (project.isDisposed) Future.unit
          else {
            TriggerCompilerHighlightingService.get(project).beforeIncrementalCompilation()
            // Perform the rest of the execution of this incremental compilation on a background thread.
            performCompilation(delayIndicator = false, refreshVfs = true) { client =>
              if (BspUtil.isBspProject(project)) {
                doBspIncrementalCompilation(request, client, runDocumentCompiler)
              } else {
                doJpsIncrementalCompilation(request, client, runDocumentCompiler)
              }
            }
          }
        promise.completeWith(future)
      }
      promise.future
    }
  }

  private def doBspIncrementalCompilation(
    request: CompilationRequest.IncrementalRequest,
    client: CompilerEventGeneratingClient,
    runDocumentCompiler: Boolean
  ): Unit = {
    val CompilationRequest.IncrementalRequest(module, sourceScope, virtualFile, _, psiFile, _) = request
    val context = new ProjectTaskContext()
    val representativeModule = module.findRepresentativeModuleForSharedSourceModuleOrSelf
    val task = ProjectTaskManager.getInstance(project)
      .createModulesBuildTask(Array(representativeModule), true, true, false, sourceScope == SourceScope.Test)
    val reporter = new CompilerEventReporter(project, CompilationId.generate())
    val arguments = CustomTaskArguments(
      CompilerIntegrationBundle.message("highlighting.compilation"),
      reporter
    )
    val taskRunner = new BspProjectTaskRunner(Some(arguments))
    val promise = taskRunner.run(project, context, task)
    promise.blockingGet(1, TimeUnit.DAYS)

    if (runDocumentCompiler && reporter.successful) {
      triggerDocumentCompilationInAllOpenEditors(Some(client))
    }
    if (psiFile.is[ScalaFile] && reporter.successful && client.successful && !project.isDisposed) {
      TriggerCompilerHighlightingService.get(project).enableDocumentCompiler(virtualFile)
    }
  }

  private def doJpsIncrementalCompilation(
    request: CompilationRequest.IncrementalRequest,
    client: CompilerEventGeneratingClient,
    runDocumentCompiler: Boolean
  ): Unit = {
    val CompilationRequest.IncrementalRequest(module, sourceScope, virtualFile, _, psiFile, _) = request
    IncrementalCompiler.compile(project, module.findRepresentativeModuleForSharedSourceModuleOrSelf, sourceScope, client)
    if (runDocumentCompiler && client.successful) {
      triggerDocumentCompilationInAllOpenEditors(Some(client))
    }
    if (psiFile.is[ScalaFile] && client.successful && !project.isDisposed) {
      TriggerCompilerHighlightingService.get(project).enableDocumentCompiler(virtualFile)
    }
  }

  private def executeDocumentCompilationRequest(request: CompilationRequest.DocumentRequest): Unit = {
    val CompilationRequest.DocumentRequest(module, sourceScope, virtualFile, document, debugReason) = request
    debug(s"documentCompilation: $debugReason")
    executeDocumentCompilationRequest(module, sourceScope, virtualFile, document, await = true)
  }

  private def executeDocumentCompilationRequest(
    module: Module,
    sourceScope: SourceScope,
    virtualFile: VirtualFile,
    document: Document,
    await: Boolean
  ): Unit = {
    prepareCompilation(await) {
      performCompilation(delayIndicator = true, refreshVfs = false) { client =>
        DocumentCompiler.get(project)
          .compile(module.findRepresentativeModuleForSharedSourceModuleOrSelf, sourceScope, document, virtualFile, client)
      }
    }
  }

  private[highlighting] def triggerDocumentCompilationInAllOpenEditors(client: Option[CompilerEventGeneratingClient]): Unit = {
    val selectedFiles = FileEditorManager.getInstance(project).getSelectedFiles
    selectedFiles.flatMap { vf =>
      val (document, module, psiFile) = inReadAction {
        (
          FileDocumentManager.getInstance().getDocument(vf),
          ProjectRootManager.getInstance(project).getFileIndex.getModuleForFile(vf),
          PsiManager.getInstance(project).findFile(vf)
        )
      }
      // Filtering by the Scala language level also ensures that the module has a Scala SDK configured and that the
      // document compiler will not be called in modules which do not have Scala configured (or during project import).
      // The Scala language level of a module is derived from the configured SDK.
      if (document == null || module == null || psiFile == null)
        None
      else if (psiFile.is[ScalaFile]) {
        if (!psiFile.isScalaWorksheet && module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_3_3)) {
          val sourceScope = calculateSourceScope(vf)
          Some((module, sourceScope, document, vf))
        } else None
      }
      else None
    }.foreach { case (module, sourceScope, document, virtualFile) =>
      client match {
        case Some(c) =>
          DocumentCompiler.get(project).compile(module.findRepresentativeModuleForSharedSourceModuleOrSelf, sourceScope, document, virtualFile, c)
        case None =>
          executeDocumentCompilationRequest(module, sourceScope, virtualFile, document, await = false)
      }
    }
  }

  private def prepareCompilation(await: Boolean)(compile: => Future[Unit]): Unit = {
    try {
      saveProject()
      CompileServerLauncher.ensureServerRunning(project)
      if (project.isDisposed) return
      val future = compile
      if (await) {
        Await.result(future, Duration.Inf)
      }
    } catch {
      case _: InterruptedException =>
        // Disposing of the CompilerHighlightingService (on project close) interrupts the compilation through the
        // Java thread interruption mechanism.
      case _: EOFException =>
        // Stopping the Scala Compiler Server can result EOF exceptions to be thrown when trying to read from the
        // byte communication stream.
    }
  }

  private def performCompilation(delayIndicator: Boolean, refreshVfs: Boolean)(compile: CompilerEventGeneratingClient => Unit): Future[Unit] = {
    val promise = Promise[Unit]()
    val taskMsg = CompilerIntegrationBundle.message("highlighting.compilation")

    val task = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = {
        if (project.isDisposed) return

        try {
          progressIndicator.set(indicator)
          val client = new CompilerEventGeneratingClient(project, indicator, Log, refreshVfs)
          CompilerLockService.instance(project).withCompilerLock(indicator) {
            compile(client)
          }
          promise.success(())
        } catch {
          case t: Throwable =>
            promise.failure(t)
        } finally {
          progressIndicator.set(null)
        }
      }
    }

    val indicator = new DeferredShowProgressIndicator(task)
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
    val Duration(length, unit) =
      if (delayIndicator) ScalaHighlightingMode.compilationTimeoutToShowProgress else 1.second
    val runnable: Runnable = () => indicator.show()
    indicatorExecutor.schedule(runnable, length, unit)
    promise.future
  }

  // SCL-17295, SCL-22491
  private def saveProject(): Unit = {
    if (projectSaveTracker.compareAndSet(true, false)) {
      if (!project.isDisposed || project.isDefault) {
        BuildersKt.runBlocking(
          coroutineScope.getCoroutineContext,
          (_, continuation) => StoreUtilKt.saveSettings(project, false, continuation)
        )
      }
    }
  }

  private def isReadyForExecution(request: CompilationRequest): RequestState = {
    if (!request.virtualFile.isValid) {
      RequestState.Expired
    } else if (request.remaining < Duration.Zero) {
      if (DumbService.isDumb(project)) return RequestState.NotReady
      request match {
        case CompilationRequest.WorksheetRequest(_, _, document, _, _) => canDocumentBeCompiled(document)
        case CompilationRequest.IncrementalRequest(_, _, _, _, _, _) => RequestState.Ready
        case CompilationRequest.DocumentRequest(_, _, _, document, _) => canDocumentBeCompiled(document)
      }
    } else RequestState.NotReady
  }

  private def canDocumentBeCompiled(document: Document): RequestState = {
    val editors = EditorFactory.getInstance().getEditors(document, project)
    if (editors.isEmpty) {
      // There are no open editors for the document. Skip the request.
      RequestState.Expired
    } else {
      val ready = editors.exists { editor =>
        LookupManager.getActiveLookup(editor) == null &&
          TemplateManager.getInstance(project).getActiveTemplate(editor) == null
      }

      // If there are no active lookups or templates in the editor, it can be compiled.
      // Otherwise, delay the request, in case the user dismisses lookups and templates without typing letters
      // (e.g. with the Esc key)
      if (ready) RequestState.Ready else RequestState.NotReady
    }
  }

  private def scheduleCompilationTask(compilationDelay: FiniteDuration): Unit = {
    val Duration(delay, unit) = compilationDelay
    val future = executor.schedule(new CompilationTask(), delay, unit)
    val previous = compilationTask.getAndSet(future)
    if (previous ne null) {
      previous.cancel(false)
    }
  }

  private final class CompilationTask extends Runnable {
    override def run(): Unit = {
      val request = priorityQueue.pollFirst()
      if (request ne null) {
        isReadyForExecution(request) match {
          case RequestState.Ready =>
            try {
              // The request with the highest priority is ready for execution. We need to also remove all requests from the
              // priority queue that are subsumed by this request. The `tailSet` returns all compilation requests that
              // have equal or smaller priority than the current one. Out of those, we only remove the requests for the same
              // source file, as less important requests for unrelated files are still useful and should be executed.
              priorityQueue.tailSet(request).forEach { r =>
                // remove less important compilation requests for the same file
                if (r.virtualFile.getCanonicalPath == request.virtualFile.getCanonicalPath) {
                  priorityQueue.remove(r)
                }
              }
              execute(request)
            } catch {
              case _: ProcessCanceledException | _: InterruptedException =>
                // Do not log PCE or InterruptedException.
              case NonFatal(t) =>
                Log.error(s"Execution of compilation request $request failed", t)
            }

          case RequestState.NotReady =>
            val delayed = request.delayed
            priorityQueue.add(delayed)

          case RequestState.Expired =>
        }
      }

      // Will not be rescheduled if there are no requests left.
      reschedule()
    }

    private def reschedule(): Unit = {
      val first =
        try priorityQueue.first()
        catch { case _: NoSuchElementException => null }

      if (first ne null) {
        scheduleCompilationTask(first.remaining)
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

  private def platformAutomakeEnabled(project: Project): Boolean =
    CompilerWorkspaceConfiguration.getInstance(project).MAKE_PROJECT_ON_SAVE

  sealed trait RequestState
  object RequestState {
    case object Ready extends RequestState
    case object NotReady extends RequestState
    case object Expired extends RequestState
  }
}
