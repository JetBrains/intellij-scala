package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTypedExpressionImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTypedExpression {

  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    typeElement match {
      case Some(te) => te.`type`()
      case None if !expr.isInstanceOf[ScUnderscoreSection] =>
        // `xs: _*` is typed against the expected type of the sequence argument
        expr.`type`(if (isSequenceArg) expectedType else None)
      case _ => Failure(ScalaBundle.message("typed.statement.is.not.complete.for.underscore.section"))
    }
  }

  override def toString: String = "TypedExpression"
}