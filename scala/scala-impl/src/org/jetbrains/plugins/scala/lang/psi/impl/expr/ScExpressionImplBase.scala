package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

abstract class ScExpressionImplBase(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExpression {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitExpression(this)
  }
}
