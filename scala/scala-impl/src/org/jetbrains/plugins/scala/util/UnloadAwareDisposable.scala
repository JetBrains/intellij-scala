package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.extensions.invokeOnScalaPluginUnload

object UnloadAwareDisposable {
  /*
   * A disposable that is disposed when the scala plugin is unloaded
   * Chain to it when you think that your disposable might live longer then the plugin
   * (like Project or Module)
   */
  lazy val scalaPluginDisposable: Disposable = {
    val unloadDisposable = Disposer.newDisposable("Scala plugin disposer")

    invokeOnScalaPluginUnload {
      Disposer.dispose(unloadDisposable)
    }

    unloadDisposable
  }

  def forProject(project: Project): Disposable = {
    project.getService(classOf[UnloadAwareProjectDisposableService])
  }

  @Service
  private final class UnloadAwareProjectDisposableService extends Disposable {
    override def dispose(): Unit = ()
  }
}
