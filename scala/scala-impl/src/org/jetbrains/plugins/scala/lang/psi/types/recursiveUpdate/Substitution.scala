package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private trait Substitution {
  val update: PartialFunction[ScType, ScType]

  def toString: String
}
