package org.jetbrains.plugins.scala.lang.psi.api.expr

/**
 * @author ilyas
 */

trait ScBlock extends ScExpression {

  def exprs : Seq[ScExpression]

}
