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

    def recursiveUpdate(fun: ScType => AfterUpdate): ScType =
      recursiveUpdateImpl(Update(fun))

    def visitRecursively(fun: ScType => Unit): ScType =
      recursiveUpdateImpl(Update.VisitRecursively(fun))
  }
}
