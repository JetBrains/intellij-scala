package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScUnderscoreSection extends ScExpression {

  //todo implement me!
  def bindingExpr: Option[ScExpression] = None

}