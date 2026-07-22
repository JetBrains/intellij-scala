package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.compiler.CompilerManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.events.TriggerPhaseEvents.{CompilerLockWaitPhaseEvent, LockWaitKind, RequestId}
import org.jetbrains.plugins.scala.compiler.tracing.Tracing

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Semaphore, TimeUnit}

/**
 * This service is isolates the JPS process from the JPS code running in the Scala Compile Server as part of the
 * compiler based highlighting pipeline, using the CompilerManager compilation semaphore. These two processes share the
 * same working directory, and it is very important to write to the shared directory in a mutually exclusive manner.
 * Otherwise, the JPS process state can become corrupted, forcing the user to do a full project rebuild in order to
 * resolve the issue.
 *
 * @see [[CompilerHighlightingUpToDateChecker]].
 */
@Service(Array(Service.Level.PROJECT))
private[highlighting] final class CompilerLockService(project: Project) {
  private val projectReadySemaphore: Semaphore = {
    val initialPermits = if (ApplicationManager.getApplication.isUnitTestMode) 1 else 0
    new Semaphore(initialPermits, true)
  }

  private val ready: AtomicBoolean = new AtomicBoolean(false)

  def withCompilerLock(indicator: ProgressIndicator, requestId: RequestId)(body: => Unit): Unit = {
    withPermit(projectReadySemaphore, indicator, LockWaitKind.ProjectReady, requestId) {
      if (!project.isDisposed) {
        val jpsBuildSemaphore =
          CompilerManager.getInstance(project).asInstanceOf[CompilerManagerImpl].getCompilationSemaphore
        withPermit(jpsBuildSemaphore, indicator, LockWaitKind.JpsBuild, requestId) {
          body
        }
      }
    }
  }

  def markProjectReady(): Unit = {
    if (ready.compareAndSet(false, true)) {
      projectReadySemaphore.release()
    }
  }

  def isReady: Boolean = ready.get()

  private def withPermit(semaphore: Semaphore, indicator: ProgressIndicator, lockName: LockWaitKind,
                         requestId: RequestId)(body: => Unit): Unit = {
    var acquired = false
    var spanClosed = false
    val span = Tracing(project).begin(CompilerLockWaitPhaseEvent(lockName, requestId))

    def closeSpan(): Unit =
      if (!spanClosed) {
        spanClosed = true
        Tracing(project).end(span)
      }

    try {
        while (!acquired) {
          acquired = semaphore.tryAcquire(300L, TimeUnit.MILLISECONDS)
          indicator.checkCanceled()
        }
        closeSpan()
        body
    } finally {
      // Covers the case where the wait itself was cancelled or threw, so the span is never left open.
      closeSpan()
      if (acquired) {
        semaphore.release()
      }
    }
  }
}

private[highlighting] object CompilerLockService {
  def instance(project: Project): CompilerLockService =
    project.getService(classOf[CompilerLockService])
}
