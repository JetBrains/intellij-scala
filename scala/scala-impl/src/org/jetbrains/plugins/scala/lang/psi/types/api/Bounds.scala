package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.{CallContext, ScType}

/**
  * @author adkozlov
  */
trait Bounds {
  def glb(first: ScType, second: ScType, checkWeak: Boolean = false)(implicit ctx: CallContext): ScType

  def glb(types: IterableOnce[ScType], checkWeak: Boolean)(implicit ctx: CallContext): ScType =
    types.iterator.reduce(glb(_, _, checkWeak))

  def lub(first: ScType, second: ScType, checkWeak: Boolean = true)(implicit ctx: CallContext): ScType

  def lub(types: IterableOnce[ScType], checkWeak: Boolean)(implicit ctx: CallContext): ScType =
    types.iterator.reduce(lub(_, _, checkWeak))
}