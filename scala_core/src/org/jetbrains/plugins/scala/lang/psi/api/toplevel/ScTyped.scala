package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.types.ScType
trait ScTyped {
  def calcType() : Option[ScType]
}