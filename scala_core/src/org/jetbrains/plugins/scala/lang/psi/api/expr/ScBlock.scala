package org.jetbrains.plugins.scala.lang.psi.api.expr

import org.jetbrains.plugins.scala.lang.psi.api.expr.util.ScBlocker

/**
 * @author ilyas
 */

trait ScBlock extends ScExpression with ScBlocker {

  def exprs : Seq[ScExpression]

}
