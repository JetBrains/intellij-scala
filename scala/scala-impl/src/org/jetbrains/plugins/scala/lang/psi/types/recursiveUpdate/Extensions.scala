package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Covariant, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}

import scala.annotation.tailrec

/**
  * Nikolay.Tropin
  * 11-Aug-17
  */
class Extensions(val tp: ScType) extends AnyVal {

  //This method allows application of different `Update` functions in a single pass (see ScSubstitutor).
  //WARNING: If several updates are used, they should be applicable only for leaf types, e.g. which return themselves
  //from `updateSubtypes` method
  @tailrec
  final def recursiveUpdateImpl(substitutor: ScSubstitutor,
                                visited: Set[ScType] = Set.empty,
                                isLazySubtype: Boolean = false): ScType = {
    val updates = substitutor.substitutions
    val index = substitutor.fromIndex

    if (index >= updates.length || visited(tp)) tp
    else {
      val currentUpdate = updates(index)

      currentUpdate(tp) match {
        case ReplaceWith(res) =>
          res.recursiveUpdateImpl(substitutor.next, Set.empty, isLazySubtype)
        case Stop => tp
        case ProcessSubtypes =>
          val newVisited = if (isLazySubtype) visited + tp else visited

          if (substitutor.hasNonLeafSubstitutions) {
            tp.updateSubtypes(ScSubstitutor(currentUpdate), newVisited)
              .recursiveUpdateImpl(substitutor.next, Set.empty)
          }
          else {
            tp.updateSubtypes(substitutor, newVisited)
          }
      }
    }
  }

  //todo: should we unify recursiveUpdateImpl and recursiveVarianceUpdate together?
  final def recursiveVarianceUpdate(update: (ScType, Variance) => AfterUpdate,
                                    variance: Variance = Covariant,
                                    isLazySubtype: Boolean = false)
                                   (implicit visited: Set[ScType] = Set.empty): ScType = {
    if (visited(tp)) tp
    else update(tp, variance) match {
      case ReplaceWith(res) => res
      case Stop => tp
      case ProcessSubtypes =>
        val newVisited = if (isLazySubtype) visited + tp else visited
        tp.updateSubtypesVariance(update, variance)(newVisited)
    }
  }

  //allows most control on what should be done when encountering a type
  def recursiveUpdate(update: Update): ScType = update(tp) match {
    case ReplaceWith(res) => res
    case Stop => tp
    case ProcessSubtypes => tp.updateSubtypes(ScSubstitutor(update), Set.empty)
  }

  //updates all matching subtypes recursively
  def updateRecursively(pf: PartialFunction[ScType, ScType]): ScType =
    recursiveUpdate(Update(pf))

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


  private def foreachSubtypeUpdate(fun: ScType => Unit): Update = scType => {
    fun(scType)
    ProcessSubtypes
  }
}
