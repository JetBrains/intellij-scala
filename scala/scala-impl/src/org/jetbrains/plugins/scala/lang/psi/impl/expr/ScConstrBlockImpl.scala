package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScConstrBlock, ScExpression}

/**
  * @author Alexander.Podkhalyuzin
  */
class ScConstrBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrBlock {

  override protected def createMirror: (String, PsiElement, PsiElement) => Option[ScExpression] =
    ScalaPsiElementFactory.createConstructorBodyWithContextFromText

  override def toString: String = "ConstructorBlock"
}