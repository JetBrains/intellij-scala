package org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types.api.Variance
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor


/** Common trait of all ScTypes with trivial [[ScType.updateSubtypes]] method */
trait LeafType {
  this: ScType =>

  override final def updateSubtypes(substitutor: ScSubstitutor, visited: Set[ScType]): ScType = this

  override final def updateSubtypesVariance(update: (ScType, Variance) => recursiveUpdate.AfterUpdate,
                                            variance: Variance,
                                            revertVariances: Boolean)
                                           (implicit visited: Set[ScType]): ScType = this
}
