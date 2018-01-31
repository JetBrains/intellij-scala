package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith, Stop}

/**
  * Nikolay.Tropin
  * 11-Aug-17
  */
trait Extensions {
  implicit class ScTypeExt(val tp: ScType) {
    def recursiveUpdateImpl(update: Update, visited: Set[ScType] = Set.empty, isLazySubtype: Boolean = false): ScType = {
      if (!update.isDefined(tp) || visited(tp)) tp
      else update(tp) match {
        case ReplaceWith(res) => res
        case Stop => tp
        case ProcessSubtypes =>
          val newVisited = if (isLazySubtype) visited + tp else visited
          tp.updateSubtypes(update, newVisited)
      }
    }

    //allows most control on what should be done when encountering a type
    def recursiveUpdate(fun: ScType => AfterUpdate): ScType =
      recursiveUpdateImpl(Update(fun))

    //updates all matching subtypes recursively
    def updateRecursively(pf: PartialFunction[ScType, ScType]): ScType =
      recursiveUpdateImpl(Update.RecursivePartial(pf))

    //invokes a function with a side-effect recursively, doesn't create any new types
    def visitRecursively(fun: ScType => Unit): ScType =
      recursiveUpdateImpl(Update.VisitRecursively(fun))

    def subtypeExists(predicate: ScType => Boolean): Boolean = {
      var found = false
      recursiveUpdateImpl(Update.VisitRecursively {
        case t if predicate(t) || found =>
          found = true
          Stop
        case _ =>
          ProcessSubtypes
      })
      found
    }

  }
}
