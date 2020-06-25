package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * Nikolay.Tropin
  * 24-Apr-17
  */
trait ValueType extends ScType {
  override def isValue = true

  override def inferValueType: ValueType = this
}
