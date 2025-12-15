package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTypedExpressionImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTypedExpression {

  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    typeElement match {
      case Some(te) => te.`type`(None)
      case None if !expr.isInstanceOf[ScUnderscoreSection] => expr.`type`(expectedType)
      case _ => Failure(ScalaBundle.message("typed.statement.is.not.complete.for.underscore.section"))
    }
  }

  override def toString: String = "TypedExpression"
}