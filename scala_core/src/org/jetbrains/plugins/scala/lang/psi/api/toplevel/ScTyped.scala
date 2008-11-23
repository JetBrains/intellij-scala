package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScTyped extends ScNamedElement {
  def calcType() : ScType

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

}