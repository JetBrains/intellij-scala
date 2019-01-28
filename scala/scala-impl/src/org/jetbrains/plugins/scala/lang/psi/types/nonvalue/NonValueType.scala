package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
  * This is internal type, no expression can have such type.
  */
trait NonValueType extends ScType {
  def isValue = false
}
