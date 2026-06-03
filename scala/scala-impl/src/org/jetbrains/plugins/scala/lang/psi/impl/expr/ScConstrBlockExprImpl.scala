package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlockExpr

final class ScConstrBlockExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrBlockExpr {

  override def toString: String = "ConstructorBlock" // TODO: rename to ConstructorBlockExpression

  override def getColon: Option[PsiElement] = None
}
