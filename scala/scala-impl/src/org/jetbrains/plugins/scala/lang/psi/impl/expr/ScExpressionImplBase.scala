package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

abstract class ScExpressionImplBase(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExpression {

  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case scalaVisitor: ScalaElementVisitor => accept(scalaVisitor)
      case _ => super.accept(visitor)
    }
  }

  override def accept(visitor: ScalaElementVisitor): Unit = {
    visitor.visitExpression(this)
  }
}
