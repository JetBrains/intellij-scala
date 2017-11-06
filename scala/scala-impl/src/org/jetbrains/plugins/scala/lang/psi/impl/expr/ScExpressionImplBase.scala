package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

class ScExpressionImplBase(node: ASTNode) extends ScalaPsiElementImpl(node) with ScExpression {

  override final def accept(visitor: PsiElementVisitor): Unit = {
    super.accept(visitor)
  }
}
