package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author Alexander Podkhalyuzin
 * Date: 06.03.2008
 */

class ScTypedExpressionImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTypedExpression {

  protected override def innerType: TypeResult = {
    typeElement match {
      case Some(te) => te.`type`()
      case None if !expr.isInstanceOf[ScUnderscoreSection] => expr.`type`()
      case _ => Failure("Typed statement is not complete for underscore section")
    }
  }

  override def toString: String = "TypedStatement"
}