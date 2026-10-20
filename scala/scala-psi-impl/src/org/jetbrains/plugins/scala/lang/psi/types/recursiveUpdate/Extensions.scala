package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.api.Variance
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScType}

class Extensions(val tp: ScType) extends AnyVal {

  def recursiveVarianceUpdate(variance: Variance = Variance.Covariant)(update: Update): ScType =
    SubtypeUpdaterVariance.recursiveUpdate(tp, variance, update)

  //allows most control on what should be done when encountering a type
  def recursiveUpdate(update: SimpleUpdate): ScType =
    SubtypeUpdaterNoVariance.recursiveUpdate(tp, Variance.Covariant, update)

  //updates all matching subtypes recursively
  def updateRecursively(pf: PartialFunction[ScType, ScType]): ScType =
    SubtypeUpdaterNoVariance.recursiveUpdate(tp, Variance.Covariant, SimpleUpdate(pf))

  def updateLeaves(pf: PartialFunction[LeafType, ScType]): ScType =
    SubtypeUpdaterNoVariance.recursiveUpdate(tp, Variance.Covariant, LeafSubstitution(pf))

  //invokes a function with a side-effect recursively, doesn't create any new types
  def visitRecursively(fun: ScType => Unit): ScType =
    SubtypeTraverser.recursiveUpdate(tp, Variance.Covariant, foreachSubtypeUpdate(fun))

  def subtypeExists(predicate: ScType => Boolean): Boolean = {
    var found = false
    SubtypeTraverser.recursiveUpdate(tp, Variance.Covariant, foreachSubtypeUpdate2(
      t =>
        if (predicate(t)) {
          found = true
          Stop
        } else {
          ProcessSubtypes
        }
    ))
    found
  }

  private def foreachSubtypeUpdate(fun: ScType => Unit): SimpleUpdate =
    scType => {
      fun(scType)
      ProcessSubtypes
    }

  private def foreachSubtypeUpdate2(fun: ScType => AfterUpdate): SimpleUpdate =
    scType => fun(scType)
}
