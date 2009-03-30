package org.jetbrains.plugins.scala.lang.refactoring.util


import psi.types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.03.2009
 */

object ScTypeUtil {
  //for java
  def presentableText(typez: ScType) = ScType.presentableText(typez)
}