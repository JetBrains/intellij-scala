package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl

abstract class ScExpressionImplBase(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExpression {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitExpression(this)
  }
}
