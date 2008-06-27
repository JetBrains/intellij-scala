package org.jetbrains.plugins.scala.lang.psi.api.expr

import toplevel.ScCodeBlock
/**
 * @author ilyas
 */

trait ScBlock extends ScExpression with ScCodeBlock {

  def exprs : Seq[ScExpression]
}
