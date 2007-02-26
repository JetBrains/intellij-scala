package org.jetbrains.plugins.scala.lang.surroundWith.surrounders

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.expressions._

/*
 * Surrounds expression with while: while { <Cursor> } { Expression }
 */

class ScalaWithWhileSurrounder extends ScalaExpressionSurrounder {
  override def getExpressionTemplateAsString (expr : ASTNode) = {
    val exprAsString = "while (true) { \n " + expr.getText + "\n" + "}"

    if (!isNeedBraces(expr)) exprAsString
    else "(" + exprAsString + ")"
  }

  override def getTemplateDescription = "while"

  override def getSurroundSelectionRange (withWhileNode : ASTNode ) : TextRange = {
    def isWhileStmt = (e : PsiElement) => e.isInstanceOf[ScWhileStmtImpl]

    val whileStmt = if (isNeedBraces(withWhileNode)) withWhileNode.getPsi.asInstanceOf[ScalaPsiElementImpl].
                      childSatisfyPredicateForPsiElement(isWhileStmt).asInstanceOf[ScWhileStmtImpl]
                    else withWhileNode.getPsi.asInstanceOf[ScWhileStmtImpl]

    val conditionNode : ASTNode = whileStmt.condition.getNode

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

    return new TextRange(startOffset, endOffset);
  }
}