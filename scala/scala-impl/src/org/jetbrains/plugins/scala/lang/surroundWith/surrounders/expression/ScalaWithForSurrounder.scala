package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaWithForSurrounder extends ScalaExpressionSurrounder {
  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "for"

  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    "for (a <- as) {" + super.getTemplateAsString(elements) + "}"
  }

  override def getSurroundSelectionRange(editor: Editor, withForNode: ASTNode): TextRange = {
    val forStmt = unwrapParenthesis(withForNode) match {
      case Some(stmt: ScFor) => stmt
      case _ => return withForNode.getTextRange
    }

    val enums = (forStmt.enumerators: @unchecked) match {
      case Some(x) => x.getNode
    }

    val offset = enums.getTextRange.getStartOffset
    forStmt.getNode.removeChild(enums)

    new TextRange(offset, offset)
  }
}
