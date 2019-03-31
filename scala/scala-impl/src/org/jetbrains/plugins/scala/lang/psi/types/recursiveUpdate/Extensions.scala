package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.api.Variance
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScType}

/**
  * Nikolay.Tropin
  * 11-Aug-17
  */
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
    visitRecursively {
      case t if predicate(t) || found =>
        found = true
        Stop
      case _ =>
        ProcessSubtypes
    }
    found
  }


  private def foreachSubtypeUpdate(fun: ScType => Unit): SimpleUpdate = scType => {
    fun(scType)
    ProcessSubtypes
  }
}
