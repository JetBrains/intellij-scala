package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScFinallyBlockBase extends ScalaPsiElementBase { this: ScFinallyBlock =>
  def expression: Option[ScExpression] = findChild[ScExpression]
}

abstract class ScFinallyBlockCompanion {
  def unapply(block: ScFinallyBlock): Option[ScExpression] = block.expression
}