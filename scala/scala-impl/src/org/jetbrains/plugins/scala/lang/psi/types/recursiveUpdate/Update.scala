package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Variance
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate._

trait Update extends ((ScType, Variance) => AfterUpdate)

object Update {
  import AfterUpdate._

  def apply(pf: PartialFunction[(ScType, Variance), ScType]): Update = (v1: ScType, v2: Variance) => {
    if (pf.isDefinedAt(v1, v2)) ReplaceWith(pf(v1, v2))
    else ProcessSubtypes
  }
}

trait SimpleUpdate extends (ScType => AfterUpdate) with Update {
  def apply(v1: ScType, v2: Variance): AfterUpdate = apply(v1)
}

object SimpleUpdate {
  def apply(pf: PartialFunction[ScType, ScType]): SimpleUpdate = (tp: ScType) => {
    if (pf.isDefinedAt(tp)) ReplaceWith(pf(tp))
    else ProcessSubtypes
  }
}