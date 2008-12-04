package org.jetbrains.plugins.scala.lang.psi.api.expr

import base.types.ScTypeArgs
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/** 
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScGenericCall extends ScExpression {

  def referencedExpr = findChildByClass(classOf[ScExpression])

  def typeArgs = findChildByClass(classOf[ScTypeArgs])

}