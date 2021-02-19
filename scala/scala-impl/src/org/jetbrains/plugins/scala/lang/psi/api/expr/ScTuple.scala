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
trait ScTupleBase extends ScInfixArgumentExpressionBase { this: ScTuple =>
  def exprs: Seq[ScExpression] = findChildren[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitTuple(this)
  }

}

abstract class ScTupleCompanion {
  def unapply(e: ScTuple): Some[Seq[ScExpression]] = Some(e.exprs)
}