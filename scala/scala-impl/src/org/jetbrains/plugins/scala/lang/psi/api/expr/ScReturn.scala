package org.jetbrains.plugins.scala
package lang
package psi
package api
package expr

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

/**
  * @author Alexander Podkhalyuzin
  *         Date: 06.03.2008
  */
trait ScReturn extends ScExpression {
  def expr: Option[ScExpression] = findChild(classOf[ScExpression])

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