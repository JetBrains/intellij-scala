package org.jetbrains.plugins.scala.lang.psi.uast.internals

import org.jetbrains.annotations.Nullable
import org.jetbrains.uast.UElement

trait LazyUElement {
  def force: UElement @Nullable
}

object LazyUElement {
  val Empty: LazyUElement = new LazyUElementImpl(() => null)

  def fromThunk(thunk: () => UElement @Nullable): LazyUElement =
    new LazyUElementImpl(thunk)

  def just(element: UElement @Nullable): LazyUElement =
    new LazyUElementImpl(() => element)
}

private class LazyUElementImpl(thunk: () => UElement @Nullable)
    extends LazyUElement {

  override lazy val force: UElement = thunk()
}
