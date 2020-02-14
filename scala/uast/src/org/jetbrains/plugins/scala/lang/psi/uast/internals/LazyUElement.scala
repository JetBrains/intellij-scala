package org.jetbrains.plugins.scala.lang.psi.uast.internals

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.Nullable
import org.jetbrains.uast.UElement

trait LazyUElement {
  def force: UElement @Nullable
}

object LazyUElement {
  val Empty: LazyUElement = new LazyUElementImpl(() => null)

  def fromThunk(thunk: () => UElement @Nullable): LazyUElement =
    new LazyUElementImpl(thunk)

  def just(element: UElement @Nullable): LazyUElement = new Just(element)

  private def mayForce: Boolean =
    !ApplicationManager.getApplication.isDispatchThread

  private class Just(element: UElement) extends LazyUElement {
    override def force: UElement @Nullable = element
  }

  private class LazyUElementImpl(private var thunk: () => UElement @Nullable)
    extends LazyUElement {

    @volatile
    private  var isForced = false

    private lazy val forced: UElement = {
      val result = thunk()
      thunk = null
      isForced = true
      result
    }

    override def force: UElement = {
      if (mayForce || isForced) forced
      else null
    }
  }
}