package org.jetbrains.plugins.scala.compiler.highlighting.services.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.{StatusBarEx, WindowManagerEx}
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.jps.incremental.scala.remote.SourceScope
import org.jetbrains.plugins.scala.compiler.CompileServerLauncher
import org.jetbrains.plugins.scala.compiler.highlighting.core.CompilerEventGeneratingClient
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{EnsureServerRunningPhaseEvent, RequestId}
import org.jetbrains.plugins.scala.compiler.highlighting.services.requests.SerializableMap
import org.jetbrains.plugins.scala.compiler.highlighting.services.{CompilerLockService, ProjectProgressService, SaveService}
import org.jetbrains.plugins.scala.compiler.highlighting.util.CompilerHighlightingBundle
import org.jetbrains.plugins.scala.compiler.tracing.Tracing
import org.jetbrains.plugins.scala.compiler.tracing.core.events.EndEvent
import org.jetbrains.plugins.scala.extensions.*
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode
import org.jetbrains.plugins.scala.util.CanonicalPath

import java.io.EOFException
import scala.concurrent.duration.*
import scala.concurrent.{Await, Future, Promise}

object CompilationUtils {

  /**
   * A Scala source file open in an editor that is eligible for document compilation, together with the
   * data needed to compile it.
   */
  final case class EligibleDocument(module: Module, sourceScope: SourceScope, document: Document, virtualFile: VirtualFile, psiFile: PsiFile)

  private val Log = Logger.getInstance(CompilationUtils.getClass)

  /**
   * @param await when `true`, blocks until the compilation completes; when `false`, the compilation is scheduled
   *              and this method returns immediately (fire-and-forget).
   */
  def prepareCompilation(project: Project, requestId: RequestId, await: Boolean = true)(compile: => Future[Unit]): Unit = {
    val tracer = Tracing(project)
    try {
      SaveService(project).saveProject()
      tracer.trace(EnsureServerRunningPhaseEvent(requestId)) {
        CompileServerLauncher.ensureServerRunning(project)
      }
      if (project.isDisposed) return
      val future = compile
      if (await) Await.result(future, Duration.Inf)
    } catch {
      case _: InterruptedException =>
        // Disposing of the CompilerHighlightingService (on project close) interrupts the compilation through the
        // Java thread interruption mechanism.
        tracer.instant(EndEvent(requestId, "Project close"))
      case _: EOFException =>
        // Stopping the Scala Compiler Server can result EOF exceptions to be thrown when trying to read from the
        // byte communication stream.
        tracer.instant(EndEvent(requestId, "EOF after stopping scala compiler server"))
    }
  }

  def performCompilation(
    project: Project,
    requestId: RequestId,
    documentVersions: SerializableMap[CanonicalPath, Long],
    delayIndicator: Boolean,
    refreshVfs: Boolean
  )(compile: CompilerEventGeneratingClient => Unit): Future[Unit] = {
    val promise = Promise[Unit]()
    val taskMsg = CompilerHighlightingBundle.message("highlighting.compilation")

    val task = new Task.Backgroundable(project, taskMsg, true) {
      override def run(indicator: ProgressIndicator): Unit = {
        if (project.isDisposed) return
        val progress = ProjectProgressService(project)
        progress.setIndicator(indicator)
        try {
          val client = new CompilerEventGeneratingClient(project, indicator, Log, refreshVfs, documentVersions)
          CompilerLockService.instance(project).withCompilerLock(indicator, requestId) {
            compile(client)
          }
          promise.success(())
        } catch {
          case t: Throwable => promise.failure(t)
        } finally {
          progress.clearIndicator()
        }
      }
    }

    val indicator = new DeferredShowProgressIndicator(project, task)
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator)
    val Duration(length, unit) =
      (if (delayIndicator) ScalaHighlightingMode.compilationTimeoutToShowProgress else 1.second): @unchecked

    AppExecutorUtil.getAppScheduledExecutorService.schedule((() => indicator.show()): Runnable, length, unit)
    promise.future
  }
  
  private class DeferredShowProgressIndicator(project: Project, task: Task.Backgroundable) extends ProgressIndicatorBase {
    setOwnerTask(task)

    /**
     * Shows the progress in the Status bar.
     * This method partially duplicates constructor of the
     * [[com.intellij.openapi.progress.impl.BackgroundableProcessIndicator]].
     */
    //noinspection ApiStatus
    def show(): Unit =
      if (!project.isDisposed && !project.isDefault && !ApplicationManager.getApplication.isUnitTestMode) {
        for {
          frameHelper <- WindowManagerEx.getInstanceEx.findFrameHelper(project).toOption
          statusBar <- frameHelper.getStatusBar.toOption
          statusBarEx <- statusBar.asOptionOf[StatusBarEx]
        } UIUtil.invokeLaterIfNeeded(() => statusBarEx.addProgress(this, task))
      }
  }
}
