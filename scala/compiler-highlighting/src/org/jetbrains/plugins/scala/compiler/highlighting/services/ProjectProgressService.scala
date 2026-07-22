package org.jetbrains.plugins.scala.compiler.highlighting.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

import java.util.concurrent.atomic.AtomicReference

/**
 * Tracks the [[ProgressIndicator]] of the compilation currently in progress, if any.
 */
@Service(Array(Service.Level.PROJECT))
final class ProjectProgressService {

  private val progressIndicator: AtomicReference[ProgressIndicator] = new AtomicReference()

  def isCompiling: Boolean = progressIndicator.get() ne null

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
  def apply(project: Project): ProjectProgressService =
    project.getService(classOf[ProjectProgressService])
}
