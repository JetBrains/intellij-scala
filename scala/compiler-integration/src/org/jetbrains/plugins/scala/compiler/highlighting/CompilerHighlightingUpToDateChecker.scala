package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.backwardRefs.IsUpToDateCheckConsumer
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.settings.ScalaHighlightingMode

/**
 * Runs once on project startup. Reports the result of the JPS up-to-date checker. Regardless of the outcome, we
 * enable compiler based highlighting for the project.
 *
 * This listener exists to prevent compiler based highlighting to run before the JPS up-to-date check. This is because
 * the JPS up-to-date check runs without holding the CompilerManager compilation semaphore. This semaphore is used to
 * isolate the JPS process from the JPS code running in the Scala Compile Server as part of the compiler based
 * highlighting pipeline. These two processes share the same working directory, and it is important to write to it in
 * a mutually exclusive manner, otherwise there is a risk of corrupting the shared state and forcing possibly multiple
 * project rebuilds until it is fully resolved.
 *
 * @see [[CompilerLockService]].
 */
private final class CompilerHighlightingUpToDateChecker extends IsUpToDateCheckConsumer {
  override def isApplicable(project: Project): Boolean = {
    if (ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(project)) {
      // The project is a Scala project with Compiler Based Highlighting enabled. We need to wait for the JPS
      // up-to-date check to complete before initializing the CBH system, in order to avoid state corruption of the
      // JPS process.
      true
    } else {
      // In projects where Compiler Based Highlighting is not enabled (including non-Scala projects), we do not need to
      // wait for the up-to-date checker to run first. This also avoids showing the up-to-date message in the build
      // window.
      markProjectReady(project)
      false
    }
  }

  override def isUpToDate(project: Project, isUpToDate: Boolean): Unit = {
    markProjectReady(project)
  }

  private def markProjectReady(project: Project): Unit = {
    if (project.isDisposed) return
    CompilerLockService.instance(project).markProjectReady()
  }
}
