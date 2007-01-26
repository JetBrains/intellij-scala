package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

class ScalaWithTrySurrounder extends ScalaBlockSurrounder {

  override def getExpressionTemplateAsString (expr : ASTNode) = {
    val expAsString = "try {" + expr.getText + "}"
    if (!isNeedBraces(expr)) expAsString
    else "(" + expAsString + ")"
  }

  override def getTemplateDescription = "try"

  override def getSurroundSelectionRange (withTryNode : ASTNode ) : TextRange = {
    def isTryStmt = (e : PsiElement) => e.isInstanceOf[ScTryStmtImpl]

    val tryStmt = if (isNeedBraces(withTryNode)) withTryNode.getPsi.asInstanceOf[ScalaPsiElementImpl].
                      childSatisfyPredicateForPsiElement(isTryStmt).asInstanceOf[ScTryStmtImpl]
                    else withTryNode.getPsi.asInstanceOf[ScTryStmtImpl]

    val offset = tryStmt.getTextRange.getEndOffset
    new TextRange(offset, offset)
  }
}