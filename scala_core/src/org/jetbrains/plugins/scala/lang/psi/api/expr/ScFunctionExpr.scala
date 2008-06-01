package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

/**
* @author Alexander Podkhalyuzin, ilyas
* Date: 06.03.2008
*/

trait ScFunctionExpr extends ScExpression {

  def parameters: Seq[ScParameter]

  def params: ScParameters

  def result: Option[ScExpression]

}