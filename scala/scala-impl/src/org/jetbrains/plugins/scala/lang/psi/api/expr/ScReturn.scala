package org.jetbrains.plugins.scala.lang.psi.api.expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

trait ScReturn extends ScExpression {
  def expr: Option[ScExpression] = findChild[ScExpression]

  def keyword: PsiElement

  def method: Option[ScFunctionDefinition] =
    this.parentOfType(classOf[ScFunctionDefinition])

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitReturn(this)
  }
}

object ScReturn {
  def unapply(expr: ScReturn): Option[ScExpression] = expr.expr

  object of {
    def unapply(expr: ScReturn): Option[ScFunctionDefinition] = expr.method
  }
}