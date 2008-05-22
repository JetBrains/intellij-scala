package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

trait ScCodeBlock extends ScalaPsiElement {

  def exprs : Seq[ScExpression]
  
}