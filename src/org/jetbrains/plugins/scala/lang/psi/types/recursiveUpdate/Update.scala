package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.ProcessSubtypes

import scala.language.implicitConversions

/**
  * Nikolay.Tropin
  * 10-Aug-17
  */
trait Update {
  def apply(scType: ScType): AfterUpdate

  def isDefined(scType: ScType): Boolean = true
}

object Update {
  def apply(fun: ScType => AfterUpdate) = Simple(fun)

  case class VisitRecursively(fun: ScType => Unit) extends Update {
    override def apply(scType: ScType): AfterUpdate = {
      fun(scType)
      ProcessSubtypes
    }
  }

  case class Simple(fun: ScType => AfterUpdate) extends Update {
    override def apply(scType: ScType): AfterUpdate = fun(scType)
  }
}

sealed trait AfterUpdate

object AfterUpdate {
  case class ReplaceWith(scType: ScType) extends AfterUpdate

  case object ProcessSubtypes extends AfterUpdate

  case object Stop extends AfterUpdate
}