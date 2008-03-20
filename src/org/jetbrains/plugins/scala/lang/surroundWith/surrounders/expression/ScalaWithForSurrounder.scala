package org.jetbrains.plugins.scala.lang.surroundWith.surrounders;

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import lang.psi.api.expr._


/*
 * Surrounds expression with for: for { <Cursor> } yield Expression
 */

class ScalaWithForSurrounder extends ScalaExpressionSurrounder {

  override def getExpressionTemplateAsString (expr : ASTNode ) =
    if (!isNeedBraces(expr)) "for (val a <- as) yield " + expr.getText
    else "(" + "for (val a <- as) yield " + expr.getText + ")" 

  override def getTemplateDescription = "for / yield"

  override def getSurroundSelectionRange (withForNode : ASTNode ) : TextRange = {
    def isForStmt = (e : PsiElement) => e.isInstanceOf[ScForStmt]

    val forStmt = if (isNeedBraces(withForNode)) withForNode.getPsi.asInstanceOf[ScalaPsiElementImpl].childSatisfyPredicateForPsiElement(isForStmt).asInstanceOf[ScForStmt]
                else withForNode.getPsi.asInstanceOf[ScForStmt]

    val enums = forStmt.asInstanceOf[ScForStmt].enumerators.getNode

    val offset = enums.getTextRange.getStartOffset
    forStmt.getNode.removeChild(enums)

    new TextRange(offset, offset);
  }
}




