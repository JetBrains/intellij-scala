package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Covariant, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}

import scala.annotation.tailrec

/**
  * Nikolay.Tropin
  * 11-Aug-17
  */
trait Extensions {
  implicit class ScTypeExt(val tp: ScType) {

    //This method allows application of different `Update` functions in a single pass (see ScSubstitutor).
    //WARNING: If several updates are used, they should be applicable only for leaf types, e.g. which return themselves
    //from `updateSubtypes` method
    @tailrec
    final def recursiveUpdateImpl(updates: Seq[Update], visited: Set[ScType] = Set.empty, isLazySubtype: Boolean = false): ScType = {
      if (updates.isEmpty || visited(tp)) tp
      else updates.head(tp) match {
        case ReplaceWith(res) => res.recursiveUpdateImpl(updates.tail, visited, isLazySubtype)
        case Stop => tp
        case ProcessSubtypes =>
          val newVisited = if (isLazySubtype) visited + tp else visited
          tp.updateSubtypes(updates, newVisited)
      }
    }

    //todo: revertVariances parameter looks really strange and used only in one place, can we get rid of it?
    //todo: should we unify recursiveUpdateImpl and recursiveVarianceUpdate together?
    final def recursiveVarianceUpdate(update: (ScType, Variance) => AfterUpdate,
                                      variance: Variance = Covariant,
                                      isLazySubtype: Boolean = false,
                                      revertVariances: Boolean = false)
                                     (implicit visited: Set[ScType] = Set.empty): ScType = {
      if (visited(tp)) tp
      else update(tp, variance) match {
        case ReplaceWith(res) => res
        case Stop => tp
        case ProcessSubtypes =>
          val newVisited = if (isLazySubtype) visited + tp else visited
          tp.updateSubtypesVariance(update, variance, revertVariances)(newVisited)
      }
    }

    //allows most control on what should be done when encountering a type
    def recursiveUpdate(update: Update): ScType =
      recursiveUpdateImpl(update :: Nil)

    //updates all matching subtypes recursively
    def updateRecursively(pf: PartialFunction[ScType, ScType]): ScType =
      recursiveUpdate(Update(pf))

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
}
