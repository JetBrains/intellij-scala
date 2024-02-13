package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.backwardRefs.IsUpToDateCheckConsumer
import com.intellij.openapi.project.Project

/**
 * Runs once on project startup. Reports the result of the JPS up-to-date checker. Regardless of the outcome, we
 * enable compiler based highlighting for the project.
 *
 * This listener exists to prevent compiler based highlighting to run before the JPS up-to-date check. This is because
 * the JPS up-to-date check runs without holding the CompilerManager compilation semaphore. This semaphore is used to
 * isolate the JPS process from the JPS code running in the Scala Compile Server as part of the compiler based
 * highlighting pipeline. These two processes share the same working directory, and it is important to write to it in
 * a mutually exclusive manner, otherwise.
 *
 * @see [[CompilerLockService]].
 */
private final class CompilerHighlightingUpToDateChecker extends IsUpToDateCheckConsumer {
  override def isApplicable(project: Project): Boolean = true

  override def isUpToDate(project: Project, isUpToDate: Boolean): Unit = {
    if (project.isDisposed) return
    CompilerLockService.instance(project).markProjectReady()
  }
}
