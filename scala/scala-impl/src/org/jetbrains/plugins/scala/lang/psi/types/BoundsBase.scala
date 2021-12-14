package org.jetbrains.plugins.scala.lang.psi.types

trait BoundsBase {
  def glb(first: ScType, second: ScType, checkWeak: Boolean = false): ScType

  def lub(first: ScType, second: ScType, checkWeak: Boolean = true): ScType

  protected def lubInner(
    l:                    ScType,
    r:                    ScType,
    checkWeak:            Boolean,
    stopAddingUpperBound: Boolean
  ): ScType

  protected def lubInner(
    t1:        ScType,
    t2:        ScType,
    depth:     Int,
    checkWeak: Boolean
  )(implicit
    stopAddingUpperBound: Boolean
  ): ScType
}
