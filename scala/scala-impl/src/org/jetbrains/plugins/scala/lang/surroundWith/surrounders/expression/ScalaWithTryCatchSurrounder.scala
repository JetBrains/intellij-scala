package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._

object ScalaWithTryCatchSurrounder extends ScalaExpressionSurrounder {
  override def getTemplateAsString(elements: Array[PsiElement]): String = {
    val arrow = if (elements.length == 0) "=>" else ScalaPsiUtil.functionArrow(elements(0).getProject)
    "try {\n" + super.getTemplateAsString(elements) + s"\n} catch {\n case _ $arrow \n}"
  }

  //noinspection ScalaExtractStringToBundle
  override def getTemplateDescription = "try / catch"

  override def getSurroundSelectionRange(editor: Editor, withTryCatchNode: ASTNode): TextRange = {
    val tryCatchStmt = unwrapParenthesis(withTryCatchNode) match {
      case Some(stmt: ScTry) => stmt
      case _ => return withTryCatchNode.getTextRange
    }

    val catchBlockPsiElement: ScCatchBlock = tryCatchStmt.catchBlock.get
    val caseClause =
      catchBlockPsiElement.expression.get.asInstanceOf[ScBlockExpr].caseClauses.get.caseClauses.head.pattern.get

    val offset = caseClause.getTextRange.getStartOffset
    tryCatchStmt.getNode.removeChild(caseClause.getNode)

    new TextRange(offset, offset)
  }
}
