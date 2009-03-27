package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/** 
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

trait ScArgumentExprList extends ScArguments {
  def exprs: Seq[ScExpression] = findChildrenByClass(classOf[ScExpression]).toSeq
}