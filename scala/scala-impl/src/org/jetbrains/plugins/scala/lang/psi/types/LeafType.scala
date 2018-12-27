package org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types.api.Variance
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor


/** Common trait of all ScTypes with trivial [[ScType.updateSubtypes()]] method */
trait LeafType {
  this: ScType =>

  override final def updateSubtypes(substitutor: ScSubstitutor, variance: Variance)
                                   (implicit visited: Set[ScType]): ScType = this
}
