package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

object UnloadAwareDisposable {

  /**
   * A disposable that is disposed when the scala plugin is unloaded
   * (that includes Application exit in non-unit-test mode)
   *
   * Use it as a parent when you think that your current parent disposable might live longer then the plugin
   * (like Application, Project or Module)
   *
   * If you have a class that does not implement Disposable, but you still register it somewhere where it is referenced
   * longer than the plugin lives, then we fail to clean up correctly.
   * So in that case you could make it a disposable, chain it to `scalaPluginDisposable`
   * and tidy up correctly when dispose() is called.
   *
   * @note
   * it isn't disposed during tests run (see description of [[ScalaShutDownTracker]]).<br>
   * If you want to ensure some work to be triggered on application exit
   * you consider using [[ScalaShutDownTracker.registerShutdownTask]]
   */
  lazy val scalaPluginDisposable: Disposable =
    ApplicationManager.getApplication.getService(classOf[UnloadAwareDisposableService])

  def forProject(project: Project): Disposable = {
    project.getService(classOf[UnloadAwareDisposableService])
  }

  @Service
  private final class UnloadAwareDisposableService extends Disposable {
    override def dispose(): Unit = ()
  }
}
