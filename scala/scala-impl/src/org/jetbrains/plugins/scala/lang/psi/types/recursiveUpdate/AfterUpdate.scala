package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.ScType

sealed trait AfterUpdate

object AfterUpdate {
  case class ReplaceWith(scType: ScType) extends AfterUpdate

  case object ProcessSubtypes extends AfterUpdate

  case object Stop extends AfterUpdate
}