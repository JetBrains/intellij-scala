package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

/**
 * @author: Dmitry Krasilschikov
 */

import com.intellij.psi.PsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange

import org.jetbrains.plugins.scala.lang.psi.impl.expr._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import lang.psi.api.expr._

/*
 * Surrounds expression with if: if { <Cursor> } Expression
 */

class ScalaWithIfSurrounder extends ScalaExpressionSurrounder {

  override def getExpressionTemplateAsString (expr : ASTNode) =
    if (!isNeedBraces(expr)) "if (a) " + expr.getText
    else "(" + "if (a) " + expr.getText + ")"

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
     return "if (a) {\n" + super.getTemplateAsString(elements) + "\n}"
  }

  override def getTemplateDescription = "if"

  override def getSurroundSelectionRange(nodeWithIfNode : ASTNode ) : TextRange = {
    def isIfStmt = (e : PsiElement) => e.isInstanceOf[ScIfStmtImpl]

    val stmt = nodeWithIfNode.getPsi.asInstanceOf[ScIfStmtImpl]

    val conditionNode : ASTNode = stmt.condition match {
        case Some(c) => c.getNode
    }
    val offset = conditionNode.getStartOffset();
    stmt.getNode.removeChild(conditionNode)

    return new TextRange(offset, offset);
  }
}




