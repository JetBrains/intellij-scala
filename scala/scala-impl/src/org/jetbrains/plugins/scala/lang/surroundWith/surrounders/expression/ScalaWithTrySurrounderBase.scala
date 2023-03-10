package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.lang.ASTNode
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScTry}

abstract class ScalaWithTrySurrounderBase extends ScalaExpressionSurrounder {
  override def getSurroundSelectionRange(editor: Editor, tryNode: ASTNode): TextRange = {
    val stmt = unwrapParenthesis(tryNode) match {
      case Some(stmt: ScTry) =>
        val converted = ScalaPsiUtil.convertTryToBracelessIfNeeded(stmt)(stmt.projectContext, stmt)
        if (stmt eq converted) stmt
        else stmt.replace(converted).asInstanceOf[ScTry]
      case _ => return null
    }

    unblockDocument(editor)
    getRangeToDelete(editor, stmt).pipeIf(_ != null) { rangeToDelete =>
      deleteText(editor, rangeToDelete)
      TextRange.from(rangeToDelete.getStartOffset, 0)
    }
  }

  protected def getRangeToDelete(editor: Editor, tryStmt: ScTry): TextRange

  private def unblockDocument(editor: Editor): Unit =
    PsiDocumentManager.getInstance(editor.getProject)
      .doPostponedOperationsAndUnblockDocument(editor.getDocument)

  private def deleteText(editor: Editor, range: TextRange): Unit = {
    val document = editor.getDocument
    document.deleteString(range.getStartOffset, range.getEndOffset)
    document.commit(editor.getProject)
  }

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
