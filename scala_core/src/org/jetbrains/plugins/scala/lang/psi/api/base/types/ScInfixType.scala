package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScInfixTypeElement extends ScTypeElement {
  def lOp : ScTypeElement = findChildByClass(classOf[ScTypeElement])
  def rOp : Option[ScTypeElement] 
  def ref : ScStableCodeReferenceElement = findChildByClass(classOf[ScStableCodeReferenceElement])
}