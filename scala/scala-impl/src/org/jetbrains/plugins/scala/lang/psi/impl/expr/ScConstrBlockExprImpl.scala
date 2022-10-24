package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlockExpr

final class ScConstrBlockExprImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrBlockExpr {

  override def toString: String = "ConstructorBlock" // TODO: rename to ConstructorBlockExpression

  override def isEnclosedByBraces: Boolean =
    this.firstChild.exists(_.elementType == ScalaTokenTypes.tLBRACE)
}