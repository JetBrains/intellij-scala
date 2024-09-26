package org.jetbrains.plugins.scala.editor.smartEnter.fixers

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.editor.{Document, Editor}
import org.jetbrains.plugins.scala.editor.smartEnter.ScalaSmartEnterProcessor
import org.jetbrains.plugins.scala.extensions.{IteratorExt, ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScEnumerator, ScFor}
import org.jetbrains.plugins.scala.util.IndentUtil

// TODO(SCL-23041): indentation-based syntax support
final class ScalaForStatementFixer extends ScalaForStatementFixerBase {
  override protected def doApply(forStatement: ScFor)(implicit editor: Editor, document: Document,
                                                      processor: ScalaSmartEnterProcessor): OperationPerformed = {
    val leftBracket = forStatement.getLeftBracket.orNull
    val rightBracket = forStatement.getRightBracket.orNull

    forStatement.enumerators match {
      case None if leftBracket == null && rightBracket == null && forStatement.body.isEmpty =>
        val forStartOffset = forStatement.startOffset
        val stopOffset = document.getLineEndOffset(document.getLineNumber(forStartOffset))
        document.replaceString(forStartOffset, stopOffset, "for () {}")
        editor.getCaretModel.moveToOffset(forStartOffset)
        WithReformat(5) // put caret inside parens
      case None if leftBracket != null && rightBracket == null =>
        val offset = forStatement.endOffset
        document.insertString(offset, s"${matchingBracketText(leftBracket)}")
        createEmptyBodyIfNeeded(forStatement)
        WithReformat(0)
      case None if leftBracket != null && rightBracket != null =>
        moveToStart(editor, rightBracket)
        createEmptyBodyIfNeeded(forStatement)
        WithReformat(0)
      case Some(cond) if leftBracket != null && rightBracket == null =>
        document.insertString(cond.endOffset, matchingBracketText(leftBracket))
        createEmptyBodyIfNeeded(forStatement)
        WithReformat(0)
      case Some(cond) if leftBracket != null && rightBracket != null =>
        val currentOffset = editor.getCaretModel.getOffset
        if (hasRelevantMissingRightBraceErrorAfter(forStatement, rightBracket)) {
          // parsed right brace belongs to one of the parents, and this for is actually without a brace
          document.insertString(cond.endOffset, matchingBracketText(leftBracket))
          if (cond.startsFromNewLine()) {
            placeAfterCurrentEnumerator(forStatement, newLines = 2) // additional newline to move new '}' down
          }
          createEmptyBodyIfNeeded(forStatement)
          WithReformat(0)
        } else if (leftBracket.textMatches("{") && cond.getTextRange.containsOffset(currentOffset) && cond.startsFromNewLine()) {
          placeAfterCurrentEnumerator(forStatement)
          createEmptyBodyIfNeeded(forStatement)
          WithReformat(0)
        } else if (forStatement.body.exists(_.is[ScBlockExpr])) {
          placeInWholeBodyBlock(forStatement, editor)
        } else NoOperation
      case _ => NoOperation
    }
  }

  private def createEmptyBodyIfNeeded(forStatement: ScFor)
                                     (implicit editor: Editor, document: Document, processor: ScalaSmartEnterProcessor): Unit = {
    processor.commit(editor)
    if (forStatement.body.isEmpty) {
      forStatement.getRightBracket.foreach { rightBracket =>
        document.insertString(rightBracket.endOffset, " {}")
      }
    }
  }

  private def placeAfterCurrentEnumerator(forStatement: ScFor, newLines: Int = 1)
                                         (implicit editor: Editor, document: Document, processor: ScalaSmartEnterProcessor): Unit =
    forStatement.enumerators.foreach { enumerators =>
      val caretModel = editor.getCaretModel
      val currentOffset = caretModel.getOffset
      val anchor = enumerators.children
        .filterByType[ScEnumerator]
        .find(_.getTextRange.containsOffset(currentOffset))
        .getOrElse(enumerators)
      document.insertString(anchor.endOffset, "\n" * newLines)
      processor.commit(editor)
      forStatement.getRightBracket.foreach { rightBracket =>
        processor.reformatWithoutAdjustment(rightBracket)
        val tabSize = CodeStyle.getSettings(anchor.getProject)
          .getTabSize(anchor.getLanguage.getAssociatedFileType)
        val indent = IndentUtil.calcIndent(anchor, tabSize)
        if (indent > 0) {
          document.insertString(anchor.endOffset + 1, " " * indent)
        }
        caretModel.moveToOffset(anchor.endOffset + 1 + indent)
      }
    }
}
