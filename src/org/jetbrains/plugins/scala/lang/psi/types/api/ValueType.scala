package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Nikolay.Tropin
  * 24-Apr-17
  */
trait ValueType extends ScType {
  def isValue = true

  def inferValueType: ValueType = this
}
