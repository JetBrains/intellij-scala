package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.CompilerManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

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
private final class CompilerLockService(project: Project) {
  private val projectReadySemaphore: Semaphore = {
    val initialPermits = if (ApplicationManager.getApplication.isUnitTestMode) 1 else 0
    new Semaphore(initialPermits, true)
  }

  def withCompilerLock(indicator: ProgressIndicator)(body: => Unit): Unit = {
    withPermit(projectReadySemaphore, indicator) {
      if (!project.isDisposed) {
        val compilationSemaphore =
          CompilerManager.getInstance(project).asInstanceOf[CompilerManagerImpl].getCompilationSemaphore
        withPermit(compilationSemaphore, indicator) {
          body
        }
      }
    }
  }

  def markProjectReady(): Unit = {
    projectReadySemaphore.release()
  }

  private def withPermit(semaphore: Semaphore, indicator: ProgressIndicator)(body: => Unit): Unit = {
    var acquired = false
    try {
      while (!acquired) {
        acquired = semaphore.tryAcquire(300L, TimeUnit.MILLISECONDS)
        indicator.checkCanceled()
      }
      body
    } finally {
      if (acquired) {
        semaphore.release()
      }
    }
  }
}

private object CompilerLockService {
  def instance(project: Project): CompilerLockService =
    project.getService(classOf[CompilerLockService])
}
