package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks the [[ProgressIndicator]] of the compilation currently in progress, if any, and how far that
 * compilation has got.
 */
@Service(Array(Service.Level.PROJECT))
final class ProjectProgressService {

  private val progressIndicator: AtomicReference[ProgressIndicator] = new AtomicReference()

  /**
   * Fraction of the compilation currently in progress that is done, as reported by the compiler. Only
   * meaningful while [[isCompiling]]; it is reset when a compilation finishes.
   */
  @volatile private var progress: Double = ProjectProgressService.Finished

  def isCompiling: Boolean = progressIndicator.get() ne null

  def compilationProgress: Double = progress

  def setCompilationProgress(value: Double): Unit = progress = value

  def resetCompilationProgress(): Unit = progress = ProjectProgressService.Finished

  def cancel(): Unit = {
    val indicator = progressIndicator.get()
    if (indicator ne null) {
      indicator.cancel()
    }
  }

  def setIndicator(indicator: ProgressIndicator): Unit = progressIndicator.set(indicator)

  def clearIndicator(): Unit = progressIndicator.set(null)
}

object ProjectProgressService {

  private final val Finished: Double = 1.0

  def apply(project: Project): ProjectProgressService =
    project.getService(classOf[ProjectProgressService])
}
