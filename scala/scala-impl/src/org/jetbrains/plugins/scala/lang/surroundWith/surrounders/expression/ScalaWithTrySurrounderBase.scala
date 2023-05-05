package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScTry}

abstract class ScalaWithTrySurrounderBase extends ScalaExpressionSurrounder {
  override def getSurroundSelectionRange(editor: Editor, tryNode: ASTNode): TextRange = {
    val stmt = unwrapParenthesis(tryNode) match {
      case Some(stmt: ScTry) =>
        stmt.toIndentationBasedSyntax
      case _ => return null
    }

    unblockDocument(editor)
    getRangeToDelete(editor, stmt).pipeIf(_ != null) { rangeToDelete =>
      deleteText(editor, rangeToDelete)
      TextRange.from(rangeToDelete.getStartOffset, 0)
    }
  }

  protected def getRangeToDelete(editor: Editor, tryStmt: ScTry): TextRange

  protected def arrow(elements: Array[PsiElement]): String =
    if (elements.isEmpty) "=>"
    else ScalaPsiUtil.functionArrow(elements.head.getProject)
}

abstract class ScalaWithTryCatchSurrounderBase extends ScalaWithTrySurrounderBase {
  override protected def getRangeToDelete(editor: Editor, tryStmt: ScTry): TextRange =
    tryStmt.catchBlock match {
      case Some(ScCatchBlock(clauses)) =>
        clauses.caseClause.pattern match {
          case Some(pattern) => pattern.getTextRange
          case _ => null
        }
      case _ => null
    }
}
