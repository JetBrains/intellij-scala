package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScPlaceholderExpr extends ScExpression {
  def placeholdedExpr: Option[ScExpression] = findChild(classOf[ScExpression])
}