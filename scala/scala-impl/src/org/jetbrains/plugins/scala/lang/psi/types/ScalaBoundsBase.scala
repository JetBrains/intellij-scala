package org.jetbrains.plugins.scala.lang.psi.types

trait ScalaBoundsBase extends api.Bounds {
  self: BoundsUtil =>

  override def lub(
    t1:        ScType,
    t2:        ScType,
    checkWeak: Boolean
  )(implicit context: Context): ScType =
    lubInner(
      t1,
      t2,
      checkWeak,
      stopAddingUpperBound = false
    )

  protected def lubInner(
    l:                    ScType,
    r:                    ScType,
    checkWeak:            Boolean,
    stopAddingUpperBound: Boolean
  )(implicit
    context: Context
  ): ScType =
    lubInner(
      l,
      r,
      lubDepth(l, r),
      checkWeak
    )(stopAddingUpperBound = stopAddingUpperBound, context = context)

  protected def lubInner(
    t1:        ScType,
    t2:        ScType,
    depth:     Int,
    checkWeak: Boolean
  )(implicit
    stopAddingUpperBound: Boolean,
    context:              Context
  ): ScType
}
