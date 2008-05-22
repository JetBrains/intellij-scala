package org.jetbrains.plugins.scala.lang.psi.api.expr

/**
 * @authoe ilyas
 */

trait ScBlock extends ScExpression {

  def exprs : Seq[ScExpression]

}
