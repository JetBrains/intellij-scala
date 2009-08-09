package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel

import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScTyped extends ScNamedElement {
  def calcType() : ScType

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  /**
   * @return false for variable elements
   */
  def isStable = true

}