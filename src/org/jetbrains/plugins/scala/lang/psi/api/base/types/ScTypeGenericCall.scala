package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParameterizedTypeElement extends ScTypeElement {

  def typeArgList: ScTypeArgs

  def typeElement: ScTypeElement
}