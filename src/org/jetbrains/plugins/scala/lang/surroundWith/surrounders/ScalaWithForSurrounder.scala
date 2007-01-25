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

class ScalaWithForSurrounder extends ScalaExpressionSurrounder {
  override def isApplicable(expr : ScExprImpl) : Boolean = {
    expr.isInstanceOf[ScExprImpl]
  }

  override def getExpressionTemplateAsString (expr : ASTNode ) =
    if (!isNeedBraces(expr)) "for (val a <- as) yield " + expr.getText
    else "(" + "for (val a <- as) yield " + expr.getText + ")" 

  override def getTemplateDescription = "for / yield"

  override def getSurroundSelectionRange (withForNode : ASTNode ) : TextRange = {
    def isForStmt = (e : PsiElement) => e.isInstanceOf[ScForStmtImpl]

    val forStmt = if (isNeedBraces(withForNode)) withForNode.getPsi.asInstanceOf[ScalaPsiElementImpl].childSatisfyPredicateForPsiElement(isForStmt).asInstanceOf[ScForStmtImpl]
                else withForNode.getPsi.asInstanceOf[ScForStmtImpl]

    val enums = forStmt.asInstanceOf[ScForStmtImpl].enumerators.getNode

    val offset = enums.getTextRange.getStartOffset
    forStmt.getNode.removeChild(enums)

    new TextRange(offset, offset);
  }
}




