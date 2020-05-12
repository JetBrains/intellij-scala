package org.jetbrains.plugins.scala.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.Nullable
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

  def apply(original: Disposable): Disposable = {
    class DisposableTwin extends Disposable {
      @Nullable
      var twin: DisposableTwin = _

      override def dispose(): Unit = {
        if (twin != null) {
          twin.twin = null
          Disposer.dispose(twin)
        }
      }
    }

    val originalChild = new DisposableTwin
    val unloadingChild = new DisposableTwin
    originalChild.twin = unloadingChild
    unloadingChild.twin = originalChild

    Disposer.register(original, originalChild)
    Disposer.register(scalaPluginDisposable, unloadingChild)

    originalChild
  }
}
