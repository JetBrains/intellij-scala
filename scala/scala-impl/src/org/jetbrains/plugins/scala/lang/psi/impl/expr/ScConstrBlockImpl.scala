package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScConstrBlock

final class ScConstrBlockImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScConstrBlock {

  override def toString: String = "ConstructorBlock"

  override def isEnclosedByBraces: Boolean =
    this.firstChild.forall(_.elementType == ScalaTokenTypes.tLBRACE)
}