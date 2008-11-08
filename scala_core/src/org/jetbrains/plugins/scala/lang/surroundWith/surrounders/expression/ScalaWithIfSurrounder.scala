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

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
     return "if (a) {\n" + super.getTemplateAsString(elements) + "\n}"
  }

  override def getTemplateDescription = "if"

  override def getSurroundSelectionRange(nodeWithIfNode : ASTNode ) : TextRange = {
    val element: PsiElement = nodeWithIfNode.getPsi match {
      case x: ScParenthesisedExpr => x.expr match {
        case Some(y) => y
        case _ => return x.getTextRange
      }
      case x => x
    }

    val stmt = element.asInstanceOf[ScIfStmtImpl]

    val conditionNode : ASTNode = (stmt.condition: @unchecked) match {
        case Some(c) => c.getNode
    }
    val offset = conditionNode.getStartOffset();
    stmt.getNode.removeChild(conditionNode)

    return new TextRange(offset, offset);
  }
}




