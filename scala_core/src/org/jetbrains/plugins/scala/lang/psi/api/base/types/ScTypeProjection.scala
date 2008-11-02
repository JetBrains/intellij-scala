package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 13.03.2008
*/

trait ScTypeProjection extends ScTypeElement with ScReferenceElement {
  def typeElement = findChildByClass(classOf[ScTypeElement])
}