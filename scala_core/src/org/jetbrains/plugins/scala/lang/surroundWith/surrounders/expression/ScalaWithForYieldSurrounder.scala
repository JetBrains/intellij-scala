package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

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

class ScalaWithForYieldSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    return "for (a <- as) yield {" + super.getTemplateAsString(elements) + "}"
  }

  override def getTemplateDescription = "for / yield"

  override def getSurroundSelectionRange(withForNode: ASTNode): TextRange = {
    val forStmt = withForNode.getPsi.asInstanceOf[ScForStatement]

    val enums = (forStmt.asInstanceOf[ScForStatement].enumerators: @unchecked) match {
      case Some(x) => x.getNode
    }

    val offset = enums.getTextRange.getStartOffset
    forStmt.getNode.removeChild(enums)

    new TextRange(offset, offset);
  }
}




