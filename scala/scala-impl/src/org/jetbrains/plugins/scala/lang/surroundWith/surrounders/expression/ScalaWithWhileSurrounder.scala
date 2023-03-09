package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScWhile

/*
 * Surrounds expression with while: while { <Cursor> } { Expression }
 */
class ScalaWithWhileSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "while (true) {" + super.getTemplateAsString(elements) + "}"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "while"

  override def getSurroundSelectionRange(editor: Editor, withWhileNode: ASTNode): TextRange = {
    val whileStmt = unwrapParenthesis(withWhileNode) match {
      case Some(stmt: ScWhile) => stmt
      case _ => return withWhileNode.getTextRange
    }

    val conditionNode: ASTNode = (whileStmt.condition: @unchecked) match {
      case Some(c) => c.getNode
    }

    val startOffset = conditionNode.getTextRange.getStartOffset
    val endOffset = conditionNode.getTextRange.getEndOffset

    new TextRange(startOffset, endOffset)
  }
}
