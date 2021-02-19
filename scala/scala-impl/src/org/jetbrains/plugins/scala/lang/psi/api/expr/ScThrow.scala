package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import org.jetbrains.plugins.scala.lang.psi.api._


/**
* @author Alexander Podkhalyuzin
*/
trait ScThrowBase extends ScExpressionBase { this: ScThrow =>
  def expression: Option[ScExpression] = findChild[ScExpression]

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitThrow(this)
  }
}