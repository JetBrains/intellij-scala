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
 * Surrounds block with try - catch: try { Block } catch { <Cursor> } 
 */

class ScalaWithTryCatchSurrounder extends ScalaWithTrySurrounder {
  override def getExpressionTemplateAsString (expr : ASTNode) = {
    val expAsString = "try { \n " + expr.getText + "\n" + "} catch { \n " + "case a=>b" +  "\n }"
    if (!isNeedBraces(expr)) expAsString
    else "(" + expAsString + ")"
  }

  override def getTemplateDescription = "try / catch"

  override def getSurroundSelectionRange (withTryCatchNode : ASTNode) : TextRange = {
    def isTryCatchStmt = (e : PsiElement) => e.isInstanceOf[ScTryStmt]

    val tryCatchStmt = if (isNeedBraces(withTryCatchNode)) withTryCatchNode.getPsi.asInstanceOf[ScalaPsiElementImpl].
                      childSatisfyPredicateForPsiElement(isTryCatchStmt).asInstanceOf[ScTryStmt]
                    else withTryCatchNode.getPsi.asInstanceOf[ScTryStmt]

    val catchBlockPsiElement = tryCatchStmt.catchBlock
    val caseClause = catchBlockPsiElement.caseClauses.elements.next

    val offset = caseClause.getTextRange.getStartOffset
    tryCatchStmt.getNode.removeChild(caseClause.getNode)

    new TextRange(offset, offset)
  }
}