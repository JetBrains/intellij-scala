package org.jetbrains.plugins.scala.lang.psi.api.expr

import base.types.ScTypeElement
import psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScTypedStmt extends ScExpression {
  def expr = findChildByClass(classOf[ScExpression])
  def typeElement = findChild(classOf[ScTypeElement])
}