package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

object UnloadAwareDisposable {
  /*
   * A disposable that is disposed when the scala plugin is unloaded
   * Chain to it when you think that your disposable might live longer then the plugin
   * (like Project or Module)
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
