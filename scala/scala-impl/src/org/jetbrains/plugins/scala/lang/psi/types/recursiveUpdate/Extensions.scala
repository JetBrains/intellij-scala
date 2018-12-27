package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.api.{Covariant, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}
import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScType}

/**
  * Nikolay.Tropin
  * 11-Aug-17
  */
class Extensions(val tp: ScType) extends AnyVal {

  //This method allows application of different `Update` functions in a single pass (see ScSubstitutor).
  //WARNING: If several updates are used, they should be applicable only for leaf types, e.g. which return themselves
  //from `updateSubtypes` method
  final def recursiveUpdateImpl(substitutor: ScSubstitutor,
                                variance: Variance = Covariant,
                                isLazySubtype: Boolean = false)
                               (implicit visited: Set[ScType] = Set.empty): ScType = {
    val updates = substitutor.substitutions
    val index = substitutor.fromIndex

    if (index >= updates.length || visited(tp)) tp
    else {
      val currentUpdate = updates(index)

      currentUpdate(tp, variance) match {
        case ReplaceWith(res) =>
          res.recursiveUpdateImpl(substitutor.next, variance, isLazySubtype)(Set.empty)
        case Stop => tp
        case ProcessSubtypes =>
          val newVisited = if (isLazySubtype) visited + tp else visited

          if (substitutor.hasNonLeafSubstitutions) {
            tp.updateSubtypes(ScSubstitutor(currentUpdate), variance)(newVisited)
              .recursiveUpdateImpl(substitutor.next, variance)(Set.empty)
          }
          else {
            tp.updateSubtypes(substitutor, variance)(newVisited)
          }
      }
    }
  }

  def recursiveVarianceUpdate(variance: Variance = Variance.Covariant)(update: Update): ScType = update(tp, variance) match {
    case ReplaceWith(res) => res
    case Stop => tp
    case ProcessSubtypes => tp.updateSubtypes(ScSubstitutor(update), variance)(Set.empty)
  }

  //allows most control on what should be done when encountering a type
  def recursiveUpdate(update: SimpleUpdate): ScType = recursiveVarianceUpdate(Variance.Covariant)(update)

  //updates all matching subtypes recursively
  def updateRecursively(pf: PartialFunction[ScType, ScType]): ScType =
    recursiveUpdate(SimpleUpdate(pf))

  def updateLeaves(pf: PartialFunction[LeafType, ScType]): ScType =
    recursiveUpdate(LeafSubstitution(pf))

  //invokes a function with a side-effect recursively, doesn't create any new types
  def visitRecursively(fun: ScType => Unit): ScType =
    recursiveUpdate(foreachSubtypeUpdate(fun))

  def subtypeExists(predicate: ScType => Boolean): Boolean = {
    var found = false
    recursiveUpdate(foreachSubtypeUpdate {
      case t if predicate(t) || found =>
        found = true
        Stop
      case _ =>
        ProcessSubtypes
    })
    found
  }


  private def foreachSubtypeUpdate(fun: ScType => Unit): SimpleUpdate = scType => {
    fun(scType)
    ProcessSubtypes
  }
}
