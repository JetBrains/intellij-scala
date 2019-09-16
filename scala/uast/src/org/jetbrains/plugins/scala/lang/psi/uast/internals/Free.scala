package org.jetbrains.plugins.scala.lang.psi.uast.internals

import org.jetbrains.uast.UElement

trait Free[U <: UElement] {
  def pinTo(parent: UElement): U = pinTo(LazyUElement.just(parent))

  def pinTo(lazyParent: LazyUElement): U

  def standalone: U = pinTo(parent = null)
}

object Free {
  def ignoringParent[U <: UElement](elem: U): Free[U] =
    new FreeImpl[U](_ => elem)

  def fromLazyConstructor[U <: UElement](
    constructor: LazyUElement => U
  ): Free[U] =
    new FreeImpl[U](constructor)

  def fromConstructor[U <: UElement](constructor: UElement => U): Free[U] =
    fromLazyConstructor[U](
      (lazyParent: LazyUElement) => constructor(lazyParent.force)
    )
}

private class FreeImpl[U <: UElement](constructor: LazyUElement => U)
    extends Free[U] {

  override def pinTo(lazyParent: LazyUElement): U = constructor(lazyParent)
}
