package org.jetbrains.plugins.scala.lang.psi.api.base.patterns

import psi.ScalaPsiElement
import expr.ScExpression

/** 
* @author Alexander Podkhalyuzin
* Date: 28.02.2008
*/

trait ScCaseClause extends ScalaPsiElement {
  def pattern = findChild(classOf[ScPattern])
  def expr = findChild(classOf[ScExpression])
}