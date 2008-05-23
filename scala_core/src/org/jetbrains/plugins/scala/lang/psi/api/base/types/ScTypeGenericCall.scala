package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScParametrizedTypeElement extends ScTypeElement {

  def getTypeArgs: ScTypeArgs

  def getSimpleTypeElement: ScSimpleTypeElement

}