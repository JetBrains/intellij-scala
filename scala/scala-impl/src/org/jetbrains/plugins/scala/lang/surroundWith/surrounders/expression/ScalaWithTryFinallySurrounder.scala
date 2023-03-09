package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._

class ScalaWithTryFinallySurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "try {\n" + super.getTemplateAsString(elements) + "\n} finally a"

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "try / finally"

  override def getSurroundSelectionRange(editor: Editor, withTryCatchNode: ASTNode): TextRange = {
    val tryCatchStmt = unwrapParenthesis(withTryCatchNode) match {
      case Some(stmt: ScTry) => stmt
      case _ => return withTryCatchNode.getTextRange
    }

    val caseClause = tryCatchStmt.getNode.getLastChildNode.getLastChildNode.getPsi

    val offset = caseClause.getTextRange.getStartOffset
    tryCatchStmt.getNode.removeChild(caseClause.getNode)

    new TextRange(offset, offset)
  }
}
