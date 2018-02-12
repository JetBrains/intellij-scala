package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScConstrExpr, ScExpression}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */
class ScConstrExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrExpr {

  override protected def createMirror: (String, PsiElement, PsiElement) => Option[ScExpression] =
    ScalaPsiElementFactory.createConstructorBodyWithContextFromText

  override def toString: String = "ConstructorExpression"
}